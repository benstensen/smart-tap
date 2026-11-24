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
  - A UI option lets you empty the tap's contents.
- **Leftovers and replaced targets (decided):**
  - Core rule: a pour only starts if it will fill the target. With tap amount x, target amount y and full recipe amount z, every pour that starts ends with x + y = z.
  - Partly filled targets (y > 0) already satisfy this: the simulated-fill probe returns z − y, so the tap pours exactly the remainder.
  - Replacing the target does **not** start a pour on its own. The tap waits for activation (right click, later redstone).
  - Activation while holding leftover:
    1. **Return first (primary):** push the leftover back into the drain. If it all goes back, continue with a normal pour (same rules as always, bottom fluid by default).
    2. **Top up (fallback, if the drain won't take it back):** treat the leftover as already reserved. If it covers what the new target needs, pour that and keep the rest. If not, draw the missing amount of the **same fluid** from the drain, all-or-nothing. If the drain doesn't have enough, or the target won't accept that fluid, do nothing.
    3. **Neither works:** do nothing and tell the player why (e.g. an action bar message). The "Empty tap" UI button is the manual way out.
  - Every trigger behaves the same way: either a pour that fills the target, or nothing. This keeps the tap safe for automation.
  - Open: is it OK that one activation can return the leftover and then not pour (e.g. the bottom fluid doesn't have enough)? Leaning yes, since the tap ends up clean.
  - Open: when player feedback goes in (action bar now, or wait for the UI)?
  - To verify: does the PM drain (and later Tinkers') accept fluid pushed into it? `source.fill(leftover, SIMULATE)`. If not, the fallback is effectively the only path.

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
- Decided: by default (no filter), the tap always pours the bottom fluid (tank 0), like a Tinkers' faucet. Filters and automatic modes make pours configurable, so they can target other fluids in the drain.

### Pour until
- (The condition for stopping a pour is still to be defined.)
- Idea: "until the foundry can't fill the target any more" (see example workflow 5, "Cast everything").

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
  - Decided: on the next activation, the leftover goes back to the drain, or tops up the next pour if the drain won't take it (see "Leftovers and replaced targets").
- To verify: what capacity do Tinkers' / Productive Metalworks casting basins and tables report before any fluid is in them? They may only know their capacity once a recipe matches.
  - Productive Metalworks: 1000mB (for both basins & tables). This is also the case when the basin is partially filled with fluid, and when a table has a cast in it. I can get the target capacity by running `target.fill(<...>, FluidAction.SIMULATE)` and seeing what the function returns. A table without a cast returns 0. I can use `source.drain()` to drain fluids other than the bottom fluid. 
  - Tinker's: I'm not checking this right now.
- To verify: which fluid(s) a drain exposes (Tinkers' drains typically expose only the bottom fluid). Affects example workflow 3 and filters.
  - Productive Metalworks: Get tanks seems to return the total number of fluid types in the foundry. Fluids are numbered from the bottom up (bottom fluid == 0, fluid above that == 1, etc.).
  - Tinker's: I'm not checking this right now.

## Example workflows
Concrete scenarios the design should handle well:
1. I have 5 blocks of iron in the smeltery.
2. I have 5 blocks and 2 ingots of iron in the smeltery.
3. I have 3 gold ingots and 5 blocks + 2 ingots of iron in the smeltery.
4. Multiple smart taps on the same foundry.
5. (Pie in the sky) "Cast everything": the foundry holds several fluids. I activate the tap once over a basin with a hopper underneath, which pulls each block out once it has cooled. The tap keeps working until no fluid left in the foundry has enough for another full block.
   - The tap pours, waits for the block to cool and be pulled out, then pours again. While the basin is busy, its simulated fill returns 0, so in this mode "target accepts 0" means "wait", not "stop".
   - It works through all fluids, not just the bottom one (see Fluid filters), and skips any that can't fill the target.
   - It stops when no fluid has enough for a full pour. Leftovers smaller than one block stay in the foundry, which is the all-or-nothing rule applied over and over.
   - Related: Pour until, Redstone control, Preset modes. It could be a preset mode ("cast everything") built on a "pour until" condition.
