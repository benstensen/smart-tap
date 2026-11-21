# Smart Tap — Design Notes

A Minecraft mod idea: a "smart" tap that pours molten fluid from a foundry / smeltery into target containers. This document is a text conversion of a mind map. Nested bullets mean "child of". Items ending in `?` are open questions or undecided ideas, not decisions.

## Version / Mod Loader
- Decided: NeoForge, Minecraft 1.21.1.

## Pouring model (decided)
How a pour works end to end. These decisions resolve several of the snags below.
- **Activation:** the tap only pulls fluid when it is activated (right click, and later redstone / presets).
- **All or nothing:** on activation, the tap works out how much the target needs to be full. It only pulls if the source has at least that much; otherwise nothing moves.
  - Example: 5 blocks + 2 ingots of iron poured into a basin → the basin gets filled and the 2 ingots stay in the smeltery.
- **Instant reservation:** once activated, the tap instantly takes the full required amount from the source's internal tank into its own internal tank.
- **Gradual flow:** the tap then pours from its internal tank into the target over time. No instant transfer.
  - Flow rate depends on the tap's upgrade tier (see Upgrades).
- **Interrupted pour (target removed / filled by something else):**
  - Fluid already poured into the target stays in the target.
  - The tap keeps the leftover in its internal tank.
  - If the target is replaced, the tap can push the leftover into it.
  - A UI option lets you empty the tap's contents.

## Motivation
- It's annoying to automate pouring from a foundry / smeltery, to the point where I don't even want to use it for ore doubling.
- It's frustrating to get stuck with 2 ingots' worth of molten material in a casting basin, for example.
- The robust automation options are kinda heavy / manual and seem annoying to do.

## Goals
- The tap should have default settings that make it easy to use, with a low barrier to entry.
- Satisfying to use.

## Features

### Perfect pours
- Read the capacity of the target container.
  - Basin
  - Casts
  - Tanks

### Fluid filters
- Target based on fluid ID.
- Whitelist / blacklist.
- Probably no mixed AND / OR filter expressions.

### Pour until
- (No sub-notes; the condition for stopping a pour is still to be defined.)

### Fluid reservation
- Purpose: ensure that something can get fully poured.
- Decided: the tap has an internal tank that is filled instantly on activation (see Pouring model).
  - Persist the inventory on break.

### Upgrades
- Decided: upgrade items, similar to the pipe upgrades in the Pipez mod.
- Higher tiers flow faster.
- Open: number of tiers, their names and flow rates; whether upgrades also unlock features (filters, redstone, scripting)?

### Scripting?
- Advanced feature.
- Inspiration from SFM (Super Factory Manager).

### Preset modes
- A menu to select a preset.

### Copying settings
- (No sub-notes.)

### Interaction: can you right-click it to pour, like a normal tap?
- Right click: pour.
- Shift + right click: open settings.

### Redstone control
- (No sub-notes.)

### Colors
- Match the colors of Productive Metalworks.
- Generic R/G/B values in settings?
- Block texture mask:
  - Example: choose stone bricks, right-click the tap with a stone brick. The tap keeps its shape but gains the stone bricks texture.

## Visuals
- Block texture
- Settings UI

## Integrations
- Tinker's Construct
- Productive Metalworks
- Generic?
- AE / RS? (Applied Energistics / Refined Storage)
- Are there other mods that this could reasonably plug into?

## Snags (known problems to solve)
- No duplicating fluid.
  - Addressed by the pouring model: fluid only moves between tanks, and only the amount the receiver actually accepts.
- Break mid-pour?
  - Break the tap.
  - Break the drain.
- Multiple taps.
  - Could be addressed if the tap has [text cut off in the source image; probably "an internal inventory", see Fluid reservation].
  - Addressed by instant reservation: each tap takes its whole amount up front, so taps can't race for the same fluid.
- Partially-filled container.
  - Handled by computing the remaining capacity (capacity minus current amount) on activation.
- Target removed or filled mid-pour.
  - Decided: poured fluid stays in the target; the tap keeps the leftover (see Pouring model).
- To verify: what capacity do Tinkers' / Productive Metalworks casting basins and tables report before any fluid is in them? They may only know their capacity once a recipe matches.
- To verify: which fluid(s) a drain exposes (Tinkers' drains typically expose only the bottom fluid). Affects example workflow 3 and filters.

## Example workflows
Concrete scenarios the design should handle well:
1. I have 5 blocks of iron in the smeltery.
2. I have 5 blocks and 2 ingots of iron in the smeltery.
3. I have 3 gold ingots and 5 blocks + 2 ingots of iron in the smeltery.
4. Multiple smart taps on the same foundry.
