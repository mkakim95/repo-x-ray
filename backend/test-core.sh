#!/bin/sh
# Works even when java is installed without a javac launcher, if jdk.compiler exists.
set -eu
cd "$(dirname "$0")"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
java -m jdk.compiler/com.sun.tools.javac.Main -d "$OUT" src/main/java/dev/repoxray/Model.java src/main/java/dev/repoxray/RepoInput.java src/main/java/dev/repoxray/StaticAnalyzer.java src/main/java/dev/repoxray/GradleSettings.java src/test/java/dev/repoxray/CoreChecks.java
java -cp "$OUT" dev.repoxray.CoreChecks
