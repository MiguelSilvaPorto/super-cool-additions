package com.sccadd.village;

import com.mojang.brigadier.arguments.StringArgumentType;
import de.scc.calculator.calc.StackKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /sccadd villagetest <workstation> <item>}: builds a simulator, moves a villager in, lets it
 * trade for free until it finds the item (or runs out of levels) and reports what happened. It is a
 * diagnostic for operators, not a feature.
 */
final class VillageSelfTest {
    private VillageSelfTest() {
    }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sccadd").requires(source -> source.hasPermission(2))
                .then(Commands.literal("villagetest")
                        .then(Commands.argument("workstation", StringArgumentType.word())
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .executes(context -> run(context.getSource(),
                                                StringArgumentType.getString(context, "workstation"),
                                                StringArgumentType.getString(context, "item")))))));
    }

    private static int run(CommandSourceStack source, String workstationId, String itemId) {
        ServerLevel level = source.getLevel();
        Block workstation = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(workstationId.contains(":") ? workstationId : "minecraft:" + workstationId));
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId.contains(":") ? itemId : "minecraft:" + itemId));
        VillagerProfession profession = VillageSimulatorBlockEntity.professionFor(workstation);
        if (profession == null) {
            source.sendFailure(Component.literal(workstationId + " is not a villager workstation"));
            return 0;
        }
        BlockPos pos = BlockPos.containing(source.getPosition()).above(3);
        level.setBlockAndUpdate(pos, VillageRegistry.AUTO_TRADER.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof VillageSimulatorBlockEntity be)) {
            source.sendFailure(Component.literal("no block entity at " + pos));
            return 0;
        }
        ItemStack villager = VillageSimulatorBlockEntity.newVillagerItem(level, VillagerProfession.NONE);
        be.selfTest(villager, workstation, StackKey.of(item));
        int steps = 0;
        while (steps < 300) {
            be.step();
            steps++;
            boolean found = be.waiting().stream().anyMatch(stack -> stack.is(item));
            if (found || be.stateText().contains("not offered") || be.stateText().contains("no trades")
                    || be.stateText().contains("gives no profession")) {
                break;
            }
        }
        StringBuilder out = new StringBuilder("villagetest " + profession.name() + ": " + steps + " step(s), "
                + be.trades() + " trade(s), state '" + be.stateText() + "', got:");
        be.waiting().forEach(stack -> out.append(' ').append(stack.getCount()).append('x').append(stack.getHoverName().getString()));
        level.removeBlock(pos, false);
        String text = out.toString();
        de.scc.calculator.SccLog.info("sccadd", text);
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
