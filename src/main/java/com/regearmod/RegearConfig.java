package com.regearmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All settings, saved in config/regear.json.
 * Slot numbers count from 0, left to right, top to bottom (row 2, column 5 = slot 13).
 * Times are in ticks (20 ticks = 1 second).
 */
public final class RegearConfig {
    // ---- general ----
    public static String command = "shop";          // command that opens the shop (no slash)
    public static boolean topUp = true;             // only buy what is missing from the inventory
    public static boolean hideWindow = true;        // true = shop windows are never drawn
    public static int clickDelayTicks = 3;          // pause between two clicks
    public static int waitTicks = 40;               // how long to wait for a menu / purchase message
    public static int hudPos = 1;                   // 0 = off, 1 = top center, 2 = top left, 3 = top right

    // ---- sorting and totems ----
    public static boolean sortEnabled = true;       // arrange hotbar + offhand after buying
    public static boolean fillTotems = false;       // buy totems last until every free slot is full
    public static boolean debug = false;            // print every step in chat
    public static int offhandItem = KitItem.TOTEM.ordinal();   // -1 = leave the offhand alone
    public static int[] hotbarItem = defaultHotbar();          // hotbar slots 1-9, -1 = leave

    // ---- shop menus (defaults match the DraftSMP shop) ----
    public static int categorySlot = 12;            // "Gear" icon in the main shop menu
    public static int[] kitSlot = new int[KitItem.values().length];
    public static int[] kitAmount = new int[KitItem.values().length];

    // ---- confirm menu ----
    public static int displaySlot = 13;             // the item (its stack size is the quantity)
    public static int minus64Slot = 9;              // red pane labelled 64: quantity -64
    public static int minus10Slot = 10;
    public static int minus1Slot = 11;
    public static int plus1Slot = 15;
    public static int plus10Slot = 16;
    public static int plus64Slot = 17;              // green pane labelled 64: quantity +64
    public static int cancelSlot = 21;              // red pane: go back to the category
    public static int confirmSlot = 23;             // green pane: buy

