package com.sccadd.mixin;

import com.sccadd.village.VillageSimulatorBlockEntity;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.NetworkView;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The villager inside a Village Trader is paid from the SCC network: picking an offer fills the
 * payment slots from the network instead of from your inventory, and whatever is still in the slots
 * when you close the window goes back to the network.
 */
@Mixin(value = MerchantMenu.class, remap = false)
public abstract class VillageMerchantMenuMixin {
    @Shadow
    @Final
    private Merchant trader;

    @Shadow
    @Final
    private MerchantContainer tradeContainer;

    @Inject(method = "tryMoveItems", at = @At("TAIL"), require = 0)
    private void sccadd$payFromNetwork(int index, CallbackInfo ci) {
        VillageSimulatorBlockEntity owner = VillageSimulatorBlockEntity.ownerOf(this.trader);
        if (owner == null || owner.isAuto()) {
            return;
        }
        MerchantOffers offers = this.trader.getOffers();
        NetworkView view = owner.network();
        if (index < 0 || index >= offers.size() || view == null || !view.online()) {
            return;
        }
        MerchantOffer offer = offers.get(index);
        this.fill(0, offer.getCostA(), view);
        this.fill(1, offer.getCostB(), view);
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void sccadd$giveBack(Player player, CallbackInfo ci) {
        VillageSimulatorBlockEntity owner = VillageSimulatorBlockEntity.ownerOf(this.trader);
        if (owner == null || player.level().isClientSide) {
            return;
        }
        NetworkView view = owner.network();
        for (int slot = 0; slot < 2; slot++) {
            ItemStack left = this.tradeContainer.removeItemNoUpdate(slot);
            if (left.isEmpty()) {
                continue;
            }
            if (view != null && view.online()) {
                left = view.insert(left, false);
            }
            if (!left.isEmpty()) {
                player.getInventory().placeItemBackInInventory(left);
            }
        }
    }

    private void fill(int slot, ItemStack cost, NetworkView view) {
        if (cost.isEmpty()) {
            return;
        }
        ItemStack current = this.tradeContainer.getItem(slot);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, cost)) {
            return;
        }
        int have = current.isEmpty() ? 0 : current.getCount();
        int need = cost.getCount() - have;
        if (need <= 0) {
            return;
        }
        ItemStack got = view.extract(StackKey.of(cost), need, false);
        if (!got.isEmpty()) {
            this.tradeContainer.setItem(slot, cost.copyWithCount(have + got.getCount()));
        }
    }
}
