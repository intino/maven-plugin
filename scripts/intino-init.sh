#!/usr/bin/env bash
# Creates initial Maven POMs only. Existing project files are preserved.
# Download: https://raw.githubusercontent.com/intino/maven-plugin/master/scripts/intino-init.sh
set -euo pipefail

if [[ "${1:-}" == "--help" || $# -gt 1 ]]; then
  echo "Uso: $0 [directorio-del-proyecto]"
  echo "Crea los POM iniciales; no importa, compila ni publica."
  echo "INTINO_PLUGIN_VERSION permite elegir la versión del plugin (por defecto: 1.0.0)."
  exit 0
fi
command -v mvn >/dev/null || { echo "Se necesita Maven (mvn) en PATH." >&2; exit 1; }
project_directory=$(cd "${1:-.}" && pwd)
plugin_version=${INTINO_PLUGIN_VERSION:-1.0.0}
if [[ ! "$plugin_version" =~ ^[a-zA-Z0-9._-]+$ ]]; then
  echo "Versión del plugin no válida: $plugin_version" >&2
  exit 1
fi

# An independent POM lets Maven load the plugin even when the project has no POM,
# or its declared modules do not have their POMs yet.
bootstrap_directory=$(mktemp -d "${TMPDIR:-/tmp}/intino-init.XXXXXX")
trap 'rm -f "$bootstrap_directory/pom.xml"; rmdir "$bootstrap_directory"' EXIT
cat > "$bootstrap_directory/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.intino.bootstrap</groupId>
  <artifactId>init</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>
  <pluginRepositories>
    <pluginRepository>
      <id>intino-maven</id>
      <url>https://artifactory.intino.io/artifactory/release-libraries</url>
    </pluginRepository>
  </pluginRepositories>
</project>
EOF
mvn -N -f "$bootstrap_directory/pom.xml" \
  "io.intino:intino-maven-plugin:$plugin_version:init" \
  "-Dintino.directory=$project_directory"
echo "POM iniciales preparados en $project_directory. No se ha ejecutado import ni compilación."
