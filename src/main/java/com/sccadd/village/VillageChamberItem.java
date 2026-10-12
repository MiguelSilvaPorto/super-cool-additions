package com.sccadd.village;

import de.scc.calculator.network.SccTier;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** The chamber as an item: same as a block item, plus the tier line the other SCC machines show. */
public class VillageChamberItem extends BlockItem {
    private final SccTier tier;

    public VillageChamberItem(Block block, Properties properties, SccTier tier) {
        super(block, properties);
        this.tier = tier;
    }

    public SccTier tier() {
        return this.tier;
    }

    /** Client only: the item draws the box with the villager inside, in 3D, instead of the flat model. */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        super.initializeClient(consumer);
        consumer.accept(new IClientItemExtensions() {
            private com.sccadd.village.client.VillageChamberItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new com.sccadd.village.client.VillageChamberItemRenderer();
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("sccadd.tip.tier", Component.translatable("scc.tier." + this.tier.id()))
                .withStyle(style -> style.withColor(this.tier.color() & 0xFFFFFF)));
        lines.add(Component.translatable("sccadd.tip.upgrade").withStyle(ChatFormatting.DARK_GRAY));
    }
}
