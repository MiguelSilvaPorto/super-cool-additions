package com.sccadd.village;

import de.scc.calculator.network.SccTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("sccadd.tip.tier", Component.translatable("scc.tier." + this.tier.id()))
                .withStyle(style -> style.withColor(this.tier.color() & 0xFFFFFF)));
        lines.add(Component.translatable("sccadd.tip.upgrade").withStyle(ChatFormatting.DARK_GRAY));
    }
}
