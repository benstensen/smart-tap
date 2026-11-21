package dev.fishna.smarttap.registry;

import dev.fishna.smarttap.SmartTap;
import dev.fishna.smarttap.block.SmartTapBlock;
import dev.fishna.smarttap.block.SmartTapBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Everything Smart Tap adds to the game's registries lives here.
 * <p>
 * Docs: https://docs.neoforged.net/docs/1.21.1/concepts/registries
 */
public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SmartTap.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SmartTap.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SmartTap.MODID);

    // The block itself: "smart_tap:smart_tap"
    public static final DeferredBlock<SmartTapBlock> SMART_TAP = BLOCKS.register("smart_tap",
            () -> new SmartTapBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion())); // not a full cube, so don't cull neighbouring faces

    // The item you hold in your hand / inventory that places the block
    public static final DeferredItem<BlockItem> SMART_TAP_ITEM = ITEMS.registerSimpleBlockItem("smart_tap", SMART_TAP);

    // The block entity type: links the block to SmartTapBlockEntity, which holds per-tap data (tank, settings)
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmartTapBlockEntity>> SMART_TAP_BE =
            BLOCK_ENTITY_TYPES.register("smart_tap",
                    () -> BlockEntityType.Builder.of(SmartTapBlockEntity::new, SMART_TAP.get()).build(null));

    private ModRegistry() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
