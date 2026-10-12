package com.sccadd.village.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sccadd.village.VillageChamberBlockEntity;
import com.sccadd.village.VillageChamberItem;
import de.maxhenkel.easyvillagers.corelib.client.RendererProviders;
import de.maxhenkel.easyvillagers.datacomponents.VillagerData;
import de.maxhenkel.easyvillagers.entity.EasyVillagerEntity;
import de.maxhenkel.easyvillagers.items.ModItems;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * The chamber as an item: the glass box with a villager and its workstation inside, the way Easy Villagers'
 * own items show theirs. Works in the inventory, in JEI/EMI and in the hand, and takes the tier's colour
 * from the block (the floor and the rim are tinted), so every tier looks different at a glance.
 */
public final class VillageChamberItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final VillageChamberRenderer chamber;
    private final Map<Item, VillageChamberBlockEntity> previews = new HashMap<>();
    private Level previewLevel;

    public VillageChamberItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.chamber = new VillageChamberRenderer(RendererProviders.createBlockEntityRendererContext());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof VillageChamberItem item)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getBlockRenderer().renderSingleBlock(item.getBlock().defaultBlockState(), pose, buffers, light, overlay);
        if (minecraft.level != null) {
            this.chamber.render(this.preview(item, minecraft.level), 0.0F, pose, buffers, light, overlay);
        }
    }

    /** A chamber with a farmer and a composter inside, made once per item: what the placed block shows. */
    private VillageChamberBlockEntity preview(VillageChamberItem item, Level level) {
        if (this.previewLevel != level) {
            this.previews.clear();
            this.previewLevel = level;
        }
        return this.previews.computeIfAbsent(item, ignored -> {
            VillageChamberBlockEntity be = new VillageChamberBlockEntity(BlockPos.ZERO, item.getBlock().defaultBlockState());
            be.setLevel(level);
            EasyVillagerEntity villager = new EasyVillagerEntity(EntityType.VILLAGER, level);
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(3));
            ItemStack villagerItem = new ItemStack(ModItems.VILLAGER.get());
            VillagerData.applyToItem(villagerItem, villager);
            be.selfTest(VillageChamberBlockEntity.Module.TRADER, villagerItem, Blocks.COMPOSTER);
            return be;
        });
    }
}
