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

Put `mariohead-0.1.0.jar` and Fabric API in the `mods` folder of a Fabric 26.3 profile.

## Calling him

- `/mariohead summon` puts him in view of you, 7 to 12 blocks away, somewhere open with a clear
  line of sight. `/mariohead summon <players>` does it for others. He refuses when there is no room
  in view, for example when you stare at the floor or a wall.
- The Floating Mario Head Spawn Egg is in the Spawn Eggs tab of the creative inventory. A head
  from an egg chases the nearest player in survival or adventure within 32 blocks.

The command needs operator rights (permission level 2).

## How he behaves

- He floats about two blocks above the floor and plans his way along it, through doors, up stairs
  and around walls. When he cannot walk there he flies straight at you.
- He is about three blocks wide and shrinks to squeeze through doors, caves and gaps down to one
  block, then swells back. He never pokes through blocks. A gap under one block stops him.
- He cannot be hurt. `/kill` removes him. He leaves on Peaceful.
- Before he says hello he will go after whoever else is near if his player leaves. After hello
  the chase belongs to that player, and he vanishes when they die, leave, change dimension or
  turn spectator.
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
