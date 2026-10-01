// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.client.codex;

import dev.mistercheese.apothicascension.AscensionStage;
import dev.mistercheese.apothicascension.AscensionStageTracker;
import dev.mistercheese.apothicascension.registry.ModItems;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Pixel-art in-game reference for Ascension progression, workstations and diagnostics. */
public final class AscensionCodexScreen extends Screen {
    private static final int GUI_W = 280;
    private static final int GUI_H = 200;
    private static final int GOLD = 0xFFE4BD52;
    private static final int TEXT = 0xFFD9D5C9;
    private static final int MUTED = 0xFF8D887D;
    private static final int PANEL = 0xEE12151A;
    private static final int SELECTED = 0xFF5B4823;
    private static final int BODY_TOP_OFFSET = 54;
    private static final int BODY_BOTTOM_OFFSET = 166;
    private static final int SCROLL_STEP = 18;
    private static final int BODY_WIDTH = 204;
    private static final int LINE_HEIGHT = 10;
    private static final int PARAGRAPH_GAP = 5;
    private Page page = Page.OVERVIEW;
    private int left;
    private int top;
    private int scrollOffset;

    public AscensionCodexScreen() {
        super(Component.translatable("screen.apothic_ascension.codex"));
    }

    @Override
    protected void init() {
        this.left = (this.width - GUI_W) / 2;
        this.top = (this.height - GUI_H) / 2;
        this.scrollOffset = 0;
        this.rebuildPageButtons();
    }

