package com.regearmod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class RegearConfigScreen extends Screen {
    private static final String[] HUD_NAMES = {"HUD: off", "HUD: top center", "HUD: top left", "HUD: top right"};

    private final Screen parent;
    private TextFieldWidget commandField;
    private ButtonWidget startBtn, pauseBtn;

    public RegearConfigScreen(Screen parent) {
        super(Text.literal("Auto Regear settings"));
        this.parent = parent;
    }

    private static String secs(int ticks) {
        return String.format(Locale.ROOT, "%.2f", ticks / 20.0);
    }

    private static Text topUpLabel() {
        return Text.literal(RegearConfig.topUp ? "Top-up: on" : "Top-up: off");
    }

    private static Text windowLabel() {
        return Text.literal(RegearConfig.hideWindow ? "Windows: hidden" : "Windows: shown");
    }

    private static Text debugLabel() {
        return Text.literal(RegearConfig.debug ? "Debug: on" : "Debug: off");
    }

    private static Text startLabel() {
        return Text.literal(RegearRunner.isRunning() ? "Stop regear" : "Start regear");
    }

    private static Text pauseLabel() {
        return Text.literal(RegearRunner.isPaused() ? "Resume" : "Pause");
    }

    @Override
    protected void init() {
        // Command that opens the shop. Saved while you type.
        commandField = new TextFieldWidget(textRenderer, width / 2 - 100, 28, 200, 20, Text.literal("Shop command"));
        commandField.setMaxLength(40);
        commandField.setText(RegearConfig.command);
        commandField.setChangedListener(s -> {
            RegearConfig.command = RegearConfig.cleanCommand(s);
            RegearConfig.save();
        });
        addDrawableChild(commandField);

        addStepper(54, "Click delay", () -> RegearConfig.clickDelayTicks, v -> RegearConfig.clickDelayTicks = v, 1, 40, 1, 5);
        addStepper(76, "Wait time", () -> RegearConfig.waitTicks, v -> RegearConfig.waitTicks = v, 10, 200, 5, 20);

        int left = width / 2 - 100;
        int right = width / 2 + 2;

        // Row A: sub pages
        addDrawableChild(ButtonWidget.builder(Text.literal("Kit items..."), b -> client.setScreen(kitScreen()))
                .dimensions(left, 102, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Menu layout..."), b -> client.setScreen(layoutScreen()))
                .dimensions(right, 102, 98, 20).build());

        // Row B
        addDrawableChild(ButtonWidget.builder(Text.literal("Messages..."), b -> client.setScreen(messagesScreen()))
                .dimensions(left, 124, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(topUpLabel(), b -> {
                    RegearConfig.topUp = !RegearConfig.topUp;
                    RegearConfig.save();
                    b.setMessage(topUpLabel());
                }).dimensions(right, 124, 98, 20).build());

        // Row C
        addDrawableChild(ButtonWidget.builder(windowLabel(), b -> {
                    RegearConfig.hideWindow = !RegearConfig.hideWindow;
                    RegearConfig.save();
                    b.setMessage(windowLabel());
                }).dimensions(left, 146, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(HUD_NAMES[RegearConfig.hudPos]), b -> {
                    RegearConfig.hudPos = (RegearConfig.hudPos + 1) % HUD_NAMES.length;
                    RegearConfig.save();
                    b.setMessage(Text.literal(HUD_NAMES[RegearConfig.hudPos]));
                }).dimensions(right, 146, 98, 20).build());

        // Row D: start / pause
        startBtn = addDrawableChild(ButtonWidget.builder(startLabel(), b -> {
                    if (RegearRunner.isRunning()) {
                        RegearRunner.cancel(client);
                        b.setMessage(startLabel());
                        pauseBtn.active = false;
                    } else {
                        client.setScreen(null);
                        RegearRunner.start(client);
                    }
                }).dimensions(left, 168, 98, 20).build());
        startBtn.active = client.world != null;

        pauseBtn = addDrawableChild(ButtonWidget.builder(pauseLabel(), b -> {
                    RegearRunner.togglePause(client);
                    b.setMessage(pauseLabel());
                }).dimensions(right, 168, 98, 20).build());
        pauseBtn.active = RegearRunner.isRunning();

        // Row E: sorting and totem options
        addDrawableChild(ButtonWidget.builder(Text.literal("Sorting & totems..."), b -> client.setScreen(new RegearSortScreen(this)))
                .dimensions(left, 190, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(debugLabel(), b -> {
                    RegearConfig.debug = !RegearConfig.debug;
                    RegearConfig.save();
                    b.setMessage(debugLabel());
                }).dimensions(right, 190, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());
    }

    private Screen kitScreen() {
        List<RegearFieldsScreen.Row> rows = new ArrayList<>();
        for (KitItem k : KitItem.values()) {
            int i = k.ordinal();
            rows.add(new RegearFieldsScreen.Row(k.label,
                    () -> String.valueOf(RegearConfig.kitSlot[i]),
                    s -> RegearConfig.kitSlot[i] = RegearConfig.parse(s, RegearConfig.kitSlot[i], 0, 53)));
        }
        for (KitItem k : KitItem.values()) {
            int i = k.ordinal();
            rows.add(new RegearFieldsScreen.Row(k.label,
                    () -> String.valueOf(RegearConfig.kitAmount[i]),
                    s -> RegearConfig.kitAmount[i] = RegearConfig.parse(s, RegearConfig.kitAmount[i], 0, 9999)));
        }
        return new RegearFieldsScreen("Kit items", this, rows, 2,
                "Left: shop slot of each item.  Right: how many to carry (0 = skip)");
    }

    private Screen layoutScreen() {
        List<RegearFieldsScreen.Row> rows = new ArrayList<>();
        rows.add(slotRow("Category slot", () -> RegearConfig.categorySlot, v -> RegearConfig.categorySlot = v));
        rows.add(slotRow("Item shown", () -> RegearConfig.displaySlot, v -> RegearConfig.displaySlot = v));
        rows.add(slotRow("Minus 64", () -> RegearConfig.minus64Slot, v -> RegearConfig.minus64Slot = v));
        rows.add(slotRow("Minus 10", () -> RegearConfig.minus10Slot, v -> RegearConfig.minus10Slot = v));
        rows.add(slotRow("Minus 1", () -> RegearConfig.minus1Slot, v -> RegearConfig.minus1Slot = v));
        rows.add(slotRow("Plus 1", () -> RegearConfig.plus1Slot, v -> RegearConfig.plus1Slot = v));
        rows.add(slotRow("Plus 10", () -> RegearConfig.plus10Slot, v -> RegearConfig.plus10Slot = v));
        rows.add(slotRow("Plus 64", () -> RegearConfig.plus64Slot, v -> RegearConfig.plus64Slot = v));
        rows.add(slotRow("Cancel", () -> RegearConfig.cancelSlot, v -> RegearConfig.cancelSlot = v));
        rows.add(slotRow("Confirm", () -> RegearConfig.confirmSlot, v -> RegearConfig.confirmSlot = v));
        return new RegearFieldsScreen("Menu layout", this, rows, 2,
                "Slots count from 0: left to right, top to bottom (row 2, column 5 = 13)");
    }

    private static RegearFieldsScreen.Row slotRow(String label, IntSupplier get, IntConsumer set) {
        return new RegearFieldsScreen.Row(label,
                () -> String.valueOf(get.getAsInt()),
                s -> set.accept(RegearConfig.parse(s, get.getAsInt(), 0, 53)));
    }

    private Screen messagesScreen() {
        List<RegearFieldsScreen.Row> rows = new ArrayList<>();
        rows.add(new RegearFieldsScreen.Row("Bought", () -> RegearConfig.msgBought,
                s -> RegearConfig.msgBought = s.isBlank() ? RegearConfig.msgBought : s));
        rows.add(new RegearFieldsScreen.Row("Inventory full", () -> RegearConfig.msgFull,
                s -> RegearConfig.msgFull = s.isBlank() ? RegearConfig.msgFull : s));
        rows.add(new RegearFieldsScreen.Row("No money", () -> RegearConfig.msgMoney,
                s -> RegearConfig.msgMoney = s.isBlank() ? RegearConfig.msgMoney : s));
        return new RegearFieldsScreen("Chat messages", this, rows, 1,
                "Part of the message is enough. Upper/lower case does not matter.");
    }

    private void addStepper(int y, String name, IntSupplier get, IntConsumer set,
                            int min, int max, int small, int big) {
        int x0 = width / 2 - 134;
        ButtonWidget label = ButtonWidget.builder(
                Text.literal(name + " " + secs(get.getAsInt()) + "s"), b -> { })
                .dimensions(x0 + 84, y, 100, 20).build();
        label.active = false;

        int[] steps = {-big, -small, small, big};
        int[] xs = {x0, x0 + 42, x0 + 186, x0 + 228};
        for (int i = 0; i < 4; i++) {
            int d = steps[i];
            String text = (d < 0 ? "-" : "+") + secs(Math.abs(d));
            addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> {
                        set.accept(MathHelper.clamp(get.getAsInt() + d, min, max));
                        RegearConfig.save();
                        label.setMessage(Text.literal(name + " " + secs(get.getAsInt()) + "s"));
                    }).dimensions(xs[i], y, 40, 20).build());
        }
        addDrawableChild(label);
    }

    @Override
    public void close() {
        RegearConfig.save();
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 6, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Shop command (without the /)"), width / 2, 17, 0xFFAAAAAA);
    }
}
