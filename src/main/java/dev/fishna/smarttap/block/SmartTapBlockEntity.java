package dev.fishna.smarttap.block;

import org.jetbrains.annotations.Nullable;

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
