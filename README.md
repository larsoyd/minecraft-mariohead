# Mario Head

A Fabric mod for Minecraft 26.3 that brings in the Floating Mario Head from Mario Teaches Typing.

He swells into view a few blocks in front of you with a puff of smoke, glances about, says
"Hello, I'm going to fly for you", and then comes at you. Each "Mmmmm" snaps him forward and he sags
between them, a little faster every time, until he outruns a sprint and starts matching your pace.
He does not give up. If he touches you, you die.

---

### Requirements

- Minecraft Java Edition 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API 0.165.0+26.3 or newer
- Java 25

### Instructions

- To use `/mariohead summon` in singleplayer, create the world with Allow Commands on, or open it to LAN with cheats allowed.
- `/mariohead summon` puts him in view of you, 7 to 12 blocks away, somewhere open with a clear
  line of sight. `/mariohead summon <players>` does it for others. He refuses when there is no room
  in view, for example when you stare at the floor or a wall, and for players in creative or
  spectator.
- The Floating Mario Head Spawn Egg is in the Spawn Eggs tab of the creative inventory. A head
  from an egg chases the nearest player in survival or adventure within 32 blocks.
- **NB:** The head uses TNT for some dashing which is destructive. If you wish to turn that off, disable the `tntexplodes` rule.

The command needs operator rights (permission level 2).

---

### Install

Download the newest `mariohead-x.x.x.jar` from the [latest release](https://github.com/larsoyd/minecraft-mariohead/releases/latest).
Install Fabric Loader for Minecraft 26.3, then put the mod jar and
[Fabric API](https://modrinth.com/mod/fabric-api) for Minecraft 26.3 in your instance's `mods` folder.
Use Java 25 in your Minecraft launcher.

### Updating

If you need to update to a new version, download the latest version and remove the one you already have. This mod does not support two versions of the same mod in the mods directory!

---

### Building from source (i.e Manual Install)

These are the instructions to manually build the mod yourself from the source. Unless you know you want to do this, it is not recommended. The supported paths to do so is Ubuntu on WSL (Windows) or Ubuntu LTS if you are on Linux. **Remove any other version of the mod from your mods directory before you do this!**

Please note that the developer version might have bugs and it is recommended that you use the release *.jar instead (See previous "Install" category if you missed it)

#### What the build does:

Java 21 starts the bundled Gradle wrapper. The project's Gradle configuration downloads Java 25
for the build automatically. The first build needs internet access to download Gradle, Java and
Minecraft/Fabric dependencies. You do not need to install Gradle separately.
See [Ubuntu's Java setup guide](https://ubuntu.com/developers/docs/howto/java-setup/) and
[Gradle's daemon JVM documentation](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:daemon_jvm_criteria).

`build.sh` runs the build, unit tests and server game tests with at most four Gradle workers and a 20-minute timeout.
It prints the output jar path when successful. It does not install the mod or open a game client.
The model and runtime sounds are already included in the source tree.

To run unit tests separately, use `./gradlew test`. Server game tests use `./gradlew runGameTest`.

### Build Instructions

#### Windows (WSL with Ubuntu)

Install Ubuntu through [Windows Subsystem for Linux](https://learn.microsoft.com/en-us/windows/wsl/install).
In PowerShell as administrator, run:

```powershell
wsl --install -d Ubuntu
```

Restart if prompted, open Ubuntu from the Start menu, and create your Linux username and password.
Run the following commands in the Ubuntu terminal, keeping the project in your Linux home folder:

```bash
sudo apt update
sudo apt install -y git openjdk-21-jdk-headless ca-certificates
mkdir -p ~/git
cd ~/git
git clone https://github.com/larsoyd/minecraft-mariohead.git
cd minecraft-mariohead
./build.sh
```

The jar is `build/libs/mariohead-x.x.x-dev.jar`. Run `cd build/libs && explorer.exe .` from Ubuntu to open the output
folder in Windows, then copy the jar to your Windows Minecraft instance's `mods` folder.
For the standard launcher, that is `%APPDATA%\.minecraft\mods`.
Install Fabric Loader and Fabric API on Windows as described above, then launch Minecraft on Windows.


#### Ubuntu

On Ubuntu 24.04 LTS or newer, open a terminal and run:

```bash
sudo apt update
sudo apt install -y git openjdk-21-jdk-headless ca-certificates
mkdir -p ~/git
cd ~/git
git clone https://github.com/larsoyd/minecraft-mariohead.git
cd minecraft-mariohead
./build.sh
```


Copy `build/libs/mariohead-x.x.x-dev.jar` into your Minecraft instance's `mods` folder
(`~/.minecraft/mods` for the standard launcher), alongside Fabric API for Minecraft 26.3.

---

### Credits & Notes

- Head model: the high quality Mario head Blender model (`mario_head.blend`) with its cap, iris
  and swatch textures is based on the Floating Mario Head from Mario Teaches Typing. The original creator is unknown since I cannot find the site where I originally found it. If it's you, please come forward.
- Voice: clips of the Floating Mario Head from Mario Teaches Typing.
- His arrival sound is Minecraft's own illusioner mirror move sound, lowered in pitch.

#### LLM/AI Policy & Disclosure

- LLMs were used exclusively with a human in the loop in an assistive capacity during development & for the writing of documentation.  
- No LLMs was and ever will be used for the generation of art assets like textures, models and/or voice clips.
