package com.sccadd.village;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;

/** Opens the chamber's window. */
final class VillageMenus {
    private VillageMenus() {
    }

    static void openWindow(Player player, VillageChamberBlockEntity be) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new VillageMenu(id, inventory, be.getBlockPos()),
                    Component.literal("Village Chamber")), buf -> buf.writeBlockPos(be.getBlockPos()));
        }
    }
}
