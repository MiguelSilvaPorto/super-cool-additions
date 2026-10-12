package com.sccadd.village.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sccadd.village.VillageSimulatorBlockEntity;
import de.maxhenkel.easyvillagers.entity.EasyVillagerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

/** Draws the villager standing in the glass box. */
public final class VillageBlockRenderer implements BlockEntityRenderer<VillageSimulatorBlockEntity> {
    @Override
    public void render(VillageSimulatorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        EasyVillagerEntity villager = be.renderEntity();
        if (villager == null || be.getLevel() == null) {
            return;
        }
        villager.tickCount = (int) (be.getLevel().getGameTime() % 100000L);
        pose.pushPose();
        pose.translate(0.5, 2.0 / 16.0, 0.5);
        pose.scale(0.5F, 0.5F, 0.5F);
        Minecraft.getInstance().getEntityRenderDispatcher().render(villager, 0.0, 0.0, 0.0, 0.0F, partialTick, pose, buffers, light);
        pose.popPose();
    }
}