    private void rebuildPageButtons() {
        this.clearWidgets();
        int y = this.top + 39;
        for (Page candidate : Page.values()) {
            this.addRenderableWidget(new PageButton(this.left + 10, y, candidate, () -> {
                this.page = candidate;
                this.scrollOffset = 0;
                this.rebuildPageButtons();
            }));
            y += 24;
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally no-op: render() draws the Codex backdrop itself. Calling the vanilla
        // implementation here applies the menu blur shader and makes the Codex visibly soft.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Do not invoke Screen#renderBackground here. On 1.21 that path may use the menu blur
        // post-process, and on some renderer/UI-scale combinations the Codex itself can remain
        // visibly soft. Beta 9 draws a simple dim layer plus integer-pixel chrome instead.
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
        renderCrispFrame(graphics);

        graphics.drawString(this.font, Component.translatable("screen.apothic_ascension.codex"),
            this.left + 31, this.top + 12, GOLD, false);
        graphics.drawString(this.font, Component.translatable("screen.apothic_ascension.codex.subtitle"),
            this.left + 148, this.top + 12, MUTED, false);

        this.renderPage(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCrispFrame(GuiGraphics graphics) {
        int x = this.left;
        int y = this.top;
        int border = 0xFF735D2D;
        int inner = 0xFF2F2A1F;
        int surface = 0xFF111419;
        int footer = 0xFF0D1014;

        graphics.fill(x, y, x + GUI_W, y + GUI_H, 0xFF090B0F);
        graphics.fill(x, y, x + GUI_W, y + 1, GOLD);
        graphics.fill(x, y + GUI_H - 1, x + GUI_W, y + GUI_H, border);
        graphics.fill(x, y, x + 1, y + GUI_H, border);
        graphics.fill(x + GUI_W - 1, y, x + GUI_W, y + GUI_H, border);
        graphics.fill(x + 4, y + 4, x + GUI_W - 4, y + 5, inner);
        graphics.fill(x + 4, y + 31, x + GUI_W - 4, y + 33, border);

        graphics.fill(x + 4, y + 34, x + 43, y + 191, surface);
        graphics.fill(x + 4, y + 34, x + 5, y + 191, inner);
        graphics.fill(x + 42, y + 34, x + 43, y + 191, inner);
        graphics.fill(x + 46, y + 34, x + 273, y + 170, surface);
        graphics.fill(x + 46, y + 34, x + 273, y + 35, inner);
        graphics.fill(x + 46, y + 169, x + 273, y + 170, inner);
        graphics.fill(x + 46, y + 172, x + 273, y + 191, footer);
        graphics.fill(x + 46, y + 172, x + 273, y + 173, inner);

        // Tiny seal mark, also drawn from integer primitives to avoid texture filtering.
        int cx = x + 22;
        int cy = y + 17;
        graphics.fill(cx - 5, cy, cx + 6, cy + 1, border);
        graphics.fill(cx, cy - 5, cx + 1, cy + 6, GOLD);
        graphics.fill(cx - 3, cy - 3, cx - 2, cy - 2, GOLD);
        graphics.fill(cx + 3, cy + 3, cx + 4, cy + 4, GOLD);
    }

    private void renderPage(GuiGraphics graphics) {
        int contentX = this.left + 51;
        int titleY = this.top + 39;
        int bodyTop = this.top + BODY_TOP_OFFSET;
        int bodyBottom = this.top + BODY_BOTTOM_OFFSET;
        int width = BODY_WIDTH;

        graphics.drawString(this.font, Component.translatable(this.page.titleKey), contentX, titleY, GOLD, false);
        ItemStack icon = this.page.icon();
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, this.left + 246, this.top + 38);
        }

        int maxScroll = this.maxScroll(width);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);
        int y = bodyTop - this.scrollOffset;

        graphics.enableScissor(this.left + 47, bodyTop, this.left + 272, bodyBottom);
        if (this.page == Page.PROGRESSION && this.minecraft != null && this.minecraft.player != null) {
            AscensionStage progression = AscensionStageTracker.getProgressionStored(this.minecraft.player);
            AscensionStage effective = AscensionStageTracker.getStored(this.minecraft.player);
            Component progressionName = Component.translatable("stage.apothic_ascension." + progression.key());
            Component stageLine;
            int stageColor;
            if (AscensionStageTracker.hasDebugOverride(this.minecraft.player)) {
                Component effectiveName = Component.translatable("stage.apothic_ascension." + effective.key());
                stageLine = Component.translatable(
                    "screen.apothic_ascension.codex.progression.current_debug", effectiveName, progressionName);
                stageColor = 0xFFBCA7E8;
            }
            else {
                stageLine = Component.translatable(
                    "screen.apothic_ascension.codex.progression.current", progressionName);
                stageColor = 0xFFB8D6B1;
            }
            y = this.drawWrapped(graphics, stageLine, contentX, y, width, stageColor) + 3;
        }

        for (String key : this.page.paragraphKeys) {
            y = this.drawWrapped(graphics, Component.translatable(key), contentX, y, width, TEXT) + PARAGRAPH_GAP;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            this.renderScrollBar(graphics, bodyTop, bodyBottom, maxScroll);
        }

        graphics.drawString(this.font, Component.translatable("screen.apothic_ascension.codex.footer"),
            contentX, this.top + 176, MUTED, false);
        graphics.drawString(this.font, (this.page.ordinal() + 1) + "/" + Page.values().length,
            this.left + 245, this.top + 176, MUTED, false);
    }

