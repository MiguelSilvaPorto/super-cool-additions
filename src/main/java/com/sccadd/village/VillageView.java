package com.sccadd.village;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Everything the chamber's window shows, made on the server and sent as it is: the villager, its offers, the
 * villagers and workstations in the system, the settings. The window never builds a villager of its own to
 * ask it for offers (a client cannot), so what it shows is what the server has.
 */
public record VillageView(
        int module,
        int tier,
        boolean hasVillager,
        String profession,
        int level,
        int xp,
        int xpFrom,
        int xpTo,
        boolean topLevel,
        String name,
        ItemStack bench,
        List<MerchantOffer> offers,
        int output,
        int xpMode,
        String state,
        List<Villager> villagers,
        List<Bench> benches,
        List<Integer> keeps,
        int keepCap,
        int villagerCap,
        int stored,
        int tradesPerSecond,
        int tradeEnergy,
        int villagerEnergy) {

    /** A villager item in the system. */
    public record Villager(String code, String name, String profession, String professionId, String type, int level, int count,
                           List<MerchantOffer> offers) {
    }

    /** A workstation item in the system. */
    public record Bench(String code, ItemStack stack, long count) {
    }

    public static final VillageView EMPTY = new VillageView(0, 0, false, "", 1, 0, 0, 0, false, "", ItemStack.EMPTY, List.of(), 0, 0,
            "", List.of(), List.of(), List.of(), 1_000, 1, 0, 1, 0, 0);

    public static final StreamCodec<RegistryFriendlyByteBuf, VillageView> CODEC = StreamCodec.of(VillageView::write, VillageView::read);

    private static void writeOffers(RegistryFriendlyByteBuf buf, List<MerchantOffer> offers) {
        MerchantOffers list = new MerchantOffers();
        list.addAll(offers);
        MerchantOffers.STREAM_CODEC.encode(buf, list);
    }

    private static List<MerchantOffer> readOffers(RegistryFriendlyByteBuf buf) {
        return new ArrayList<>(MerchantOffers.STREAM_CODEC.decode(buf));
    }

    private static void write(RegistryFriendlyByteBuf buf, VillageView view) {
        buf.writeVarInt(view.module);
        buf.writeVarInt(view.tier);
        buf.writeBoolean(view.hasVillager);
        buf.writeUtf(view.profession);
        buf.writeVarInt(view.level);
        buf.writeVarInt(view.xp);
        buf.writeVarInt(view.xpFrom);
        buf.writeVarInt(view.xpTo);
        buf.writeBoolean(view.topLevel);
        buf.writeUtf(view.name);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, view.bench);
        writeOffers(buf, view.offers);
        buf.writeVarInt(view.output);
        buf.writeVarInt(view.xpMode);
        buf.writeUtf(view.state, 512);
        buf.writeVarInt(view.villagers.size());
        for (Villager villager : view.villagers) {
            buf.writeUtf(villager.code, 1024);
            buf.writeUtf(villager.name);
            buf.writeUtf(villager.profession);
            buf.writeUtf(villager.professionId);
            buf.writeUtf(villager.type);
            buf.writeVarInt(villager.level);
            buf.writeVarInt(villager.count);
            writeOffers(buf, villager.offers);
        }
        buf.writeVarInt(view.benches.size());
        for (Bench bench : view.benches) {
            buf.writeUtf(bench.code, 1024);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, bench.stack);
            buf.writeVarLong(bench.count);
        }
        buf.writeVarInt(view.keeps.size());
        for (int target : view.keeps) {
            buf.writeVarInt(target);
        }
        buf.writeVarInt(view.keepCap);
        buf.writeVarInt(view.villagerCap);
        buf.writeVarInt(view.stored);
        buf.writeVarInt(view.tradesPerSecond);
        buf.writeVarInt(view.tradeEnergy);
        buf.writeVarInt(view.villagerEnergy);
    }

    private static VillageView read(RegistryFriendlyByteBuf buf) {
        int module = buf.readVarInt();
        int tier = buf.readVarInt();
        boolean hasVillager = buf.readBoolean();
        String profession = buf.readUtf();
        int level = buf.readVarInt();
        int xp = buf.readVarInt();
        int xpFrom = buf.readVarInt();
        int xpTo = buf.readVarInt();
        boolean top = buf.readBoolean();
        String name = buf.readUtf();
        ItemStack bench = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        List<MerchantOffer> offers = readOffers(buf);
        int output = buf.readVarInt();
        int xpMode = buf.readVarInt();
        String state = buf.readUtf(512);
        int villagerCount = buf.readVarInt();
        List<Villager> villagers = new ArrayList<>(villagerCount);
        for (int i = 0; i < villagerCount; i++) {
            villagers.add(new Villager(buf.readUtf(1024), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(),
                    buf.readVarInt(), readOffers(buf)));
        }
        int benchCount = buf.readVarInt();
        List<Bench> benches = new ArrayList<>(benchCount);
        for (int i = 0; i < benchCount; i++) {
            benches.add(new Bench(buf.readUtf(1024), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong()));
        }
        int keepCount = buf.readVarInt();
        List<Integer> keeps = new ArrayList<>(keepCount);
        for (int i = 0; i < keepCount; i++) {
            keeps.add(buf.readVarInt());
        }
        return new VillageView(module, tier, hasVillager, profession, level, xp, xpFrom, xpTo, top, name, bench, offers, output, xpMode,
                state, villagers, benches, keeps, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt());
    }
}
