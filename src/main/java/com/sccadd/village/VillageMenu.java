package com.sccadd.village;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** The Auto Trader's configuration window. It has no slots: everything is buttons, and JEI/EMI still work beside it. */
public final class VillageMenu extends AbstractContainerMenu {
    private final BlockPos pos;

    public VillageMenu(int id, Inventory inventory, BlockPos pos) {
        super(VillageRegistry.MENU.get(), id);
        this.pos = pos;
    }

    public BlockPos pos() {
        return this.pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64.0
                && player.level().getBlockEntity(this.pos) instanceof VillageChamberBlockEntity;
    }
}