    private int maxScroll(int width) {
        int contentHeight = 0;
        if (this.page == Page.PROGRESSION && this.minecraft != null && this.minecraft.player != null) {
            AscensionStage progression = AscensionStageTracker.getProgressionStored(this.minecraft.player);
            AscensionStage effective = AscensionStageTracker.getStored(this.minecraft.player);
            Component progressionName = Component.translatable("stage.apothic_ascension." + progression.key());
            Component stageLine;
            if (AscensionStageTracker.hasDebugOverride(this.minecraft.player)) {
                Component effectiveName = Component.translatable("stage.apothic_ascension." + effective.key());
                stageLine = Component.translatable(
                    "screen.apothic_ascension.codex.progression.current_debug", effectiveName, progressionName);
            }
            else {
                stageLine = Component.translatable(
                    "screen.apothic_ascension.codex.progression.current", progressionName);
            }
            contentHeight += this.wrappedHeight(stageLine, width) + 3;
        }
        for (String key : this.page.paragraphKeys) {
            contentHeight += this.wrappedHeight(Component.translatable(key), width) + PARAGRAPH_GAP;
        }
        if (this.page.paragraphKeys.length > 0) contentHeight -= PARAGRAPH_GAP;
        int viewportHeight = BODY_BOTTOM_OFFSET - BODY_TOP_OFFSET;
        return Math.max(0, contentHeight - viewportHeight);
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x, y, color, false);
            y += LINE_HEIGHT;
        }
        return y;
    }

    private int wrappedHeight(Component text, int width) {
        return this.font.split(text, width).size() * LINE_HEIGHT;
    }

    private void renderScrollBar(GuiGraphics graphics, int bodyTop, int bodyBottom, int maxScroll) {
        int viewportHeight = bodyBottom - bodyTop;
        int contentHeight = viewportHeight + maxScroll;
        int thumbHeight = Math.max(12, viewportHeight * viewportHeight / contentHeight);
        int travel = viewportHeight - thumbHeight;
        int thumbTop = bodyTop + (int) Math.round(travel * (this.scrollOffset / (double) maxScroll));
        int x = this.left + 269;
        graphics.fill(x, bodyTop, x + 1, bodyBottom, 0xFF2F2A1F);
        graphics.fill(x - 1, thumbTop, x + 2, thumbTop + thumbHeight, GOLD);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = this.maxScroll(BODY_WIDTH);
        if (maxScroll > 0 && this.isOverScrollableContent(mouseX, mouseY)) {
            int next = (int) Math.round(this.scrollOffset - scrollY * SCROLL_STEP);
            this.scrollOffset = Mth.clamp(next, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isOverScrollableContent(double mouseX, double mouseY) {
        return mouseX >= this.left + 46 && mouseX < this.left + 273
            && mouseY >= this.top + BODY_TOP_OFFSET && mouseY < this.top + BODY_BOTTOM_OFFSET;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Page {
        OVERVIEW("overview", () -> ModItems.ASCENSION_SEAL.get().getDefaultInstance(), 2),
        PROGRESSION("progression", () -> ModItems.LEGENDARY_MATERIAL.get().getDefaultInstance(), 3),
        WORKSTATIONS("workstations", () -> ModItems.ASCENSION_FORGE.get().getDefaultInstance(), 3),
        FOCUS("focus", () -> ModItems.ATTACK_FOCUS_SIGIL.get().getDefaultInstance(), 3),
        APEX("apex", () -> ModItems.APEX_ASCENSION_BENCH.get().getDefaultInstance(), 3),
        DIAGNOSTICS("diagnostics", () -> ModItems.ASCENSION_CODEX.get().getDefaultInstance(), 2);

        private final String titleKey;
        private final String[] paragraphKeys;
        private final java.util.function.Supplier<ItemStack> icon;

        Page(String id, java.util.function.Supplier<ItemStack> icon, int paragraphs) {
            this.titleKey = "screen.apothic_ascension.codex." + id + ".title";
            this.paragraphKeys = new String[paragraphs];
            for (int i = 0; i < paragraphs; i++) {
                this.paragraphKeys[i] = "screen.apothic_ascension.codex." + id + ".p" + (i + 1);
            }
            this.icon = icon;
        }

        ItemStack icon() {
            return this.icon.get();
        }
    }

    private final class PageButton extends Button {
        private final Page target;

        private PageButton(int x, int y, Page target, Runnable press) {
            super(x, y, 28, 20, Component.translatable(target.titleKey), b -> press.run(), DEFAULT_NARRATION);
            this.target = target;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = AscensionCodexScreen.this.page == this.target;
            int border = selected ? GOLD : (this.isHoveredOrFocused() ? 0xFFC2A55B : 0xFF4E422A);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, PANEL);
            graphics.fill(this.getX(), this.getY(), this.getX() + 2, this.getY() + this.height, selected ? GOLD : border);
            if (selected) {
                graphics.fill(this.getX() + 2, this.getY() + 1, this.getX() + this.width, this.getY() + this.height - 1, SELECTED);
            }
            ItemStack icon = this.target.icon();
            if (!icon.isEmpty()) graphics.renderItem(icon, this.getX() + 6, this.getY() + 2);
        }
    }
}
