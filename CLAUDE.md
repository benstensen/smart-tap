# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Smart Tap (`mod_id=smart_tap`, package `dev.fishna.smarttap`) is a NeoForge mod for Minecraft 1.21.1 (Java 21), built with ModDevGradle (`net.neoforged.moddev`) and Parchment mappings. It is designed around an **optional** integration with the Productive Metalworks mod (`productivemetalworks`), with JEI available in dev.

The Java sources are still largely the NeoForge MDK template (example block/item/creative tab, demo config values in `Config.java`, matching lang keys). Treat those as placeholders to replace, not established conventions.

## Commands

Use `./gradlew` (Git Bash) or `.\gradlew.bat` (PowerShell).

- `./gradlew build` — compile and package the jar into `build/libs/` (this is what CI runs in `.github/workflows/build.yml`)
- `./gradlew runClient` / `./gradlew runServer` — launch dev game instances (working dir `run/`)
- `./gradlew runGameTestServer` — run all registered GameTests in the `smart_tap` namespace and exit (crashes if none are registered). GameTests can also be run in-game with `/test`.
- `./gradlew runData` — run data generators; output goes to `src/generated/resources/` (included as a resource source dir)
- `./gradlew --refresh-dependencies` / `./gradlew clean` — fix missing/stale dependencies

There are no unit tests or linters configured.

## Build structure worth knowing

- **Versions and mod metadata live in `gradle.properties`** (`neo_version`, `minecraft_version`, `mod_version`, `mod_authors`, dependency versions like `pm_file_id` and `jei_version`). Change them there, not in source.
- **`neoforge.mods.toml` is a template** at `src/main/templates/META-INF/`. The `generateModMetadata` task expands `${...}` placeholders from `gradle.properties` into `build/generated/sources/modMetadata`. Edit the template, never the generated copy. Mod dependencies (including the optional `productivemetalworks` entry, loaded `AFTER`) are declared here.
- **External mod dependencies** come from CurseMaven (`curse.maven:productive-metalworks-1184570:${pm_file_id}`, where the file ID pins the version) and BlameJared maven (JEI). Both are `compileOnly` + `runtimeOnly`. Because Productive Metalworks is optional at runtime for players, code that touches its classes must be isolated and only loaded when `ModList.get().isLoaded("productivemetalworks")` is true.
- `SmartTap.MODID` must match `mod_id` in `gradle.properties`.

## Code layout

- `SmartTap` — the common `@Mod` entrypoint. It creates the `DeferredRegister`s, registers them and the `COMMON` config spec on the mod event bus, and subscribes to game events on `NeoForge.EVENT_BUS`.
- `SmartTapClient` — a separate `@Mod(dist = Dist.CLIENT)` entrypoint, also an `@EventBusSubscriber` for client events. Client-only code (rendering, `Minecraft` access, screens) belongs here or in classes it references, so it never loads on a dedicated server. It also registers NeoForge's auto-generated config screen.
- `Config` — a `ModConfigSpec`. Every config option needs a matching `smart_tap.configuration.<key>` entry in `assets/smart_tap/lang/en_us.json` for the config screen.
