# Create: Blood & Bones

A [Create](https://github.com/Creators-of-Create/Create) addon for Minecraft 1.21.1 / NeoForge:
mob carcass butchery, blood as a processable fluid, body-horror progression and gore-themed
decoration, built on Create's kinetic, fluid and contraption systems, with ragdoll physics
provided by [Sable](https://github.com/ryanhcode/sable).

- Mod id: `bloodandbones`
- Target: Minecraft 1.21.1, NeoForge 21.1.x, Create 6.0.x, Sable 2.0.x, Java 21
- Design: see `docs/ARCHITECTURE-PROPOSAL.md`

## Building

```
./gradlew build
```

The first build downloads NeoForge and decompiles Minecraft (several minutes); later builds are
incremental. `./gradlew runClient` / `runServer` / `runData` are configured by ModDevGradle.
