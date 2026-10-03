#!/usr/bin/env bash
# One-time: downloads every build dependency into .bootstrap/ so the build can also run offline
# in the development sandbox. Safe to re-run. Does not change your default Java or global settings.
set -eo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOOT_DIR="$PROJECT_DIR/.bootstrap"
cd "$PROJECT_DIR"
mkdir -p "$BOOT_DIR"

echo "==> 1/6 Selecting Java 21 for this shell only"
# shellcheck disable=SC1091
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.4-tem >/dev/null
java -version 2>&1 | head -1

echo "==> 2/6 Checking Maven"
if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven not found; installing with Homebrew"
  brew install maven
fi
mvn -v | head -1

echo "==> 3/6 Creating the Maven wrapper (mvnw)"
mvn -B -q -N wrapper:wrapper -Dtype=only-script -Dmaven=3.9.9

echo "==> 4/6 Building the skeleton (downloads all Java deps, Node $(
  grep -m1 '<node.version>' pom.xml | sed 's/.*>\(.*\)<.*/\1/'
) and npm packages)"
./mvnw -B -Dmaven.repo.local="$BOOT_DIR/m2repo" spotless:apply
./mvnw -B -Dmaven.repo.local="$BOOT_DIR/m2repo" verify

echo "==> 5/6 Fetching Linux builds of native frontend tools (for the sandbox)"
cd frontend
export PATH="$PWD/node:$PATH"
NPM="node $PWD/node/node_modules/npm/bin/npm-cli.js"
ESBUILD_VER="$(node -p "require('esbuild/package.json').version")"
ROLLUP_VER="$(node -p "require('rollup/package.json').version")"
mkdir -p "$BOOT_DIR/linux-bin"
$NPM pack "@esbuild/linux-x64@$ESBUILD_VER" "@rollup/rollup-linux-x64-gnu@$ROLLUP_VER" \
  --pack-destination "$BOOT_DIR/linux-bin" >/dev/null
cd "$PROJECT_DIR"

echo "==> 6/6 Packing caches into .bootstrap/ (40 MB parts)"
rm -f "$BOOT_DIR"/*.part-*
tar czf - -C "$BOOT_DIR" m2repo linux-bin | split -b 40m - "$BOOT_DIR/deps.tgz.part-"
tar czf - frontend/node_modules frontend/package-lock.json .mvn mvnw \
  | split -b 40m - "$BOOT_DIR/frontend.tgz.part-"

echo
echo "Done. Parts created:"
ls -lh "$BOOT_DIR"/*.part-*
echo
echo "Tell Claude: bootstrap done."
