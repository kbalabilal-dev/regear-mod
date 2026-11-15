package com.regearmod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A page of text boxes (one label + one box per row), in 1 or 2 columns.
 * Everything typed is saved right away.
 */
public class RegearFieldsScreen extends Screen {
    public record Row(String label, Supplier<String> get, Consumer<String> set) {}

    private final Screen parent;
    private final List<Row> rows;
    private final int columns;
    private final String hint;

    private final List<String> labelTexts = new ArrayList<>();
    private final List<int[]> labelPos = new ArrayList<>();

    public RegearFieldsScreen(String title, Screen parent, List<Row> rows, int columns, String hint) {
        super(Text.literal(title));
        this.parent = parent;
        this.rows = rows;
        this.columns = Math.max(1, columns);
        this.hint = hint;
    }

    @Override
    protected void init() {
        labelTexts.clear();
        labelPos.clear();

        int perCol = (rows.size() + columns - 1) / columns;
        boolean wide = columns == 1;
        int colW = wide ? 320 : 156;
        int labelW = wide ? 90 : 88;
        int fieldW = wide ? 220 : 60;
        int x0 = width / 2 - (colW * columns + 4 * (columns - 1)) / 2;

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int col = i / perCol;
            int row = i % perCol;
            int x = x0 + col * (colW + 4);
            int y = 34 + row * 22;

            TextFieldWidget f = new TextFieldWidget(textRenderer, x + labelW, y, fieldW, 20, Text.literal(r.label()));
            f.setMaxLength(wide ? 80 : 6);
            f.setText(r.get().get());
            f.setChangedListener(s -> {
                r.set().accept(s);
                RegearConfig.save();
            });
            addDrawableChild(f);

            labelTexts.add(r.label());
            labelPos.add(new int[]{x, y + 6});
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());
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
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(hint), width / 2, 21, 0xFFAAAAAA);
        for (int i = 0; i < labelTexts.size(); i++) {
            int[] p = labelPos.get(i);
            context.drawTextWithShadow(textRenderer, labelTexts.get(i), p[0], p[1], 0xFFFFFFFF);
        }
    }
}
