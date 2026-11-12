"""Run the maintained Java suite on a disposable, real Ghidra server."""
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

root = Path.cwd()
work = Path(os.environ["TMPDIR"]) / "retained-fixture"
work.mkdir()
ghidra = Path(os.environ["GHIDRA_INSTALL_DIR"])
env = dict(os.environ, XDG_RUNTIME_DIR=str(work))
base = ["gradle", "--offline", "--no-daemon", "--max-workers=2", "--init-script",
        str(root / "ci/retained-java.init.gradle"), "-PGHIDRA_INSTALL_DIR=" + str(ghidra)]
subprocess.run(base + ["testClasses"], env=env, check=True)
(work / "fixture.c").write_text("int add(int a,int b){return a+b;} int main(void){return add(2,3);}")
subprocess.run(["cc", "-g", "-O0", str(work / "fixture.c"), "-o", str(work / "fixture")], check=True)
(work / "project").mkdir()
subprocess.run([str(ghidra / "support/analyzeHeadless"), str(work / "project"), "retained",
                "-import", str(work / "fixture"), "-max-cpu", "2", "-analysisTimeoutPerFile", "60"], env=env, check=True)
# These retained Java tests use 8089. Refuse an existing listener before starting ours.
with socket.socket() as probe:
    probe.bind(("127.0.0.1", 8089))
classpath = os.pathsep.join(map(str, [root / "build/classes/java/main", root / "build/resources/main",
                                    *ghidra.joinpath("Ghidra").rglob("*.jar")]))
with (root / "build/retained-server.log").open("w") as log:
    server = subprocess.Popen(["java", "-Dghidra.home=" + str(ghidra), "-Djava.awt.headless=true", "-cp", classpath,
        "com.xebyte.headless.GhidraMCPHeadlessServer", "--port", "8089", "--bind", "127.0.0.1",
        "--project", str(work / "project/retained.gpr"), "--program", "fixture"], env=dict(env, GHIDRA_MCP_FILE_ROOT=str(work)), stdout=log, stderr=subprocess.STDOUT)
    try:
        for _ in range(120):
            if server.poll() is not None:
                raise RuntimeError("Disposable Ghidra server exited")
            try:
                with urllib.request.urlopen("http://127.0.0.1:8089/mcp/health", timeout=1) as response:
                    if response.status == 200:
                        break
            except OSError:
                time.sleep(.5)
        else:
            raise RuntimeError("Disposable Ghidra server did not become ready")
        subprocess.run(base + ["test"], env=dict(env, GHIDRA_MCP_LIVE_TESTS="1"), check=True)
    finally:
        server.terminate()
        try:
            server.wait(timeout=15)
        except subprocess.TimeoutExpired:
            server.kill()
            server.wait()
results = [ET.parse(p).getroot() for p in root.glob("build/test-results/test/TEST-*.xml")]
assert sum(int(r.attrib["tests"]) for r in results) >= 888, "Java suite was not fully selected"
assert all(int(r.attrib["failures"]) == 0 and int(r.attrib["errors"]) == 0 for r in results)

assert all(int(r.attrib["skipped"]) == 0 for r in results), "A retained Java gate was skipped"
