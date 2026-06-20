#!/bin/sh
# Maven Wrapper script for Volcano Arts Center Platform
# This script downloads and runs Maven without requiring a system Maven install.

set -e

# Determine the project base directory
if [ -z "$MAVEN_PROJECTBASEDIR" ]; then
  MAVEN_PROJECTBASEDIR="$(cd "$(dirname "$0")" && pwd)"
  export MAVEN_PROJECTBASEDIR
fi

MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

# Read distribution URL
if [ -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
  MAVEN_DIST_URL=$(grep 'distributionUrl' "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
fi

if [ -z "$MAVEN_DIST_URL" ]; then
  MAVEN_DIST_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"
fi

# Maven installation directory
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-3.9.9"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  echo "Downloading Maven..."
  mkdir -p "$MAVEN_HOME"
  TMPFILE=$(mktemp /tmp/maven-XXXXXX.zip)
  curl -sL "$MAVEN_DIST_URL" -o "$TMPFILE"
  unzip -qo "$TMPFILE" -d "$HOME/.m2/wrapper/dists/"
  rm -f "$TMPFILE"
  chmod +x "$MAVEN_HOME/bin/mvn"
fi

# Find Java
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="$(which java)"
fi

if [ ! -x "$JAVACMD" ]; then
  echo "Error: JAVA_HOME is not set and 'java' is not in PATH." >&2
  exit 1
fi

export JAVA_HOME="${JAVA_HOME:-$(dirname $(dirname $(readlink -f "$JAVACMD" 2>/dev/null || echo "$JAVACMD")))}"

exec "$MAVEN_HOME/bin/mvn" "$@"
