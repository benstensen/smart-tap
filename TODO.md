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
Activation (`pour()`) already reserves the right amount safely: it probes the target with a simulated fill (PM's reported capacity is unreliable), enforces all-or-nothing, checks the tap's tank has room, and reserves the pour into the tap's tank; `serverTick` then pours it gradually at `flowRate`. By default it pours the bottom fluid. Phase 1 is complete, and example workflows 1–4 are tested with PM.

Done during phase 0:
- [x] Activation method (`pour()`) called on right-click
- [x] Ignore activation while the tap's tank isn't empty
- [x] Work out how much the target needs with a simulated fill, not `getTankCapacity`
- [x] All-or-nothing: bail out if the source has less than the target needs (the 2 ingots stay in the smeltery)
- [x] Check the tap's tank can hold the whole pour before draining
- [x] Drain from the source into the tap's tank, refunding (or keeping as leftover) anything from a short drain
- [x] Tank → target transfer that only drains what the target accepted

Remaining:
- [x] `transfer(from, to, request)` helper: simulates both sides, moves what both agree on, refunds anything the target refuses, and logs if fluid is ever lost. Activation and `serverTick` both use it
- [x] Split `pour()`: activation stops after filling the tap's tank; the tank → target step moves into `serverTick`
- [x] Add a saved `pouring` flag (`saveAdditional` / `loadAdditional`) so a pour resumes after a reload, and so "pouring" can be told apart from "holding leftover" (both have a non-empty tank)
- [x] `serverTick`: while pouring, offer `tank.getFluid().copyWithAmount(Math.min(flowRate, tank.getFluidAmount()))` to the target, then drain exactly what it accepted
- [x] Look up the target handler every tick (it can be broken or replaced mid-pour)
  - [ ] (Optional, later) Switch to `BlockCapabilityCache` for the source and target lookups
- [x] Stop pouring when the tank is empty, or when the target is gone or accepts 0 (anything left becomes leftover, see phase 2)
- [x] Hardcode `flowRate` (mB per tick) for now; upgrades come in phase 7
- [x] Expose a read-only view of the tank to Jade (unsided capability only)
- [x] Default fluid: always pour the bottom fluid (tank 0). Targeting other tanks comes with filters and automatic modes (phase 8)
- [x] Clean up:
  - [x] Drop the unused `player` parameter from `pour()`
  - [X] Remove the commented-out calls in `SmartTapBlock`
  - [X] Decide whether to keep `describeConnections` as a debug tool
- [X] Test example workflows 1 and 2 with PM (exact pour; leftovers stay in the smeltery)
- [X] Test example workflow 3 with PM (gold + iron): the tap pours only the bottom fluid, and does nothing if the bottom fluid doesn't have enough for the target
- [X] Test multiple taps on one foundry (example workflow 4)
- [X] Test breaking the target mid-pour: the remainder should stay in the tap, not vanish

## 2. Interrupted pours and leftovers
- [x] Target removed mid-pour: stop pouring and keep the leftover in the tank (done and tested in phase 1)
Design: see "Leftovers and replaced targets" in `mindmap.md`. Rule: a pour only starts if it will fill the target; replacing the target never starts a pour by itself.
- [x] Decide what activation does while holding leftover (return first, top up as a fallback, otherwise do nothing)
- [ ] Check your assumptions: does PM's drain accept fluid pushed into it? `source.fill(<leftover>, SIMULATE)` with a foundry that has room, and with a full one. Record the result in `mindmap.md`
- [ ] Return first: in `pour()`, replace the "still holding leftover" early return with `transfer(tank, source, tank.getFluid().copy())`. If the tank is now empty, carry on with the normal pour
- [ ] Top up (fallback when the drain won't take everything back):
  - [ ] Work out `need = getTargetCapacity(target, <leftover>)`. If it's 0, do nothing (the target won't take that fluid)
  - [ ] Leftover ≥ need: reserve nothing new, just start pouring (`serverTick` stops once the target is full, and the rest stays as leftover)
  - [ ] Leftover < need: probe that the drain has `need − leftover` of the **same fluid** (not just tank 0), then reserve it with `transfer`, all-or-nothing (send it back if it comes up short)
- [ ] Neither works: do nothing, and log why (player feedback comes later)
- [ ] Don't start a new activation while `pouring` is true (the tank isn't empty then, so make sure the leftover logic doesn't treat an in-progress pour as leftover)
- [ ] Decide on player feedback for "didn't pour" cases: action bar message now, or wait for the UI (phase 6)
- [ ] Test: break the target mid-pour, place a new empty one, activate. The leftover should go back to the drain and a full pour should start
- [ ] Test: same, but with the drain full or unable to accept fluid. The pour should be topped up from the drain, or nothing should happen
- [ ] Test: leftover of a fluid the new target won't accept (find a cast with no recipe for it). It should go back to the drain, or nothing should happen

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
- [ ] If emptying (or anything else) changes what the tap exposes as a capability (see `ModRegistry#registerCapabilities`), call `level.invalidateCapabilities(worldPosition)` whenever it changes, since NeoForge and other mods cache capability lookups

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
- [ ] Configurable fluid selection: filtered and automatic pours can target tanks other than the bottom one (`pour(int tankIdx)` already takes an index). Decide the rule, e.g. the first matching fluid (bottom first) with enough for the target
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
- [ ] "Cast everything" mode (example workflow 5 in `mindmap.md`): one activation keeps pouring full blocks, across all fluids, until none has enough. Needs: a mode that waits instead of stopping while the target is busy (simulated fill returns 0 while the block cools), a way to notice the target is free again (check each tick, or every N ticks), and fluid selection beyond tank 0 (phase 8). Probably builds on Pour until (phase 9) and presets (phase 10)
