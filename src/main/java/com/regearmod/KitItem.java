package com.regearmod;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** The items the regear can buy, with their default shop slot (Gear shop) and default amount. */
public enum KitItem {
    TOTEM("Totem", Items.TOTEM_OF_UNDYING, 13, 4),
    CRYSTAL("Crystal", Items.END_CRYSTAL, 10, 64),
    PEARL("Pearl", Items.ENDER_PEARL, 14, 16),
    OBSIDIAN("Obsidian", Items.OBSIDIAN, 9, 64),
    ANCHOR("Anchor", Items.RESPAWN_ANCHOR, 11, 0),
    XP("XP bottle", Items.EXPERIENCE_BOTTLE, 16, 64),
    GLOWSTONE("Glowstone", Items.GLOWSTONE, 12, 0);

    public final String label;
    public final Item item;
    public final int defaultSlot;
    public final int defaultAmount;

    KitItem(String label, Item item, int defaultSlot, int defaultAmount) {
        this.label = label;
        this.item = item;
        this.defaultSlot = defaultSlot;
        this.defaultAmount = defaultAmount;
    }

    /** How many of this item fit in one stack (the shop's quantity limit). */
    public int maxStack() {
        return Math.max(1, item.getDefaultStack().getMaxCount());
    }
}
