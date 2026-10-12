package com.sccadd.village.client;

import com.sccadd.village.VillageView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client side of {@code VillageViewPayload}: hands the view to the open window. */
public final class VillageClientHooks {
    private VillageClientHooks() {
    }

    public static void accept(BlockPos pos, VillageView view) {
        if (Minecraft.getInstance().screen instanceof VillageScreen screen && screen.chamber().equals(pos)) {
            screen.setView(view);
        }
    }
}
