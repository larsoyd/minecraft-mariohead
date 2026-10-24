#!/bin/sh
# builds Mario Head and installs it with Fabric into the launcher folder, or runs a dev client
# sh play.sh help lists the commands
set -eu

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
	(cd "$root" && timeout --foreground 1200 ./gradlew --console=plain jar </dev/null) || die "build failed"
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
	api="$mc/mods/fabric-api-$FABRIC_API_VERSION.jar"
	fetch "$MAVEN/fabric-api/fabric-api/$FABRIC_API_VERSION/fabric-api-$FABRIC_API_VERSION.jar" "$api" "$FABRIC_API_SHA1"
	for other in "$mc"/mods/fabric-api-*.jar; do
		[ "$other" = "$api" ] || say "warning: another Fabric API is in the mods folder: $other"
	done
	rm -f "$mc"/mods/mariohead-*.jar
	cp "$jar" "$mc/mods/"
	say "installed $(basename "$jar") and $(basename "$api") into $mc/mods"
	say "in the launcher pick the profile fabric-loader-$MC_VERSION and press Play"
}

case ${1:-play} in
	play)
		install
		need minecraft-launcher
		say "opening the Minecraft Launcher"
		nohup minecraft-launcher >/dev/null 2>&1 &
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
sh play.sh          build, install with Fabric, then open the Minecraft Launcher
sh play.sh install  build and install only
sh play.sh dev      run a throwaway dev client with the mod, nothing installed
MINECRAFT_DIR=...   use another launcher folder than ~/.minecraft
USAGE
		;;
	*)
		die "unknown command $1, use play, install, dev or help"
		;;
esac