    // ---- chat messages (part of the text is enough, upper/lower case does not matter) ----
    public static String msgBought = "You bought";
    public static String msgFull = "inventory is full";
    public static String msgMoney = "enough money";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        resetKit();
    }

    static final class Data {
        String command = "shop";
        boolean topUp = true;
        boolean hideWindow = true;
        int clickDelayTicks = 3;
        int waitTicks = 40;
        int hudPos = 1;
        boolean sortEnabled = true;
        boolean fillTotems = false;
        boolean debug = false;
        int offhandItem = KitItem.TOTEM.ordinal();
        int[] hotbarItem;
        int categorySlot = 12;
        int[] kitSlot;
        int[] kitAmount;
        int displaySlot = 13;
        int minus64Slot = 9;
        int minus10Slot = 10;
        int minus1Slot = 11;
        int plus1Slot = 15;
        int plus10Slot = 16;
        int plus64Slot = 17;
        int cancelSlot = 21;
        int confirmSlot = 23;
        String msgBought = "You bought";
        String msgFull = "inventory is full";
        String msgMoney = "enough money";
    }

    private RegearConfig() {}

    static int[] defaultHotbar() {
        return new int[]{-1, KitItem.CRYSTAL.ordinal(), KitItem.OBSIDIAN.ordinal(), KitItem.ANCHOR.ordinal(),
                KitItem.GLOWSTONE.ordinal(), KitItem.PEARL.ordinal(), KitItem.GOLDEN_APPLE.ordinal(),
                KitItem.XP.ordinal(), KitItem.TOTEM.ordinal()};
    }

    private static void resetKit() {
        for (KitItem k : KitItem.values()) {
            kitSlot[k.ordinal()] = k.defaultSlot;
            kitAmount[k.ordinal()] = k.defaultAmount;
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("regear.json");
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Reads a number typed in a text box. Invalid text keeps the old value. */
    public static int parse(String text, int old, int min, int max) {
        try {
            return clamp(Integer.parseInt(text.trim()), min, max);
        } catch (NumberFormatException e) {
            return old;
        }
    }

    /** Removes a leading slash and keeps only safe characters. Empty text gives "shop". */
    public static String cleanCommand(String raw) {
        if (raw == null) return "shop";
        String s = raw.trim();
        while (s.startsWith("/")) s = s.substring(1).trim();
        s = s.replaceAll("\\s+", " ");
        if (s.length() > 40) s = s.substring(0, 40).trim();
        if (!s.matches("[A-Za-z0-9_:.\\- ]+")) return "shop";
        return s;
    }

    private static String text(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    public static void load() {
        Path p = path();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p)) {
            Data d = GSON.fromJson(r, Data.class);
            if (d == null) return;
            command = cleanCommand(d.command);
            topUp = d.topUp;
            hideWindow = d.hideWindow;
            clickDelayTicks = clamp(d.clickDelayTicks, 1, 40);
            waitTicks = clamp(d.waitTicks, 10, 200);
            hudPos = clamp(d.hudPos, 0, 3);
            sortEnabled = d.sortEnabled;
            fillTotems = d.fillTotems;
            debug = d.debug;
            int kits = KitItem.values().length;
            offhandItem = clamp(d.offhandItem, -1, kits - 1);
            hotbarItem = defaultHotbar();
            if (d.hotbarItem != null) {
                for (int i = 0; i < 9 && i < d.hotbarItem.length; i++) hotbarItem[i] = clamp(d.hotbarItem[i], -1, kits - 1);
            }
            categorySlot = clamp(d.categorySlot, 0, 53);
            resetKit();
            for (KitItem k : KitItem.values()) {
                int i = k.ordinal();
                if (d.kitSlot != null && i < d.kitSlot.length) kitSlot[i] = clamp(d.kitSlot[i], 0, 53);
                if (d.kitAmount != null && i < d.kitAmount.length) kitAmount[i] = clamp(d.kitAmount[i], 0, 9999);
            }
            displaySlot = clamp(d.displaySlot, 0, 53);
            minus64Slot = clamp(d.minus64Slot, 0, 53);
            minus10Slot = clamp(d.minus10Slot, 0, 53);
            minus1Slot = clamp(d.minus1Slot, 0, 53);
            plus1Slot = clamp(d.plus1Slot, 0, 53);
            plus10Slot = clamp(d.plus10Slot, 0, 53);
            plus64Slot = clamp(d.plus64Slot, 0, 53);
            cancelSlot = clamp(d.cancelSlot, 0, 53);
            confirmSlot = clamp(d.confirmSlot, 0, 53);
            msgBought = text(d.msgBought, "You bought");
            msgFull = text(d.msgFull, "inventory is full");
            msgMoney = text(d.msgMoney, "enough money");
        } catch (Exception ignored) {
            // keep defaults if the file is broken
        }
    }

    public static void save() {
        Data d = new Data();
        d.command = command;
        d.topUp = topUp;
        d.hideWindow = hideWindow;
        d.clickDelayTicks = clickDelayTicks;
        d.waitTicks = waitTicks;
        d.hudPos = hudPos;
        d.sortEnabled = sortEnabled;
        d.fillTotems = fillTotems;
        d.debug = debug;
        d.offhandItem = offhandItem;
        d.hotbarItem = hotbarItem.clone();
        d.categorySlot = categorySlot;
        d.kitSlot = kitSlot.clone();
        d.kitAmount = kitAmount.clone();
        d.displaySlot = displaySlot;
        d.minus64Slot = minus64Slot;
        d.minus10Slot = minus10Slot;
        d.minus1Slot = minus1Slot;
        d.plus1Slot = plus1Slot;
        d.plus10Slot = plus10Slot;
        d.plus64Slot = plus64Slot;
        d.cancelSlot = cancelSlot;
        d.confirmSlot = confirmSlot;
        d.msgBought = msgBought;
        d.msgFull = msgFull;
        d.msgMoney = msgMoney;
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(d, w);
            }
        } catch (IOException ignored) {
            // not fatal
        }
    }
}
