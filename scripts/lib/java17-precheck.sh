#!/usr/bin/env bash
# Require JDK 17+ for this repository (Spring Boot 3.5 / --release 17).
# Usage: source this file, then call require_java_17

require_java_17() {
  local java_cmd="java"
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    java_cmd="${JAVA_HOME}/bin/java"
  elif ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java not found. Install JDK 17+ and set JAVA_HOME (e.g. /opt/homebrew/opt/openjdk@17)." >&2
    return 1
  fi

  local major
  major="$("$java_cmd" -XshowSettings:properties -version 2>&1 \
    | awk -F'= ' '/java\.specification\.version/ {gsub(/[[:space:]]/,"",$2); print $2; exit}')"
  if [ -z "$major" ]; then
    echo "ERROR: unable to detect java.specification.version from: $java_cmd" >&2
    return 1
  fi
  case "$major" in
    1.*) major="${major#1.}" ;;
  esac
  if ! [[ "$major" =~ ^[0-9]+$ ]] || [ "$major" -lt 17 ]; then
    echo "ERROR: JDK 17+ required (found java.specification.version=${major})." >&2
    echo "  export JAVA_HOME=/opt/homebrew/opt/openjdk@17" >&2
    echo "  export PATH=\"\$JAVA_HOME/bin:\$PATH\"" >&2
    return 1
  fi
  echo "  Java precheck OK: $($java_cmd -version 2>&1 | head -n1)"
  return 0
}
