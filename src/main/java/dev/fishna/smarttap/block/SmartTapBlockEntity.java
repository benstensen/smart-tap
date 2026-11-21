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

    public SmartTapBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SMART_TAP_BE.get(), pos, state);
    }

    public FluidTank getTank() {
        return tank;
    }

    /** Called every tick (20/s) on the server only. See SmartTapBlock#getTicker. */
    public void serverTick() {
        // TODO: pouring logic goes here
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

    public void pour(Player player) {
        pour(player, 0);
    }

    public void pour(Player player, int tankIdx) {
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

        // Drain fluid from source
        FluidStack toPour = source.drain(sourceFS.copyWithAmount(targetCap), FluidAction.EXECUTE);
        SmartTap.LOGGER.debug("Smart Tap at {}: target capacity {} mB, drained {} mB from source",
                worldPosition, targetCap, toPour.getAmount());

        // If we don't have enough fluid from source to fill target, refund remainder to source.
        if (toPour.getAmount() < targetCap) {
            SmartTap.LOGGER.debug("Smart Tap at {}: source gave less than expected ({} of {} mB), refunding",
                    worldPosition, toPour.getAmount(), targetCap);

            // Refund the pour
            int difference = toPour.getAmount() - source.fill(toPour, FluidAction.EXECUTE);

            // Whatever the source wouldn't take back stays in the tap as leftover.
            if (difference > 0) {
                int kept = tank.fill(toPour.copyWithAmount(difference), FluidAction.EXECUTE);
                if (kept < difference) {
                    SmartTap.LOGGER.warn("Smart Tap at {} lost {} mB of {} while refunding a short drain",
                            worldPosition, difference - kept, toPour.getHoverName().getString());
                }
            }

            return;
        }

        // Pour from source to tank (space was checked above, so this takes all of it)
        tank.fill(toPour, FluidAction.EXECUTE);

        // Pour from tank to target. The target goes first, then the tank gives up exactly what it accepted,
        // so nothing is lost if the target takes less than expected. Pass copies: getFluid() is the tank's own stack.
        int pourAmount = target.fill(tank.getFluid().copy(), FluidAction.EXECUTE);
        FluidStack poured = tank.drain(pourAmount, FluidAction.EXECUTE);
        SmartTap.LOGGER.debug("Smart Tap at {}: poured {} mB of {}",
                worldPosition, poured.getAmount(), poured.getHoverName().getString());
    }

    /** Return the amount of the given fluid that a target IFluidHandler would accept.
     *  @param target The IFluidHandler for the target of the pour
     *  @param fluidStack The FluidStack for the fluid that would be poured
     * */
    private int getTargetCapacity(IFluidHandler target, FluidStack fluidStack) {
        FluidStack tMaxFluidStack = fluidStack.copyWithAmount(fluidStack.getAmount() + 1);
        return target.fill(tMaxFluidStack, FluidAction.SIMULATE);
    }

    // ---- Saving / loading ----
    // Anything not written here is lost when the chunk unloads or the world closes.

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
    }
}
