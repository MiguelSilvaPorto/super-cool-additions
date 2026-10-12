package com.sccadd.slots;

/**
 * What one inventory slot does with the system.
 *
 * <p>Slot numbers are the player inventory indexes: 0-8 hotbar, 9-35 inventory,
 * 36 feet, 37 legs, 38 chest, 39 head, 40 off hand.</p>
 *
 * @param item        encoded SCC item key (export: what the slot should hold), may be empty
 * @param fluid       fluid id (export: fill a container in the slot with it), may be empty
 * @param want        export: how many items the slot should hold
 * @param craft       export: order more when the system has none left
 * @param craftAmount export: how many to order
 */
public record SlotRule(int slot, Kind kind, String item, String fluid, int want, boolean craft, int craftAmount) {
    public enum Kind {
        /** System -> slot. */
        EXPORT,
        /** Slot -> system, items only. */
        IMPORT_ITEM,
        /** Slot -> system, only the fluid inside a bucket or tank (the container stays). */
        IMPORT_FLUID,
        /** Fluid first, then the container as an item. */
        IMPORT_BOTH;

        public boolean isImport() {
            return this != EXPORT;
        }
    }

    public static final int SLOT_COUNT = 41;

    public boolean hasItem() {
        return this.item != null && !this.item.isBlank();
    }

    public boolean hasFluid() {
        return this.fluid != null && !this.fluid.isBlank();
    }
}
