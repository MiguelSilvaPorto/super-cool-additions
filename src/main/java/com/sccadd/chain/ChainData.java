package com.sccadd.chain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * A production chain drawn by the player on one requester row: boxes (steps) joined by arrows.
 * Each box keeps one item stocked using one chosen recipe, so a loop of recipes that feed each
 * other never has to be solved by the planner - the requester just runs the boxes in the order
 * the arrows give, round and round.
 */
public final class ChainData {
    public static final int MAX_NODES = 64;

    public static final class Node {
        public int id;
        public int x;
        public int y;
        /** {@code StackKey.encode()} of the item this box keeps stocked. */
        public String item = "";
        /** Stock to keep. */
        public long target = 64;
        /** The most crafts one run may do; 0 = no limit besides stock. */
        public int batch = 0;
        /** {@code CraftStep.encode()} of the chosen recipe for one craft. */
        public String step = "";
        /** Per ingredient of the recipe: the items it may be made of (tags), {@code StackKey.encode()} joined by '|'. */
        public List<String> alts = new ArrayList<>();

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", this.id);
            tag.putInt("x", this.x);
            tag.putInt("y", this.y);
            tag.putString("item", this.item);
            tag.putLong("target", this.target);
            tag.putInt("batch", this.batch);
            tag.putString("step", this.step);
            ListTag alt = new ListTag();
            for (String options : this.alts) {
                alt.add(net.minecraft.nbt.StringTag.valueOf(options));
            }
            tag.put("alts", alt);
            return tag;
        }

        public static Node load(CompoundTag tag) {
            Node node = new Node();
            node.id = tag.getInt("id");
            node.x = tag.getInt("x");
            node.y = tag.getInt("y");
            node.item = tag.getString("item");
            node.target = Math.max(0L, tag.getLong("target"));
            node.batch = Math.max(0, tag.getInt("batch"));
            node.step = tag.getString("step");
            ListTag alt = tag.getList("alts", Tag.TAG_STRING);
            for (int i = 0; i < Math.min(32, alt.size()); i++) {
                node.alts.add(alt.getString(i));
            }
            return node;
        }
    }

    public final List<Node> nodes = new ArrayList<>();
    /** Pairs {from id, to id}. */
    public final List<int[]> links = new ArrayList<>();

    public boolean isEmpty() {
        return this.nodes.isEmpty();
    }

    public Node byId(int id) {
        for (Node node : this.nodes) {
            if (node.id == id) {
                return node;
            }
        }
        return null;
    }

    /**
     * The order the boxes run in: start at the first box, follow the arrows, and then take any box
     * that no arrow reached in the order it was made. A loop simply ends where it comes back.
     */
    public List<Node> flow() {
        List<Node> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Node start : this.nodes) {
            Node at = start;
            while (at != null && seen.add(at.id)) {
                out.add(at);
                Node next = null;
                for (int[] link : this.links) {
                    if (link[0] == at.id) {
                        next = this.byId(link[1]);
                        break;
                    }
                }
                at = next;
            }
        }
        return out;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Node node : this.nodes) {
            list.add(node.save());
        }
        tag.put("nodes", list);
        List<Integer> flat = new ArrayList<>();
        for (int[] link : this.links) {
            flat.add(link[0]);
            flat.add(link[1]);
        }
        tag.putIntArray("links", flat.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    public static ChainData load(CompoundTag tag) {
        ChainData data = new ChainData();
        ListTag list = tag.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_NODES, list.size()); i++) {
            data.nodes.add(Node.load(list.getCompound(i)));
        }
        int[] flat = tag.getIntArray("links");
        for (int i = 0; i + 1 < flat.length; i += 2) {
            if (data.byId(flat[i]) != null && data.byId(flat[i + 1]) != null) {
                data.links.add(new int[] {flat[i], flat[i + 1]});
            }
        }
        return data;
    }
}
