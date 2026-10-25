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

Install Prism Launcher and sign into your Microsoft account in Prism, then run:

```
sh play.sh
```

This builds the mod, installs it and Fabric API into `~/.minecraft/mods`, and launches the
Mario Head instance through Prism with Minecraft 26.3 and Fabric Loader 0.20.1. The instance
shares your existing Minecraft folder, including worlds and settings. Java 25 is selected
from Gradle's downloaded JDKs or `/usr/lib/jvm/java-25-openjdk`; set `MARIOHEAD_JAVA` to
another Java 25 executable if needed.

`sh play.sh setup` prepares the instance without launching. `sh play.sh official` builds and
opens the official launcher instead; `sh play.sh install` only installs for that launcher.
`sh play.sh dev` runs a separate offline development game. `MINECRAFT_DIR` overrides the game
folder, and `PRISM_DIR` overrides Prism's data folder. Run only one game using these worlds at a time.

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
- Against boats, including boats on ice, each dash grows 3% faster and longer. Quick exits and
  switching boats keep the buildup. It resets after at least five seconds on foot and eight blocks
  of movement. Once you have used elytra during the chase, the buildup never resets.
- Each "Mmmm" adds 0.3% speed, reset when he starts a taunt.
- During the first five minutes, coming within eight blocks on land gives each dash one 55% chance
  of a taunt, including during ordinary jumps. After each taunt finishes, there is a 30-second
  cooldown. He follows at your pace while speaking, giving you a chance to escape, then
  resumes with one dash that is 10% faster and longer. His lines do not overlap, and taunts and the
  underground announcement get half a second of breathing room before and after.
- He is about three blocks wide and prefers a way round he fits through. He can shrink to squeeze
  through doors, caves and gaps down to one block, but only while nobody is looking at him: watched,
  he waits at a gap too small for him. He never pokes through blocks while he chases. A gap under
  one block stops him. Leaves, grass and flowers do not stop him at all.
- Hide under rock where he does not fit, in a cave, a mountain or a dugout, and he does not shrink
  for you. After five seconds he announces in chat that he is going to move underground and blasts
  a tunnel his own size to you, a TNT dash on each full-length "Mmmm", with a quarter-second gap between hums. After each hum he has a 20% chance
  of a tunnel-themed line while the dashes continue. These lines have a five-second cooldown after
  finishing, cycle through all three before repeating, and never repeat back-to-back. Those hums are louder nearby and fade
  with distance. With mobGriefing off, or through obsidian, he passes through the ground instead.
  Wall yourself in out in the open and he comes up under you the same way.
- Ender-pearl escapes trigger a temporary catch-up burst until he recovers the distance you gained.
  Repeated pearls keep the burst active; he still has to navigate or tunnel through obstacles.
- Hide near a bed in a sealed room, or behind a closed house door, and he says "Hmm, you have a
  nice place here!", pauses half a second, then asks "Can I have it?" His TNT dash starts after
  another 0.2 seconds. This sequence can happen even after he has started digging.
- A room with a chest can distract him once per chase. He asks for food or a drink, then
  attacks the chests for at most five seconds, destroying their contents when mobGriefing is on.
  Adding more chests cannot extend the diversion. He returns to his original player with
  "Hmm, guess what time it is? It's time for you to die!"
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
