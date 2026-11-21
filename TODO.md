# Smart Tap — Implementation Checklist

A rough order to build things in. Each phase builds on the ones before it. Design decisions live in `mindmap.md`.

## 0. Check your assumptions
- [x] Tap finds the drain behind it and the container below it
- [X] Check reported capacity of an **empty** basin and casting table, for both Tinkers' and Productive Metalworks
- [X] Check what an empty casting table reports when no cast is on it (it should refuse to pour)
- [X] Check which fluid(s) a drain exposes when the smeltery holds two metals (example workflow 3)
- [X] PM reports a flat 1000 mB capacity, so check whether a dry-run fill reveals the real amount: `target.fill(new FluidStack(fluid, 1000), SIMULATE)` against a basin, a table with an ingot cast, and a partially filled table. If it returns the recipe amount, use that instead of capacity; if not, fall back to looking up PM's casting recipe (phase 12)
- [X] Record what a table **without a cast** returns from a dry-run fill (expect 0)
- [X] Check that PM's drain will drain a non-bottom fluid: `source.drain(new FluidStack(<fluid in tank 1>, 90), SIMULATE)` should return that fluid at the full amount

## 1. Core pour (the minimum useful tap)
- [ ] Replace `describeConnections` with an `activate()` method in `SmartTapBlockEntity`
- [ ] Ignore activation if the tap's tank isn't empty (already pouring or holding leftover)
- [ ] Compute how much the target needs (capacity − current amount)
- [ ] Choose a fluid: for now, the first fluid in the drain that has enough
- [ ] Dry-run (`SIMULATE`) the drain from the source **and** the fill into the target; bail out if either falls short
- [ ] For real (`EXECUTE`): drain from the source into the tap's tank
- [ ] Remember the pour target (position), so `serverTick` knows a pour is in progress
- [ ] `serverTick`: each tick, fill the target with up to `flowRate` mB and drain only what it accepted
- [ ] Stop when the tank is empty or the target stops accepting fluid
- [ ] Hardcode `flowRate` for now (upgrades come later)
- [ ] Test example workflows 1 and 2 (exact pour; the 2 ingots stay in the smeltery)
- [ ] Test multiple taps on one foundry (example workflow 4)

## 2. Interrupted pours and leftovers
- [ ] Target removed mid-pour: stop pouring and keep the leftover in the tank
- [ ] Target replaced: push the leftover into the new target (on activation, or automatically?)
- [ ] Decide what activation does while holding leftover fluid that doesn't match the new target

## 3. Persistence
- [ ] Keep the tank's contents when the tap is broken (data components: `collectImplicitComponents` / `applyImplicitComponents`)
- [ ] Show stored fluid in the item tooltip
- [ ] Decide what happens if the **drain** is broken mid-pour (the tap already holds the fluid, so this may just work)

## 4. Visuals
- [ ] Sync the tank to the client (`getUpdateTag` / `getUpdatePacket`, and send updates when it changes)
- [ ] Block entity renderer: draw the fluid stream while pouring (`.../blockentities/ber`)
- [ ] Real model and texture for the tap
- [ ] Pouring sound or particles (the "satisfying to use" goal)

## 5. Redstone
- [ ] Activate on a rising redstone edge (`neighborChanged` + `level.hasNeighborSignal`)
- [ ] Optional: redstone modes (pulse / while powered / ignore) once the UI exists

## 6. Settings UI
- [ ] Shift + right-click opens a menu (`.../gui/menus`) and screen (`.../gui/screens`)
- [ ] Show the tap's tank contents
- [ ] "Empty tap" button (client → server payload, `.../networking/payload`)
- [ ] Where should emptied fluid go? (back into the drain? voided?)

## 7. Upgrades (Pipez-style)
- [ ] Decide on tiers, names and flow rates
- [ ] Upgrade item(s)
- [ ] Upgrade slot in the tap's menu; store the upgrade in the block entity and save it
- [ ] Flow rate comes from the installed upgrade
- [ ] Drop the upgrade (or keep it on the item) when the tap is broken
- [ ] Decide whether upgrades also unlock features (filters, redstone modes, ...)

## 8. Fluid filters
- [ ] Whitelist / blacklist by fluid ID
- [ ] Filter UI (ghost slots or a fluid picker)
- [ ] Use the filter when choosing which fluid to pour (phase 1's "first fluid with enough")
- [ ] Test example workflow 3 (gold + iron)

## 9. Pour until
- [ ] Define the stop conditions (e.g. pour until the source has less than X, pour N times, ...)
- [ ] Add to the UI

## 10. Presets and copying
- [ ] Preset menu with sensible defaults (the "low barrier to entry" goal)
- [ ] Copy and paste settings between taps (e.g. a configurator item, or sneak-clicking with a tap item)

## 11. Colors and texture mask
- [ ] Match Productive Metalworks colors
- [ ] Optional R/G/B tint setting (block color handler)
- [ ] Texture mask: right-click with a block to use its texture (`useItemOn`, probably a custom baked model)

## 12. Integrations
- [ ] Tinkers' Construct: test against its drains and casting blocks
  - [ ] Repeat the phase 0 checks for Tinkers' (capacity, dry-run fill, table without a cast, which fluids the drain exposes and drains)
- [ ] Productive Metalworks: test against its drains and casting blocks
- [ ] Generic: works with any block exposing `IFluidHandler`
- [ ] Keep optional-mod code behind `ModList.get().isLoaded(...)` checks
- [ ] Look into AE / RS and other mods worth supporting

## 13. Release prep
- [ ] Crafting recipes for the tap and its upgrades
- [ ] Move tunables (flow rates, tank size) into `Config`
- [ ] Remove the MDK example block, item and creative tab from `SmartTap.java`, and the demo values from `Config.java`
- [ ] Real description in `neoforge.mods.toml`, plus a logo
- [ ] Consider data generation (`runData`) for models, loot tables and recipes

## Later / stretch
- [ ] Scripting (inspired by Super Factory Manager)
