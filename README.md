# Mario Head

A Fabric mod for Minecraft 26.3 that brings in the Floating Mario Head from Mario Teaches Typing.

He swells into view a few blocks in front of you with a puff of smoke, glances about, says
"Hello, I'm going to fly for you", and then comes at you. Each "Mmmmm" snaps him forward and he sags
between them, a little faster every time, until he outruns a sprint and starts matching your pace.
He does not give up. If he touches you, you die.

## Requirements

- Minecraft Java Edition 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API 0.165.0+26.3 or newer
- Java 25

## Install

From a checkout, with the Minecraft Launcher started at least once:

```
sh play.sh
```

It builds the mod, installs Fabric Loader 0.20.1 for 26.3 (the profile `fabric-loader-26.3`), puts
Fabric API and the mod in `~/.minecraft/mods`, and opens the launcher. Pick `fabric-loader-26.3` and
press Play. `sh play.sh install` skips opening the launcher, `sh play.sh dev` runs a throwaway client
with an offline player and installs nothing, `sh play.sh help` lists these.

By hand: put `mariohead-0.1.0.jar` and Fabric API in the `mods` folder of a Fabric 26.3 profile.

To use `/mariohead summon` in singleplayer, create the world with Allow Commands on, or open it to
LAN with cheats allowed.

## Calling him

- `/mariohead summon` puts him in view of you, 7 to 12 blocks away, somewhere open with a clear
  line of sight. `/mariohead summon <players>` does it for others. He refuses when there is no room
  in view, for example when you stare at the floor or a wall, and for players in creative or
  spectator.
- The Floating Mario Head Spawn Egg is in the Spawn Eggs tab of the creative inventory. A head
  from an egg chases the nearest player in survival or adventure within 32 blocks.

The command needs operator rights (permission level 2).

## How he behaves

- He floats about two blocks above the floor or water and flies straight toward you when his whole
  body fits the route. Around obstacles he plans through doors, stairs, caves and shafts, and heads
  off a player who runs across his path. Boats do not stop the chase.
- He tilts toward players above or below him and finishes turning before each dash. At his normal
  hover height he stays level.
- He is about three blocks wide and prefers a way round he fits through. He can shrink to squeeze
  through doors, caves and gaps down to one block, but only while nobody is looking at him: watched,
  he waits at a gap too small for him. He never pokes through blocks while he chases. A gap under
  one block stops him. Leaves, grass and flowers do not stop him at all.
- Hide under rock where he does not fit, in a cave, a mountain or a dugout, and he does not shrink
  for you. After five seconds he announces in chat that he is going to move underground and blasts
  a tunnel his own size to you, a TNT blast on every "Mmmm", and those hums come through the rock at
  full volume. With mobGriefing off, or through obsidian, he passes through the ground instead.
  Wall yourself in out in the open and he comes up under you the same way.
- He cannot be hurt. `/kill` removes him. He leaves on Peaceful.
- Before he says hello he will go after whoever else is near if his player leaves. After hello
  the chase belongs to that player, and he vanishes when they die, leave, change dimension or
  turn creative or spectator.
- A catch kills through armour, shields and Resistance. A Totem of Undying saves you once, and he
  is gone either way.
- He keeps the chunks around him loaded while he chases, so outrunning him only delays him.
- He is not saved with the world. Logging out or reloading ends the chase.
- His voice gets louder the closer he is to you and uses the Hostile Creatures volume slider.

## Building from source

```
./gradlew build
```

The jar is written to `build/libs`. `./gradlew test` runs the unit tests and
`./gradlew runGameTest` runs the in-world tests on a game test server.

## Credits

- Head model: the high quality Mario head Blender model (`mario_head.blend`) with its cap, iris
  and swatch textures.
- Voice: clips of the Floating Mario Head from Mario Teaches Typing.
- His arrival sound is Minecraft's own illusioner mirror move sound, lowered in pitch.
