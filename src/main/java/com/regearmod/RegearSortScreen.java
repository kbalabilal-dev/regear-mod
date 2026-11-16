package com.regearmod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Sorting and totem settings: what goes in the offhand and each hotbar slot, and the totem fill toggle. */
public class RegearSortScreen extends Screen {
    private final Screen parent;

    public RegearSortScreen(Screen parent) {
        super(Text.literal("Sorting & totems"));
        this.parent = parent;
    }

    private static Text sortLabel() {
        return Text.literal(RegearConfig.sortEnabled ? "Sort after buying: on" : "Sort after buying: off");
    }

    private static Text fillLabel() {
        return Text.literal(RegearConfig.fillTotems ? "Fill with totems: on" : "Fill with totems: off");
    }

    private static Text itemLabel(String name, int value) {
        String item = value < 0 ? "Leave" : KitItem.values()[value].label;
        return Text.literal(name + ": " + item);
    }

    @Override
    protected void init() {
        int left = width / 2 - 154;
        int mid = width / 2 - 50;
        int right = width / 2 + 54;

        addDrawableChild(ButtonWidget.builder(sortLabel(), b -> {
                    RegearConfig.sortEnabled = !RegearConfig.sortEnabled;
                    RegearConfig.save();
                    b.setMessage(sortLabel());
                }).dimensions(left, 32, 152, 20).build());
        addDrawableChild(ButtonWidget.builder(fillLabel(), b -> {
                    RegearConfig.fillTotems = !RegearConfig.fillTotems;
                    RegearConfig.save();
                    b.setMessage(fillLabel());
                }).dimensions(width / 2 + 2, 32, 152, 20).build());

        addCycle(left, 58, 152, "Offhand", () -> RegearConfig.offhandItem, v -> RegearConfig.offhandItem = v);

        int[] xs = {left, mid, right};
        for (int i = 0; i < 9; i++) {
            int idx = i;
            int x = i % 3 == 0 ? xs[0] : i % 3 == 1 ? xs[1] : xs[2];
            int w = 100;
            int y = 94 + (i / 3) * 22;
            addCycle(x, y, w, "Slot " + (i + 1), () -> RegearConfig.hotbarItem[idx], v -> RegearConfig.hotbarItem[idx] = v);
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());
    }

    /** A button that cycles through: Leave, then every kit item. */
    private void addCycle(int x, int y, int w, String name, IntSupplier get, IntConsumer set) {
        addDrawableChild(ButtonWidget.builder(itemLabel(name, get.getAsInt()), b -> {
                    int next = get.getAsInt() + 1;
                    if (next >= KitItem.values().length) next = -1;
                    set.accept(next);
                    RegearConfig.save();
                    b.setMessage(itemLabel(name, next));
                }).dimensions(x, y, w, 20).build());
    }

    @Override
    public void close() {
        RegearConfig.save();
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Fill with totems buys totems last until every free slot is full"), width / 2, 20, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Hotbar slots (click to change)"), width / 2, 83, 0xFFAAAAAA);
    }
}
