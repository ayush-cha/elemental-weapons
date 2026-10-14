# Elemental Weapons

Fabric mod for Minecraft Java Edition 26.1.2. Adds four distinct weapons and four elemental gems to a dedicated **Elemental Weapons** Creative tab. The weapons also appear in Combat. Every item texture is 16×16 pixels.

| Weapon | Effect when hitting a living target |
|---|---|
| Surtr's Brand | Burns the target for 5 seconds |
| Rán's Reaper | Slows the target for 4 seconds and extinguishes the wielder |
| Gaia's Maul | Weakens the target for 5 seconds |
| Fujin's Shoge | Lifts the target for 1.5 seconds |

Right-click abilities:

| Weapon | Ability | Cooldown |
|---|---|---|
| Surtr's Brand | Shoots a small fireball | 5 seconds |
| Rán's Reaper | Sends a three-block-wide water wave forward for 5 seconds, pushing nearby living targets | 15 seconds |
| Gaia's Maul | Raises a 3x3 stone wall two blocks in front of the player for 5 seconds | 15 seconds |
| Fujin's Shoge | Shoots a wind charge | 7.5 seconds |

The Fire Gem, Water Gem, Earth Gem, and Air Gem share a pearl-like silhouette and retain their elemental colours. Surtr's Brand has a fiery sword, Gaia's Maul a broad hammer, Rán's Reaper a scythe, and Fujin's Shoge a hooked dagger with a cord and ring. The wall and water occupy air, short grass, dead bushes, and small flowers; replaced plants return when the effect expires. Rán's Reaper water advances straight ahead without spreading sideways or down. Temporary blocks are tracked in world save data, so unloaded chunks are cleaned when loaded again, even after a restart. Player changes to a temporary block are kept. If there is no room to place a wall or water source, the ability does not start its cooldown.

Each weapon has 500 durability and can be repaired with iron ingots. Its shaped recipe uses one iron sword, the matching elemental gem, and the original elemental ingredient:

| Weapon | Third ingredient |
|---|---|
| Surtr's Brand | Blaze powder |
| Rán's Reaper | Prismarine shard |
| Gaia's Maul | Moss block |
| Fujin's Shoge | Feather |

Every desert pyramid, jungle temple, dungeon, trial chamber supply chest, and trial chamber reward table has a 25% chance to add one random elemental gem. The four gems have equal odds within that bonus pool. Existing loot remains intact; unopened chests in existing worlds may receive the new loot when their contents generate. The gems keep their original internal `*_stone` IDs so existing items and recipes remain compatible.

## Build

Install JDK 25 or newer. From this directory, run `./gradlew build` (or `gradlew.bat build` on Windows). The included wrapper uses Gradle 9.8.1. The mod JAR will be in `build/libs`. Install it with Fabric Loader and Fabric API for Minecraft 26.1.2. If `java -version` shows Java 8, set `JAVA_HOME` to your JDK and put its `bin` folder first on `PATH`.

The project builds and runs unit tests with JDK 25 and Gradle 9.8.1. In-game testing remains to be done.
