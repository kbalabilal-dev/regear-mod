package com.regearmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Buys the kit from the shop.
 *
 * Steps: open the shop with the command, click the Gear category, then for each item:
 * click it, set the quantity with the fewest clicks, click confirm (the page stays open, so
 * it can be clicked again for the next full stack), then go back with the cancel button.
 *
 * Menus are recognised by what is inside them (the item must be where we click), so a
 * wrong slot number stops the regear instead of buying the wrong thing.
 * With "hide window" on, the mixin stops the shop windows from being drawn; this class
 * reads and clicks the open window through the player's screen handler.
 */
public final class RegearRunner {
    private enum State { IDLE, OPENING, GEAR_WAIT, GEAR_PICK, BUY_WAIT, ADJUST, ADJUST_WAIT, CONFIRM_WAIT, BACK_WAIT }

    private static final class Job {
        final KitItem kit;
        final int need;
        final ArrayDeque<Integer> purchases = new ArrayDeque<>();

        Job(KitItem kit, int need) {
            this.kit = kit;
            this.need = need;
        }
    }

    private static final List<Job> jobs = new ArrayList<>();
    private static final Set<Integer> blocked = new HashSet<>();
    private static State state = State.IDLE;
    private static int jobIdx, baseSync = -1, timer, cooldown;
    private static int totalPurchases, donePurchases, navRetries, attempts;
    private static int lastCount, lastMoveSlot, adjustClicks;
    private static boolean paused, gotBought, gotFull, gotMoney;

    private RegearRunner() {}

    // ---------- public state ----------

    public static boolean isRunning() {
        return state != State.IDLE;
    }

    public static boolean isPaused() {
        return state != State.IDLE && paused;
    }

    /** Used by the mixin: true while shop windows should not be drawn. */
    public static boolean wantsHiddenWindow() {
        return RegearConfig.hideWindow && state != State.IDLE;
    }

    public static void togglePause(MinecraftClient client) {
        if (state == State.IDLE) return;
        paused = !paused;
        status(client, paused ? "Regear: paused" : "Regear: resumed");
    }

    public static float progress() {
        return totalPurchases > 0 ? Math.min(1f, donePurchases / (float) totalPurchases) : 0f;
    }

    public static List<String> hudLines() {
        List<String> l = new ArrayList<>();
        if (state == State.IDLE) return l;
        l.add("Auto Regear" + (paused ? "  [PAUSED]" : ""));
        if (jobIdx < jobs.size()) {
            Job j = jobs.get(jobIdx);
            l.add("Buying: " + j.kit.label + " x" + j.need);
        } else {
            l.add("Finishing...");
        }
        l.add("Purchases " + donePurchases + "/" + totalPurchases);
        return l;
    }

    // ---------- start / stop ----------

    public static void start(MinecraftClient client) {
        if (state != State.IDLE) return;
        if (client.player == null || client.interactionManager == null || client.getNetworkHandler() == null) return;

        jobs.clear();
        totalPurchases = 0;
        for (KitItem k : KitItem.values()) {
            int target = RegearConfig.kitAmount[k.ordinal()];
            if (target <= 0) continue;
            int need = RegearConfig.topUp ? Math.max(0, target - countItem(client, k.item)) : target;
            if (need <= 0) continue;
            Job j = new Job(k, need);
            int max = k.maxStack();
            for (int i = 0; i < need / max; i++) j.purchases.add(max);
            if (need % max > 0) j.purchases.add(need % max);
            totalPurchases += j.purchases.size();
            jobs.add(j);
        }
        if (jobs.isEmpty()) {
            status(client, "Regear: nothing to buy (kit already complete)");
            return;
        }
        jobIdx = 0;
        donePurchases = 0;
        navRetries = 0;
        paused = false;
        beginNavigation(client);
    }

    public static void cancel(MinecraftClient client) {
        if (state == State.IDLE) return;
        state = State.IDLE;
        paused = false;
        closeWindow(client);
        status(client, "Regear: cancelled");
    }

    private static void fail(MinecraftClient client, String reason) {
        state = State.IDLE;
        paused = false;
        closeWindow(client);
        if (client.player != null) {
            client.player.sendMessage(Text.literal("Regear stopped: " + reason), false);
        }
    }

    private static void finish(MinecraftClient client) {
        state = State.IDLE;
        paused = false;
        closeWindow(client);
        status(client, "Regear: done (" + donePurchases + " purchases)");
    }

    private static void closeWindow(MinecraftClient client) {
        if (client.player != null && openContainer(client) != null) {
            client.player.closeHandledScreen();
        }
    }

    private static void status(MinecraftClient client, String msg) {
        if (client.player != null) client.player.sendMessage(Text.literal(msg), true);
    }

    // ---------- chat ----------

    /** Called for every game message from the server. */
    public static void onChat(String message) {
        if (state == State.IDLE || message == null) return;
        String m = message.toLowerCase(Locale.ROOT);
        if (m.contains(RegearConfig.msgBought.toLowerCase(Locale.ROOT))) gotBought = true;
        if (m.contains(RegearConfig.msgFull.toLowerCase(Locale.ROOT))) gotFull = true;
        if (m.contains(RegearConfig.msgMoney.toLowerCase(Locale.ROOT))) gotMoney = true;
    }

