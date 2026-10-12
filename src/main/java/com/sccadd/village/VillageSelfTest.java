package com.sccadd.village;

import com.mojang.brigadier.arguments.StringArgumentType;
import de.scc.calculator.network.SccRegistry;
import de.scc.calculator.network.SccTier;
import de.scc.calculator.network.TierUpgrades;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /sccadd villagetest <workstation> [auto]}: builds a chamber, installs the Trader (or Auto Trader) with a
 * villager, trades for free through the window's code path until the villager has levelled up as far as it will
 * go, and reports what happened. A diagnostic for operators, not a feature.
 */
final class VillageSelfTest {
    private VillageSelfTest() {
    }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sccadd").requires(source -> source.hasPermission(2))
                .then(Commands.literal("villagetest")
                        .then(Commands.argument("workstation", StringArgumentType.word())
                                .executes(context -> run(context.getSource(), StringArgumentType.getString(context, "workstation"), false))
                                .then(Commands.literal("auto")
                                        .executes(context -> run(context.getSource(), StringArgumentType.getString(context, "workstation"), true)))))
                .then(Commands.literal("villagetier")
                        .executes(context -> tierTest(context.getSource()))));
    }

    /** {@code /sccadd villagetier}: walks a chamber up every tier with SCC's own upgrade path and reports. */
    static int tierTest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition()).above(4);
        StringBuilder out = new StringBuilder("villagetier:");
        // SCC's registry has to answer for our blocks, at any tier (a yellow chamber is not "basic").
        level.setBlockAndUpdate(pos, VillageRegistry.CHAMBERS.get(SccTier.YELLOW_STAR).get().defaultBlockState());
        out.append(" tierOf(yellow) ").append(SccRegistry.sharedTierOf(level.getBlockState(pos).getBlock()).id());
        out.append(", family(red) ").append(nameOf(SccRegistry.sameFamily(level.getBlockState(pos).getBlock(), SccTier.RED_DWARF)));
        // a basic chamber with a module in it, then up one tier at a time, the way the upgrade item does it
        level.setBlockAndUpdate(pos, VillageRegistry.CHAMBERS.get(SccTier.BASIC).get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof VillageChamberBlockEntity be) {
            be.selfTest(VillageChamberBlockEntity.Module.TRADER, VillageChamberBlockEntity.newVillagerItem(level, VillagerProfession.NONE),
                    BuiltInRegistries.BLOCK.get(ResourceLocation.parse("minecraft:composter")));
        }
        for (SccTier target : SccTier.values()) {
            if (target == SccTier.BASIC) {
                continue; // already basic: nothing to swap
            }
            Block next = SccRegistry.sameFamily(level.getBlockState(pos).getBlock(), target);
            if (next == null) {
                out.append(" | no block for ").append(target.id());
                break;
            }
            boolean swapped = TierUpgrades.swap(level, pos, next);
            SccTier now = SccRegistry.sharedTierOf(level.getBlockState(pos).getBlock());
            boolean kept = level.getBlockEntity(pos) instanceof VillageChamberBlockEntity chamber
                    && chamber.module() != VillageChamberBlockEntity.Module.NONE && chamber.villager() != null;
            out.append(" | ").append(target.id()).append(swapped ? " ok" : " FAILED").append(", tier ").append(now.id())
                    .append(", module kept ").append(kept);
        }
        // keeps and the stock: a basic chamber with a villager, a keep on its first trade, then store/swap
        level.setBlockAndUpdate(pos, VillageRegistry.CHAMBERS.get(SccTier.BASIC).get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof VillageChamberBlockEntity be) {
            be.selfTest(VillageChamberBlockEntity.Module.TRADER, VillageChamberBlockEntity.newVillagerItem(level, VillagerProfession.NONE),
                    BuiltInRegistries.BLOCK.get(ResourceLocation.parse("minecraft:composter")));
            out.append(" | keep: offers ").append(be.offers().size()).append(", cap ").append(be.keepCap());
            be.setKeep(0, 5, null);
            for (int i = 0; i < 60; i++) {
                be.serverTick();
            }
            out.append(", trades ").append(be.trades()).append(", waiting ").append(be.waiting().size());
            be.storeCurrent(null);
            out.append(", stored ").append(be.stored()).append("/").append(be.villagerCap());
            be.useVillager("v0", null);
            out.append(", module has villager ").append(be.villager() != null).append(", stored ").append(be.stored());
        }
        level.removeBlock(pos, false);
        String text = out.toString();
        System.out.println("VILLAGETEST " + text);
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static String nameOf(@org.jetbrains.annotations.Nullable Block block) {
        return block == null ? "none" : String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
    }

    static int run(CommandSourceStack source, String workstationId, boolean auto) {
        ServerLevel level = source.getLevel();
        Block workstation = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(workstationId.contains(":") ? workstationId : "minecraft:" + workstationId));
        if (VillageChamberBlockEntity.professionFor(workstation) == null) {
            source.sendFailure(Component.literal(workstationId + " is not a villager workstation"));
            return 0;
        }
        BlockPos pos = BlockPos.containing(source.getPosition()).above(3);
        level.setBlockAndUpdate(pos, VillageRegistry.CHAMBER.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof VillageChamberBlockEntity be)) {
            source.sendFailure(Component.literal("no block entity at " + pos));
            return 0;
        }
        be.selfTest(auto ? VillageChamberBlockEntity.Module.AUTO : VillageChamberBlockEntity.Module.TRADER,
                VillageChamberBlockEntity.newVillagerItem(level, VillagerProfession.NONE), workstation);
        StringBuilder out = new StringBuilder("villagetest " + (auto ? "auto " : "trader ") + workstationId + ":");
        for (int round = 0; round < 12; round++) {
            be.serverTick();
            MerchantOffers offers = be.offers();
            if (offers.isEmpty()) {
                out.append(" no offers");
                break;
            }
            for (int index = 0; index < offers.size(); index++) {
                be.trade(index, 4, null);
            }
            be.serverTick();
        }
        // experience: orbs next to the chamber (no experience fluid in this test)
        int orbs = level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, new net.minecraft.world.phys.AABB(pos).inflate(3.0)).size();
        out.append(" orbs ").append(orbs);
        // the window's view, through the codec the server uses to send it
        be.rename("Bob");
        VillageView before = be.buildView();
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        VillageView.CODEC.encode(buf, before);
        VillageView after = VillageView.CODEC.decode(buf);
        out.append(", view ").append(before.offers().size()).append("->").append(after.offers().size()).append(" offers, name '")
                .append(after.name()).append("', profession ").append(after.profession()).append(" lvl ").append(after.level());
        var villager = be.villager();
        out.append(" level ").append(villager == null ? -1 : villager.getVillagerData().getLevel())
                .append(", xp ").append(villager == null ? -1 : villager.getVillagerXp())
                .append(", offers ").append(be.offers().size())
                .append(", trades ").append(be.trades())
                .append(", waiting ").append(be.waiting().size()).append(" stack(s)");
        // the module as an item again, and back in
        var stack = be.moduleStack();
        out.append(", module item ").append(stack.isEmpty() ? "none" : stack.getHoverName().getString());
        level.removeBlock(pos, false);
        String text = out.toString();
        System.out.println("VILLAGETEST " + text);
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** Dev hook: runs the self test at server start for a few workstations (set -Dsccadd.villagetest=true or SCCADD_VILLAGETEST=true). */
    static void onStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        boolean haltAfter = "true".equalsIgnoreCase(System.getenv("SCCADD_VILLAGETEST"));
        if (!Boolean.getBoolean("sccadd.villagetest") && !haltAfter) {
            return;
        }
        var source = event.getServer().createCommandSourceStack().withPermission(4);
        tierTest(source);
        {
            var level = event.getServer().overworld();
            var at = new net.minecraft.core.BlockPos(40, 120, 40);
            level.setBlockAndUpdate(at, com.sccadd.iface.CompactInterface.BLOCK.get().defaultBlockState());
            var be = level.getBlockEntity(at);
            System.out.println("VILLAGETEST compact interface: block entity " + (be == null ? "NONE" : be.getClass().getSimpleName())
                    + ", shape " + com.sccadd.iface.CompactInterface.BLOCK.get().defaultBlockState().getShape(level, at).bounds());
            level.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        for (String bench : new String[] {"composter", "cartography_table", "lectern", "blast_furnace"}) {
            run(source, bench, false);
        }
        run(source, "barrel", true);
        if (haltAfter) {
            // the env var runs are one-shot: report, then get out of the way
            event.getServer().halt(false);
        }
    }
}
