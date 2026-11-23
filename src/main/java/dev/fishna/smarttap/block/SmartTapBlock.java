package dev.fishna.smarttap.block;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.fishna.smarttap.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Smart Tap block. Like a Tinkers' faucet, it attaches to the side of a drain
 * and pours into whatever is directly below it.
 * <p>
 * FACING points <em>away</em> from the drain (the direction the spout sticks out),
 * so the drain is at {@code pos.relative(FACING.getOpposite())}.
 * <p>
 * A Block is shared by every tap in the world. Per-tap data (fluid, settings) lives in
 * {@link SmartTapBlockEntity}.
 * <p>
 * Docs: https://docs.neoforged.net/docs/1.21.1/blocks/
 * and   https://docs.neoforged.net/docs/1.21.1/blocks/states
 */
public class SmartTapBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SmartTapBlock> CODEC = simpleCodec(SmartTapBlock::new);

    // Hitbox for a north-facing tap, in pixels (0-16). Keep in sync with models/block/smart_tap.json.
    private static final double[][] NORTH_BOXES = {
            {4, 6, 14, 12, 14, 16}, // back plate against the drain
            {6, 10, 6, 10, 13, 14}, // arm
            {6, 6, 6, 10, 10, 9},   // spout
    };
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    static {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            SHAPES.put(dir, rotatedShape(dir));
        }
    }

    public SmartTapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Clicking the side of a drain: the clicked face points out of the drain, which is what we want.
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal()
                ? clicked
                : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    // ---- Interaction ----
    // Called on right-click with an empty hand. This still fires while sneaking as long as both
    // hands are empty (sneaking with an item in hand skips the block and uses the item instead).
    // For right-clicking with an item (e.g. the "texture mask" idea), override useItemOn instead.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS; // swing the arm; the server does the real work
        }
        if (level.getBlockEntity(pos) instanceof SmartTapBlockEntity tap) {
            if (player.isShiftKeyDown()) {
                // TODO: open the settings screen (see "Menus" + "Screens" docs)
                player.displayClientMessage(Component.literal("Settings: not implemented yet"), true);
            } else {
                tap.pour();
            }
        }
        return InteractionResult.CONSUME;
    }

    // ---- Block entity ----
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmartTapBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Only tick on the server; pouring logic should never run on the client.
        if (level.isClientSide || type != ModRegistry.SMART_TAP_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((SmartTapBlockEntity) be).serverTick();
    }

    // Rotates NORTH_BOXES clockwise (seen from above) to match the blockstate's "y" rotation.
    private static VoxelShape rotatedShape(Direction facing) {
        int steps = (facing.get2DDataValue() + 2) % 4; // north=0, east=1, south=2, west=3
        VoxelShape shape = Shapes.empty();
        for (double[] b : NORTH_BOXES) {
            double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
            for (int i = 0; i < steps; i++) {
                double nx1 = 16 - z2, nx2 = 16 - z1;
                z1 = x1;
                z2 = x2;
                x1 = nx1;
                x2 = nx2;
            }
            shape = Shapes.or(shape, Block.box(x1, b[1], z1, x2, b[4], z2));
        }
        return shape;
    }
}
