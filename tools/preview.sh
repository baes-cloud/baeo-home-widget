#!/usr/bin/env bash
# Renders the widget on this machine (no phone needed) into tools/out/.
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jdk-21.0.12.1+1}"
rm -rf tools/classes && mkdir -p tools/classes tools/out
"$JAVA_HOME/bin/javac" -nowarn -d tools/classes $(find tools/shim -name '*.java') \
  src/com/baeo/widgets/HomeState.java src/com/baeo/widgets/BaeoHomeRender.java tools/Preview.java
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp tools/classes Preview assets/fonts tools/out
