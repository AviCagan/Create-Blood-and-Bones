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

## Licence

All code and assets in this repository are released under the MIT licence (see `LICENSE`).
You may use, modify and redistribute them, including in your own mods, as long as you keep the
copyright notice. Credit is appreciated but the licence only requires the notice.

Dependencies are **not** bundled in this repository or in the built jar. Players install them
separately: Create (MIT), Sable (see its own licence), Create Enchantment Industry (LGPL-3.0),
Create: Dragons Plus (LGPL-3.0) and Create Diesel Generators (MIT). Nothing from those projects
is copied into this repository; the mod only calls their public APIs.

## AI usage disclosure

This mod is built with AI assistance. The design, direction, decisions, testing and review are
done by a human (AviCagan); most of the code is written by Anthropic's Claude Code under that
direction, from the design brief in `docs/ARCHITECTURE-PROPOSAL.md`. Every commit made this way
carries a `Co-Authored-By` trailer naming the tool, so the history shows exactly what was
AI-assisted.

Textures, models, icons, gallery images and the mod page description are made by hand, not
generated.

When this mod is published:

- **Modrinth** requires the "Contains AI-generated content" disclosure whenever "a substantial
  portion of the project's code is a product of AI output" (Content Rules §6.1). This project
  will enable that disclosure with the *code* category, and will not use AI-generated images
  anywhere on the project page (Content Rules §6.2 forbids it).
- **CurseForge** currently has no general AI-code rule. Its moderation policy only requires a
  visible disclaimer on AI-modified showcase images that could misrepresent the mod, and its
  author terms require keeping third-party licence notices. This project follows both: no
  AI-modified showcase images, and dependency licences are credited above.
