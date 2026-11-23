package dev.fishna.smarttap.block;

import org.jetbrains.annotations.Nullable;

import dev.fishna.smarttap.SmartTap;
import dev.fishna.smarttap.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Per-tap data and logic. One instance exists for every Smart Tap placed in the world.
 * <p>
 * Docs: https://docs.neoforged.net/docs/1.21.1/blockentities/
 * Fluid handling: https://docs.neoforged.net/docs/1.21.1/datastorage/capabilities
 */
public class SmartTapBlockEntity extends BlockEntity {
    /** Internal buffer for the "fluid reservation" idea. Capacity is in millibuckets (mB). */
    private final FluidTank tank = new FluidTank(4 * FluidType.BUCKET_VOLUME) {
        @Override
        protected void onContentsChanged() {
            setChanged(); // tell Minecraft this chunk needs saving
        }
    };

    private boolean pouring = false;
    private int flowRate = 20; // mB per tick

    public SmartTapBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SMART_TAP_BE.get(), pos, state);
    }

    public FluidTank getTank() {
        return tank;
    }

    /**
     * A look-but-don't-touch view of the tank, exposed as a capability so tools like Jade can show its contents.
     * Fill and drain always do nothing, so pipes and other taps can't push into or pull out of the tap.
     * Registered in ModRegistry#registerCapabilities.
     */
    private final IFluidHandler readOnlyTank = new IFluidHandler() {
        @Override
        public int getTanks() {
            return tank.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return tank.getFluidInTank(index).copy(); // a copy, so callers can't change the real tank
        }

        @Override
        public int getTankCapacity(int index) {
            return tank.getTankCapacity(index);
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    public IFluidHandler getReadOnlyTank() {
        return readOnlyTank;
    }

    /** Called every tick (20/s) on the server only. See SmartTapBlock#getTicker. */
    public void serverTick() {
        if (!pouring) return;

        IFluidHandler target = getTargetHandler();
        if (target == null) {
            pouring = false;
            setChanged();
            return;
        }

        FluidStack moved = transfer(tank, target, tank.getFluid().copyWithAmount(Math.min(flowRate, tank.getFluidAmount())));

        // Done when the tank is empty, or the target stopped accepting (anything left stays as leftover).
        if (moved.isEmpty() || tank.isEmpty()) {
            pouring = false;
            setChanged();
        }
    }

    // ---- Neighbours ----

    private Direction facing() {
        return getBlockState().getValue(SmartTapBlock.FACING);
    }

    /** The fluid handler of the drain behind the tap, or null if there isn't one. */
    public @Nullable IFluidHandler getSourceHandler() {
        if (level == null) return null;
        BlockPos drainPos = worldPosition.relative(facing().getOpposite());
        // The "side" argument is the face of the *drain* we're touching, which points toward us.
        return level.getCapability(Capabilities.FluidHandler.BLOCK, drainPos, facing());
    }

    /** The fluid handler of the block below the tap (basin, table, tank...), or null. */
    public @Nullable IFluidHandler getTargetHandler() {
        if (level == null) return null;
        return level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.below(), Direction.UP);
    }

    /**
     * EXAMPLE: prints what the tap can see. Shows how to read fluids from neighbouring blocks.
     * Replace with real pouring once you're comfortable with IFluidHandler.
     */
    public void describeConnections(Player player) {
        IFluidHandler source = getSourceHandler();
        if (source == null) {
            player.sendSystemMessage(Component.literal("No fluid source behind the tap"));
        } else {
            for (int i = 0; i < source.getTanks(); i++) {
                FluidStack fluid = source.getFluidInTank(i);
                if (!fluid.isEmpty()) {
                    player.sendSystemMessage(Component.literal("Source: ")
                            .append(fluid.getHoverName())
                            .append(" " + fluid.getAmount() + " mB"));
                }
            }
        }

        IFluidHandler target = getTargetHandler();
        if (target == null) {
            player.sendSystemMessage(Component.literal("No fluid container below the tap"));
        } else {
            for (int i = 0; i < target.getTanks(); i++) {
                player.sendSystemMessage(Component.literal("Target tank " + i + ": "
                        + target.getFluidInTank(i).getAmount() + " / " + target.getTankCapacity(i) + " mB"));
            }
        }
    }

    public void pour() {
        pour(0);
    }

    public void pour(int tankIdx) {
        // Don't start a new pour while still pouring or holding leftover fluid; otherwise the leftover
        // would be poured on top of the amount calculated below.
        if (!tank.isEmpty()) {
            SmartTap.LOGGER.debug("Smart Tap at {}: still holding {} mB of {}",
                    worldPosition, tank.getFluidAmount(), tank.getFluid().getHoverName().getString());
            return;
        }

        IFluidHandler source = getSourceHandler();
        if (source == null || source.getTanks() < tankIdx + 1) {
            SmartTap.LOGGER.debug("Smart Tap at {}: no source to pour from, or source has no tank {}", worldPosition, tankIdx);
            return;
        }

        IFluidHandler target = getTargetHandler();
        if (target == null) {
            SmartTap.LOGGER.debug("Smart Tap at {}: no target to pour into", worldPosition);
            return;
        }

        // Probe to see if there's enough fluid for a full pour
        FluidStack sourceFS = source.getFluidInTank(tankIdx);
        if (sourceFS.isEmpty()) {
            return;
        }

        int targetCap = getTargetCapacity(target, sourceFS);
        if (targetCap > sourceFS.getAmount()) {
            SmartTap.LOGGER.debug("Smart Tap at {}: not enough fluid to fill the target (source has {} mB of {})",
                    worldPosition, sourceFS.getAmount(), sourceFS.getHoverName().getString());
            return;
        }

        if (targetCap == 0) {
            SmartTap.LOGGER.debug("Smart Tap at {}: target won't accept {}", worldPosition, sourceFS.getHoverName().getString());
            return;
        }

        // Make sure the tap's own tank can hold the whole pour before taking anything from the source.
        if (tank.fill(sourceFS.copyWithAmount(targetCap), FluidAction.SIMULATE) < targetCap) {
            SmartTap.LOGGER.debug("Smart Tap at {}: target needs {} mB, but the tap can only hold {} mB",
                    worldPosition, targetCap, tank.getCapacity());
            return;
        }

        // Reserve the whole pour: move it from the source into the tap's tank.
        FluidStack reserved = transfer(source, tank, sourceFS.copyWithAmount(targetCap));
        SmartTap.LOGGER.debug("Smart Tap at {}: target capacity {} mB, reserved {} mB from source",
                worldPosition, targetCap, reserved.getAmount());

        // All-or-nothing: if the source gave less than the target needs, send it all back.
        if (reserved.getAmount() < targetCap) {
            FluidStack returned = transfer(tank, source, reserved);
            // Whatever the source wouldn't take back stays in the tap as leftover.
            SmartTap.LOGGER.debug("Smart Tap at {}: source gave less than expected ({} of {} mB), returned {} mB, keeping {} mB as leftover",
                    worldPosition, reserved.getAmount(), targetCap, returned.getAmount(), reserved.getAmount() - returned.getAmount());
            return;
        }

        pouring = true;
        setChanged();
        SmartTap.LOGGER.debug("Smart Tap at {}: started pouring {} mB of {}",
                worldPosition, reserved.getAmount(), reserved.getHoverName().getString());
    }

    /** Return the amount of the given fluid that a target IFluidHandler would accept.
     *  @param target The IFluidHandler for the target of the pour
     *  @param fluidStack The FluidStack for the fluid that would be poured
     * */
    private int getTargetCapacity(IFluidHandler target, FluidStack fluidStack) {
        FluidStack tMaxFluidStack = fluidStack.copyWithAmount(fluidStack.getAmount() + 1);
        return target.fill(tMaxFluidStack, FluidAction.SIMULATE);
    }

    /**
     * Moves up to `request` from `from` to `to`, and returns what was actually moved ({@link FluidStack#EMPTY} if nothing).
     * Only loses fluid if `to` accepts less than it promised and `from` then refuses the refund; that case is logged.
     */
    private FluidStack transfer(IFluidHandler from, IFluidHandler to, FluidStack request) {
        // Simulate draining request from `from`, and simulate filling `to`. Take smaller amount to move.
        FluidStack simulatedFromDrain = from.drain(request, FluidAction.SIMULATE);
        int amountToMove = Math.min(simulatedFromDrain.getAmount(), to.fill(simulatedFromDrain, FluidAction.SIMULATE));

        // Return EMPTY if there's nothing to move.
        if (amountToMove == 0) {
            return FluidStack.EMPTY;
        }

        // Move the fluid, FOR REAL THIS TIME :3
        FluidStack drainedFrom = from.drain(request.copyWithAmount(amountToMove), FluidAction.EXECUTE);
        int filledAmount = to.fill(drainedFrom, FluidAction.EXECUTE);

        // `to` accepted less than its simulation said, so put the rest back where it came from.
        int difference = drainedFrom.getAmount() - filledAmount;
        if (difference > 0) {
            int refundedAmount = from.fill(drainedFrom.copyWithAmount(difference), FluidAction.EXECUTE);
            if (refundedAmount < difference) {
                SmartTap.LOGGER.warn("Smart Tap at {} lost {} mB of {}: target accepted less than simulated and the source refused the refund",
                        worldPosition, difference - refundedAmount, drainedFrom.getHoverName().getString());
            }
        }

        return filledAmount == 0 ? FluidStack.EMPTY : drainedFrom.copyWithAmount(filledAmount);
    }

    // ---- Saving / loading ----
    // Anything not written here is lost when the chunk unloads or the world closes.

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putBoolean("Pouring", pouring);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        pouring = tag.getBoolean("Pouring");
    }
}
