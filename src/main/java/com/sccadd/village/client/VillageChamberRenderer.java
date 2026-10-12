package com.sccadd.village.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sccadd.village.VillageChamberBlockEntity;
import de.maxhenkel.easyvillagers.blocks.tileentity.AutoTraderTileentity;
import de.maxhenkel.easyvillagers.blocks.tileentity.TraderTileentity;
import de.maxhenkel.easyvillagers.blocks.tileentity.render.AutoTraderRenderer;
import de.maxhenkel.easyvillagers.blocks.tileentity.render.TraderRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Draws what is inside the glass box with Easy Villagers' own renderers: the villager and its workstation, in 3D. */
public final class VillageChamberRenderer implements BlockEntityRenderer<VillageChamberBlockEntity> {
    private final TraderRenderer trader;
    private final AutoTraderRenderer auto;

    public VillageChamberRenderer(BlockEntityRendererProvider.Context context) {
        this.trader = new TraderRenderer(context);
        this.auto = new AutoTraderRenderer(context);
    }

    @Override
    public void render(VillageChamberBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var module = be.inner();
        if (module instanceof AutoTraderTileentity autoModule) {
            this.auto.render(autoModule, partialTick, pose, buffers, light, overlay);
        } else if (module instanceof TraderTileentity traderModule) {
            this.trader.render(traderModule, partialTick, pose, buffers, light, overlay);
        }
    }
}
