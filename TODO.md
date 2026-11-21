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
Activation (`pour()`) already reserves the right amount safely: it probes the target with a simulated fill (PM's reported capacity is unreliable), enforces all-or-nothing, checks the tap's tank has room, drains the source, and refunds a short drain. What's left is turning the instant pass-through into a gradual pour.

Done during phase 0:
- [x] Activation method (`pour()`) called on right-click
- [x] Ignore activation while the tap's tank isn't empty
- [x] Work out how much the target needs with a simulated fill, not `getTankCapacity`
- [x] All-or-nothing: bail out if the source has less than the target needs (the 2 ingots stay in the smeltery)
- [x] Check the tap's tank can hold the whole pour before draining
- [x] Drain from the source into the tap's tank, refunding (or keeping as leftover) anything from a short drain
- [x] Tank → target transfer that only drains what the target accepted

Remaining:
- [ ] Split `pour()`: activation stops after filling the tap's tank; the tank → target step moves into `serverTick`
- [ ] Add a saved `pouring` flag (`saveAdditional` / `loadAdditional`) so a pour resumes after a reload, and so "pouring" can be told apart from "holding leftover" (both have a non-empty tank)
- [ ] `serverTick`: while pouring, offer `tank.getFluid().copyWithAmount(Math.min(flowRate, tank.getFluidAmount()))` to the target, then drain exactly what it accepted
- [ ] Look up the target handler every tick (it can be broken or replaced mid-pour); `BlockCapabilityCache` is the efficient way once this works
- [ ] Stop pouring when the tank is empty, or when the target is gone or accepts 0 (anything left becomes leftover, see phase 2)
- [ ] Hardcode `flowRate` (mB per tick) for now; upgrades come in phase 7
- [ ] Choose the fluid: loop over the source's tanks (bottom first) and pick the first one with enough for the target, instead of always using tank 0
- [ ] Clean up: drop the unused `player` parameter from `pour()`, remove the commented-out calls in `SmartTapBlock`, and decide whether to keep `describeConnections` as a debug tool
- [ ] Test example workflows 1 and 2 with PM (exact pour; leftovers stay in the smeltery)
- [ ] Test example workflow 3 with PM (gold + iron; the tap should skip a fluid that doesn't have enough)
- [ ] Test multiple taps on one foundry (example workflow 4)
- [ ] Test breaking the target mid-pour: the remainder should stay in the tap, not vanish

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
- [ ] (Optional) Conditional ticker, so idle taps don't tick at all. Pairs well with a pouring model variant. Steps:
  - [ ] Add `public static final BooleanProperty POURING = BooleanProperty.create("pouring")` to `SmartTapBlock`, add it in `createBlockStateDefinition`, and default it to `false` in the constructor
  - [ ] Add `pouring=true/false` variants to `blockstates/smart_tap.json` (every facing × pouring combination)
  - [ ] Whenever the block entity starts or stops pouring, also update the blockstate: `level.setBlock(worldPosition, getBlockState().setValue(SmartTapBlock.POURING, ...), Block.UPDATE_ALL)`
  - [ ] In `getTicker`, return `null` when `state.getValue(POURING)` is false (Minecraft re-checks `getTicker` when the blockstate changes)
  - [ ] Keep the early return in `serverTick` as a safety net, and decide whether the saved `pouring` field is still needed or the blockstate alone is enough

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