    // ---------- helpers ----------

    private static int countItem(MinecraftClient client, Item item) {
        int n = 0;
        PlayerInventory inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) n += s.getCount();
        }
        return n;
    }

    /** The chest-style window the server has open for us right now (shown or hidden), or null. */
    private static GenericContainerScreenHandler openContainer(MinecraftClient client) {
        if (client.player == null) return null;
        var h = client.player.currentScreenHandler;
        if (h == client.player.playerScreenHandler) return null;
        return h instanceof GenericContainerScreenHandler g ? g : null;
    }

    /** An open window that is not the one we last clicked in (the server opened a new menu). */
    private static GenericContainerScreenHandler newContainer(MinecraftClient client) {
        GenericContainerScreenHandler g = openContainer(client);
        return g != null && g.syncId != baseSync ? g : null;
    }

    private static boolean isGreenPane(ItemStack s) {
        return s.isOf(Items.LIME_STAINED_GLASS_PANE) || s.isOf(Items.GREEN_STAINED_GLASS_PANE);
    }

    private static boolean validSlot(GenericContainerScreenHandler h, int slot) {
        return slot >= 0 && slot < h.getRows() * 9;
    }

    private static ItemStack stackAt(GenericContainerScreenHandler h, int slot) {
        return validSlot(h, slot) ? h.getSlot(slot).getStack() : ItemStack.EMPTY;
    }

    /** The configured slot if the item is there, otherwise the first slot that holds it, otherwise -1. */
    private static int findSlot(GenericContainerScreenHandler h, int wanted, Item item) {
        if (stackAt(h, wanted).isOf(item)) return wanted;
        for (int i = 0; i < h.getRows() * 9; i++) {
            if (h.getSlot(i).getStack().isOf(item)) return i;
        }
        return -1;
    }

    private static void click(MinecraftClient client, GenericContainerScreenHandler h, int slot) {
        client.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.PICKUP, client.player);
        cooldown = RegearConfig.clickDelayTicks;
    }

    private static void beginNavigation(MinecraftClient client) {
        GenericContainerScreenHandler open = openContainer(client);
        baseSync = open != null ? open.syncId : -1;
        client.player.networkHandler.sendChatCommand(RegearConfig.command);
        state = State.OPENING;
        timer = 0;
    }

    /**
     * Fewest clicks to go from quantity "from" to "to" using the +/-1, +/-10 and +/-64 buttons
     * (the quantity stays between 1 and the stack size). Uses only buttons that are currently
     * shown (not empty) and not marked as broken. Returns the slot to click, or -1.
     */
    private static int nextMove(GenericContainerScreenHandler h, int from, int to, int max) {
        int[][] moves = {
                {RegearConfig.plus64Slot, 64}, {RegearConfig.minus64Slot, -64},
                {RegearConfig.plus10Slot, 10}, {RegearConfig.minus10Slot, -10},
                {RegearConfig.plus1Slot, 1}, {RegearConfig.minus1Slot, -1}
        };
        int cap = Math.max(max, Math.max(from, to));
        int[] first = new int[cap + 1];
        Arrays.fill(first, -1);
        boolean[] seen = new boolean[cap + 1];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = RegearConfig.clamp(from, 0, cap);
        seen[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            for (int[] m : moves) {
                int slot = m[0];
                if (blocked.contains(slot) || stackAt(h, slot).isEmpty()) continue;
                int next = RegearConfig.clamp(cur + m[1], 1, max);
                if (next == cur || seen[next]) continue;
                seen[next] = true;
                first[next] = cur == start ? slot : first[cur];
                if (next == to) return first[next];
                queue.add(next);
            }
        }
        return -1;
    }

    // ---------- main loop ----------

    public static void tick(MinecraftClient client) {
        if (state == State.IDLE) return;
        if (client.player == null || client.getNetworkHandler() == null || client.interactionManager == null) {
            state = State.IDLE;
            paused = false;
            return;
        }
        timer++;
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        switch (state) {
            case IDLE -> { }
            case OPENING -> {
                GenericContainerScreenHandler h = newContainer(client);
                if (h == null) {
                    if (timer > RegearConfig.waitTicks) fail(client, "the shop did not open (check the command)");
                    return;
                }
                int slot = findSlot(h, RegearConfig.categorySlot, Items.TOTEM_OF_UNDYING);
                if (slot < 0) {
                    fail(client, "could not find the Gear category in the main shop menu");
                    return;
                }
                click(client, h, slot);
                baseSync = h.syncId;
                state = State.GEAR_WAIT;
                timer = 0;
            }
            case GEAR_WAIT -> {
                if (newContainer(client) != null) {
                    state = State.GEAR_PICK;
                    timer = 0;
                } else if (timer > RegearConfig.waitTicks) {
                    fail(client, "the Gear shop did not open");
                }
            }
            case GEAR_PICK -> {
                if (paused) return;
                GenericContainerScreenHandler h = openContainer(client);
                if (h == null) {
                    fail(client, "the shop menu closed");
                    return;
                }
                Job j = jobs.get(jobIdx);
                int slot = findSlot(h, RegearConfig.kitSlot[j.kit.ordinal()], j.kit.item);
                if (slot < 0) {
                    fail(client, "could not find " + j.kit.label + " in the shop menu");
                    return;
                }
                click(client, h, slot);
                baseSync = h.syncId;
                state = State.BUY_WAIT;
                timer = 0;
            }
            case BUY_WAIT -> {
                GenericContainerScreenHandler h = newContainer(client);
                if (h == null) {
                    if (timer > RegearConfig.waitTicks) fail(client, "the buy menu did not open");
                    return;
                }
                Job j = jobs.get(jobIdx);
                if (!stackAt(h, RegearConfig.displaySlot).isOf(j.kit.item)) {
                    fail(client, "the buy menu does not show " + j.kit.label + " (check Menu layout)");
                    return;
                }
                if (!isGreenPane(stackAt(h, RegearConfig.confirmSlot))) {
                    fail(client, "confirm button not found at slot " + RegearConfig.confirmSlot + " (check Menu layout)");
                    return;
                }
                state = State.ADJUST;
                timer = 0;
                attempts = 0;
                adjustClicks = 0;
                blocked.clear();
            }
            case ADJUST -> {
                if (paused) return;
                GenericContainerScreenHandler h = openContainer(client);
                if (h == null) {
                    fail(client, "the buy menu closed");
                    return;
                }
                Job j = jobs.get(jobIdx);
                ItemStack shown = stackAt(h, RegearConfig.displaySlot);
                if (!shown.isOf(j.kit.item)) {
                    fail(client, "the buy menu changed unexpectedly");
                    return;
                }
                int target = j.purchases.peek();
                int count = shown.getCount();
                int max = j.kit.maxStack();
                if (max == 1 || count == target) {
                    if (!isGreenPane(stackAt(h, RegearConfig.confirmSlot))) {
                        fail(client, "confirm button not found (check Menu layout)");
                        return;
                    }
                    gotBought = false;
                    gotFull = false;
                    gotMoney = false;
                    click(client, h, RegearConfig.confirmSlot);
                    state = State.CONFIRM_WAIT;
                    timer = 0;
                    return;
                }
                if (++adjustClicks > 40) {
                    fail(client, "the quantity would not settle on " + target + " (check Menu layout)");
                    return;
                }
                int move = nextMove(h, count, target, max);
                if (move < 0) {
                    fail(client, "cannot reach quantity " + target + " with the quantity buttons (check Menu layout)");
                    return;
                }
                lastCount = count;
                lastMoveSlot = move;
                click(client, h, move);
                state = State.ADJUST_WAIT;
                timer = 0;
            }
            case ADJUST_WAIT -> {
                GenericContainerScreenHandler h = openContainer(client);
                if (h == null) {
                    fail(client, "the buy menu closed");
                    return;
                }
                if (stackAt(h, RegearConfig.displaySlot).getCount() != lastCount) {
                    state = State.ADJUST;
                    return;
                }
                if (timer > RegearConfig.waitTicks / 2) {
                    // That button did nothing: do not use it again for this item.
                    blocked.add(lastMoveSlot);
                    if (++attempts > 4) {
                        fail(client, "the quantity buttons are not responding");
                        return;
                    }
                    state = State.ADJUST;
                }
            }
            case CONFIRM_WAIT -> {
                if (gotFull) {
                    fail(client, "your inventory is full");
                    return;
                }
                if (gotMoney) {
                    fail(client, "not enough money");
                    return;
                }
                if (gotBought) {
                    Job j = jobs.get(jobIdx);
                    j.purchases.poll();
                    donePurchases++;
                    if (!j.purchases.isEmpty()) {
                        state = State.ADJUST;
                        timer = 0;
                        attempts = 0;
                        adjustClicks = 0;
                        blocked.clear();
                        return;
                    }
                    jobIdx++;
                    if (jobIdx >= jobs.size()) {
                        finish(client);
                        return;
                    }
                    GenericContainerScreenHandler h = openContainer(client);
                    if (h == null) {
                        fail(client, "the buy menu closed");
                        return;
                    }
                    click(client, h, RegearConfig.cancelSlot);
                    baseSync = h.syncId;
                    state = State.BACK_WAIT;
                    timer = 0;
                    return;
                }
                if (timer > RegearConfig.waitTicks) {
                    fail(client, "no purchase message was received (check the Messages page)");
                }
            }
            case BACK_WAIT -> {
                if (newContainer(client) != null) {
                    state = State.GEAR_PICK;
                    timer = 0;
                } else if (timer > RegearConfig.waitTicks) {
                    // The cancel button did not take us back: reopen the shop and continue.
                    if (++navRetries > 2) {
                        fail(client, "lost the shop menu");
                        return;
                    }
                    closeWindow(client);
                    beginNavigation(client);
                }
            }
        }
    }
}
