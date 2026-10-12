package com.sccadd.village;

import com.mojang.brigadier.arguments.StringArgumentType;
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
                                        .executes(context -> run(context.getSource(), StringArgumentType.getString(context, "workstation"), true))))));
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
        // saved villagers: save the current one, swap it back in through the list
        be.saveSetup();
        int saved = be.setups().size();
        be.useSetup(0);
        out.append(", setups ").append(saved).append("->").append(be.setups().size());
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

    /** Dev hook: runs the self test at server start for a few workstations (set -Dsccadd.villagetest=true). */
    static void onStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if (!Boolean.getBoolean("sccadd.villagetest")) {
            return;
        }
        var source = event.getServer().createCommandSourceStack().withPermission(4);
        for (String bench : new String[] {"composter", "cartography_table", "lectern", "blast_furnace"}) {
            run(source, bench, false);
        }
        run(source, "barrel", true);
    }
}
