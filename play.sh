#!/usr/bin/env bash
[ -n "${BASH_VERSION:-}" ] || exec bash "$0" "$@"
set -euo pipefail

MC_VERSION=26.3
LOADER_VERSION=0.20.1
INSTALLER_VERSION=1.1.2
INSTALLER_SHA1=74685b5a0178c4dabd4be741a276d35c59b5ec96
FABRIC_API_VERSION=0.165.0+26.3
FABRIC_API_SHA1=8f8a23d6c9fbf7b075b31b4fb0a1007b0488d1d2
MAVEN=https://maven.fabricmc.net/net/fabricmc

root=$(cd "$(dirname "$0")" && pwd)
mc=${MINECRAFT_DIR:-$HOME/.minecraft}
cache=${XDG_CACHE_HOME:-$HOME/.cache}/mariohead

say() {
	printf '[play] %s\n' "$*"
}

die() {
	printf '[play] refused: %s\n' "$*" >&2
	exit 1
}

need() {
	command -v "$1" >/dev/null 2>&1 || die "$1 is not installed"
}

# downloads url to file unless a copy with the right sha1 is already there
fetch() {
	url=$1
	file=$2
	sha=$3
	if [ -f "$file" ] && [ "$(sha1sum "$file" | cut -d' ' -f1)" = "$sha" ]; then
		return 0
	fi
	say "downloading $(basename "$file")"
	timeout --foreground 300 curl -fsSL --retry 2 -o "$file.part" "$url" || die "download failed: $url"
	got=$(sha1sum "$file.part" | cut -d' ' -f1)
	if [ "$got" != "$sha" ]; then
		rm -f "$file.part"
		die "$(basename "$file") sha1 $got, expected $sha"
	fi
	mv "$file.part" "$file"
}

build() {
	say "building the mod"
	(cd "$root" && timeout --foreground 1200 ./gradlew --console=plain --max-workers=4 jar </dev/null) || die "build failed"
	jar="$root/build/libs/mariohead-$(sed -n 's/^version=//p' "$root/gradle.properties").jar"
	[ -f "$jar" ] || die "no jar at $jar"
}

install() {
	need java
	need curl
	need sha1sum
	[ -f "$mc/launcher_profiles.json" ] || die "no launcher profiles in $mc, start the Minecraft Launcher once first"
	build
	mkdir -p "$cache" "$mc/mods"
	installer="$cache/fabric-installer-$INSTALLER_VERSION.jar"
	fetch "$MAVEN/fabric-installer/$INSTALLER_VERSION/fabric-installer-$INSTALLER_VERSION.jar" "$installer" "$INSTALLER_SHA1"
	say "installing Fabric Loader $LOADER_VERSION for Minecraft $MC_VERSION"
	timeout --foreground 600 java -jar "$installer" client -dir "$mc" -mcversion "$MC_VERSION" -loader "$LOADER_VERSION" \
		|| die "the Fabric installer failed"
	install_mods
	say "in the launcher pick the profile fabric-loader-$MC_VERSION and press Play"
}

install_mods() {
	mkdir -p "$mc/mods"
	api="$mc/mods/fabric-api-$FABRIC_API_VERSION.jar"
	fetch "$MAVEN/fabric-api/fabric-api/$FABRIC_API_VERSION/fabric-api-$FABRIC_API_VERSION.jar" "$api" "$FABRIC_API_SHA1"
	for other in "$mc"/mods/fabric-api-*.jar; do
		[ "$other" = "$api" ] || say "warning: another Fabric API is in the mods folder: $other"
	done
	rm -f "$mc"/mods/mariohead-*.jar
	cp "$jar" "$mc/mods/"
	say "installed $(basename "$jar") and $(basename "$api") into $mc/mods"
}

setup_prism() {
	need prismlauncher
	need python3
	prism=${PRISM_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/PrismLauncher}
	java25=${MARIOHEAD_JAVA:-}
	if [ -z "$java25" ]; then
		for candidate in "$HOME"/.gradle/jdks/*25*/bin/java /usr/lib/jvm/java-25-openjdk/bin/java; do
			if [ -x "$candidate" ]; then java25=$candidate; break; fi
		done
	fi
	[ -x "$java25" ] || die "Java 25 not found, set MARIOHEAD_JAVA to its java executable"
	python3 "$root/tools/setup_prism.py" "$prism" "$mc" "$MC_VERSION" "$LOADER_VERSION" "$java25"
}

case ${1:-play} in
	play)
		setup_prism
		need curl
		need sha1sum
		build
		install_mods
		say "launching Mario Head through Prism"
		exec prismlauncher --dir "$prism" --launch mariohead
		;;
	setup)
		setup_prism
		;;
	official)
		install
		launcher=minecraft-launcher
		if command -v minecraft-launcher.sh >/dev/null 2>&1; then launcher=minecraft-launcher.sh; fi
		need "$launcher"
		say "opening the Minecraft Launcher"
		exec "$launcher"
		;;
	install)
		install
		;;
	dev)
		say "starting a dev client, it uses an offline player and its own folder under build"
		cd "$root"
		exec ./gradlew --console=plain runClient
		;;
	help)
		cat <<'USAGE'
sh play.sh          build and launch Fabric directly through Prism
sh play.sh setup    prepare the Prism instance without launching
sh play.sh official build and open the official Minecraft Launcher
sh play.sh install  build and install for the official launcher only
sh play.sh dev      run a throwaway dev client with the mod, nothing installed
MINECRAFT_DIR=...   use another launcher folder than ~/.minecraft
PRISM_DIR=...       override the Prism data folder
MARIOHEAD_JAVA=...  Java 25 executable
USAGE
		;;
	*)
		die "unknown command $1, use play, setup, official, install, dev or help"
		;;
esac
