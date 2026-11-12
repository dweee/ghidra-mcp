# Retained Ghidra Java gates

`nix build .#checks.x86_64-linux.retained-java` runs the complete maintained Java
suite, including offline, real ProgramDB/decompiler and live HTTP tests. It
compiles an innocuous ELF with a `main` function, imports it into a disposable
project, starts its own loopback-only headless server and tears that process
down. It refuses an existing port 8089 listener. No user projects, server
credentials or installed GUI profiles are used. Zero skipped Java tests are
required. The fixture provides `main` for the retained HTTP decompilation tests.

The tooling uses nixpkgs `40aa5a0e9ff16a78d4b5d5c39e72f0382cb44061`.
Its Ghidra is 12.1.4, so the Ghidra 12.2 SDK is separately pinned through public
nixpkgs `1d060086d60cb76a40cf37109f979d88a5bd6396`. This is the same SDK used
for the extension compatibility validation. Java 25 is needed for this SDK;
the upstream default Java 21 remains available for older Ghidra installations.
All Maven test jars are exact public artifacts with SHA-256 hashes in
`java-test-dependencies.nix`. The init script uses those jars offline while
preserving the maintained Gradle test task and its complete source selection.

This check does not replace the Python unit/offline floor or the live Python
read-only suite required by `CLAUDE.md`. The HTTP replay plugin proves only the
recorded bridge contract; it does not prove current Ghidra behavior. GitHub
Actions enablement and release/publishing workflows are independent of this
Nix check.
