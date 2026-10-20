# Football (Fabric, Minecraft 26.2)

Phase 1: items + ball entity.

- `sulfur_goo` – dropped by sulfur cubes (1–3).
- `football` – item; right-click a block to place the ball. Craft:
  ```
  G W G      G = sulfur goo
  G B G      W = white wool, B = black wool
  G G G
  ```
- `football` entity – `Mob` with no AI, physics from sulfur-cube attributes
  (`bounciness`, `friction_modifier`, `air_drag_modifier`, `knockback_resistance`). Walk into it to roll it, shift + empty hand right-click to pick it up.

## Build (JDK 25)
```
gradle wrapper --gradle-version 9.5.1   # or copy gradlew + gradle/wrapper/gradle-wrapper.jar from another mod
./gradlew build
./gradlew runClient
```
Jar: `build/libs/football-0.1.0.jar`
