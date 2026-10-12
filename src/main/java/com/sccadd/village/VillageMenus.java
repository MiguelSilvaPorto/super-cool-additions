package com.sccadd.village;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;

/** Opens the Auto Trader's window. */
final class VillageMenus {
    private VillageMenus() {
    }

    static void openConfig(Player player, VillageSimulatorBlockEntity be) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new VillageMenu(id, inventory, be.getBlockPos()),
                    Component.literal("Auto Trader")), buf -> buf.writeBlockPos(be.getBlockPos()));
        }
    }
}
