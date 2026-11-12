{
  description = "Retained Java gates against Ghidra 12.2";
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/40aa5a0e9ff16a78d4b5d5c39e72f0382cb44061";
  # Reuse the established public Ghidra 12.2 SDK; the tooling pin above ships 12.1.4.
  inputs.ghidra-sdk.url = "github:NixOS/nixpkgs/1d060086d60cb76a40cf37109f979d88a5bd6396";
  outputs = { self, nixpkgs, ghidra-sdk }: let
    system = "x86_64-linux";
    pkgs = nixpkgs.legacyPackages.${system};
    testJars = pkgs.callPackage ./ci/java-test-dependencies.nix {};
  in {
    checks.${system}.retained-java = pkgs.runCommand "ghidra-mcp-retained-java" {
      nativeBuildInputs = [ pkgs.jdk25 pkgs.gradle_9 pkgs.python3 pkgs.stdenv.cc ];
      inherit testJars;
      GHIDRA_INSTALL_DIR = "${ghidra-sdk.legacyPackages.${system}.ghidra}/lib/ghidra";
      JAVA_HOME = pkgs.jdk25.home;
    } ''
      export HOME="$TMPDIR/home" GRADLE_USER_HOME="$TMPDIR/gradle"
      export JAVA_TOOL_OPTIONS="-Duser.home=$HOME -Djava.io.tmpdir=$TMPDIR"
      mkdir -p "$HOME"
      cp -r ${self} source
      chmod -R u+w source
      cd source
      python3 ci/retained-java.py
      mkdir -p "$out"
      cp -r build/test-results "$out/"
    '';
  };
}
