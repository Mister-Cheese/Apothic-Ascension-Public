// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.menu.ApexReforgeOffers;
import dev.mistercheese.apothicascension.menu.AscensionWorkstationMenu;
import dev.mistercheese.apothicascension.menu.WorkstationKind;
import dev.mistercheese.apothicascension.registry.ModItems;
import dev.shadowsoffire.apotheosis.Apoth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Icon-first workstation UI. Apex uses a five-choice reforge-table layout with server-owned offers. */
public final class AscensionWorkstationScreen extends AbstractContainerScreen<AscensionWorkstationMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
        ApothicAscension.MODID, "textures/gui/workstation_base.png");
    private static final ResourceLocation APEX_BENCH_PREVIEW = ResourceLocation.fromNamespaceAndPath(
        ApothicAscension.MODID, "textures/gui/apex_bench_preview.png");
    private static final ResourceLocation APEX_PYLON_PREVIEW = ResourceLocation.fromNamespaceAndPath(
        ApothicAscension.MODID, "textures/gui/apex_pylon_preview.png");
    private static final int GOLD = 0xFFE9C25C;
    private static final int MUTED = 0xFF9F957B;
    private static final int SLOT_BORDER = 0xFF765F33;
    private static final int SLOT_INNER = 0xFF101318;
    private static final String[] OFFER_LABELS = {"I", "II", "III", "IV", "V"};

    private final List<ApexChoiceButton> apexChoiceButtons = new ArrayList<>();

    public AscensionWorkstationScreen(AscensionWorkstationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        boolean apex = menu.kind() == WorkstationKind.APEX_ASCENSION_BENCH;
        this.imageWidth = apex ? 272 : 194;
        this.imageHeight = apex ? 224 : 188;
        this.titleLabelX = 0;
        this.titleLabelY = 7;
        this.inventoryLabelX = apex ? 55 : 17;
        this.inventoryLabelY = apex ? 134 : 96;
    }

    @Override
    protected void init() {
        super.init();
        this.apexChoiceButtons.clear();
        if (this.menu.kind() == WorkstationKind.AFFIX_LOOM) {
            for (int i = 0; i < 5; i++) {
                final int family = i;
                this.addRenderableWidget(new FocusButton(
                    this.leftPos + 27 + i * 29, this.topPos + 70, family,
                    () -> this.menu.selectedFamily() == family,
                    button -> {
                        if (this.minecraft != null && this.minecraft.gameMode != null) {
                            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, family);
                        }
                    }));
            }
        }
        else if (this.menu.kind() == WorkstationKind.APEX_ASCENSION_BENCH) {
            for (int i = 0; i < AscensionWorkstationMenu.APEX_OFFER_COUNT; i++) {
                final int choice = i;
                ApexChoiceButton button = new ApexChoiceButton(
                    this.leftPos + 87 + i * 32, this.topPos + 83, choice,
                    ignored -> {
                        if (this.minecraft != null && this.minecraft.gameMode != null) {
                            this.minecraft.gameMode.handleInventoryButtonClick(
                                this.menu.containerId, AscensionWorkstationMenu.APEX_BUTTON_BASE + choice);
                        }
                    });
                this.apexChoiceButtons.add(button);
                this.addRenderableWidget(button);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        this.renderGhostTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int accent = 0xFF000000 | this.menu.kind().accentColor();

        if (this.menu.kind() == WorkstationKind.APEX_ASCENSION_BENCH) {
            renderApexPanel(graphics, x, y, accent);
            updateApexButtons();
        }
        else {
            graphics.blit(BACKGROUND, x, y, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
            graphics.fill(x + 5, y + 20, x + this.imageWidth - 5, y + 22, accent);
            graphics.fill(x + this.imageWidth / 2 - 9, y + 19, x + this.imageWidth / 2 + 9, y + 23, GOLD);
            drawMotif(graphics, x, y, accent, this.menu.kind());
            renderLinearStation(graphics, x, y, accent);
        }

        renderSlotFrames(graphics, x, y, accent);
        renderGhostInputs(graphics, x, y);
    }

    private void renderLinearStation(GuiGraphics graphics, int x, int y, int accent) {
        graphics.fill(x + 112, y + 50, x + 135, y + 52, SLOT_BORDER);
        graphics.fill(x + 132, y + 47, x + 135, y + 55, accent);
        graphics.fill(x + 135, y + 49, x + 141, y + 53, accent);
    }

    private void renderApexPanel(GuiGraphics graphics, int x, int y, int accent) {
        // Purpose-built Apex chrome: no stretched 194x188 base texture and no return to the old circular ritual UI.
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF090B0F);
        graphics.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, 0xFF14171C);
        graphics.fill(x + 5, y + 20, x + this.imageWidth - 5, y + 22, accent);
        graphics.fill(x + this.imageWidth / 2 - 13, y + 19, x + this.imageWidth / 2 + 13, y + 23, GOLD);
        graphics.fill(x + 8, y + 28, x + this.imageWidth - 8, y + 132, 0xFF0D1015);
        graphics.fill(x + 9, y + 29, x + this.imageWidth - 9, y + 131, 0xFF171B21);
        graphics.fill(x + 76, y + 34, x + 77, y + 126, 0xFF4A4130);

        graphics.drawString(this.font, Component.translatable("container.apothic_ascension.apex.inputs"), x + 16, y + 32, MUTED, false);
        graphics.drawString(this.font, Component.translatable("container.apothic_ascension.apex.reforge_choices"), x + 89, y + 32, GOLD, false);

        // Supplied authored models are used as subdued structure schematics rather than decorative replacements.
        RenderSystem.setShaderColor(0.70F, 0.72F, 0.76F, 0.24F);
        graphics.blit(APEX_PYLON_PREVIEW, x + 55, y + 45, 0, 0, 24, 59, 24, 59);
        graphics.blit(APEX_BENCH_PREVIEW, x + 194, y + 76, 0, 0, 64, 61, 64, 61);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Five bounded quality/cost cards, corresponding directly to virtual synced offer slots.
        for (int i = 0; i < AscensionWorkstationMenu.APEX_OFFER_COUNT; i++) {
            Slot offer = this.menu.getSlot(this.menu.apexOfferSlot(i));
            int ox = x + offer.x - 6;
            int oy = y + offer.y - 7;
            int strength = 0x22 + i * 0x0A;
            int cardBorder = withAlpha(i >= 3 ? GOLD : accent, 105 + i * 24);
            graphics.fill(ox, oy, ox + 28, oy + 49, 0xE6090B0F);
            graphics.fill(ox + 1, oy + 1, ox + 27, oy + 48, cardBorder);
            graphics.fill(ox + 2, oy + 2, ox + 26, oy + 47, 0xFF12161C | (strength << 24));
            graphics.drawCenteredString(this.font, OFFER_LABELS[i], ox + 14, oy + 31, i >= 3 ? GOLD : MUTED);
        }

        float charge = Math.min(1.0F, this.menu.apexProgress() / (float) this.menu.apexDuration());
        float fade = Math.min(1.0F, this.menu.apexFadeTicks() / 6.0F);
        float glow = Math.max(charge, fade);
        int barX = x + 86;
        int barY = y + 121;
        int barW = 98;
        graphics.fill(barX, barY, barX + barW, barY + 5, 0xFF080A0D);
        graphics.fill(barX + 1, barY + 1, barX + barW - 1, barY + 4, 0xFF292E35);
        int filled = Math.round((barW - 2) * charge);
        if (filled > 0) graphics.fill(barX + 1, barY + 1, barX + 1 + filled, barY + 4, withAlpha(GOLD, 180 + Math.round(75 * glow)));

        Component status;
        int statusColor;
        if (this.menu.apexProcessing()) {
            int percent = Math.max(1, Math.min(100, Math.round(charge * 100.0F)));
            status = Component.translatable("container.apothic_ascension.apex.processing", percent);
            statusColor = 0xFFE7CA7A;
        }
        else if (this.menu.getSlot(AscensionWorkstationMenu.RESULT_SLOT).hasItem() || this.menu.apexFadeTicks() > 0) {
            status = Component.translatable("container.apothic_ascension.apex.complete");
            statusColor = 0xFFC7F0AE;
        }
        else if (this.menu.hasApexOffers()) {
            status = Component.translatable("container.apothic_ascension.apex.choose_reforge");
            statusColor = 0xFFE2CF91;
        }
        else {
            status = Component.translatable("container.apothic_ascension.apex.ready");
            statusColor = 0xFFBFE8A4;
        }
        graphics.drawCenteredString(this.font, status, x + 135, y + 109, statusColor);
    }

    private void updateApexButtons() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        ItemStack material = this.menu.getSlot(1).getItem();
        ItemStack sigils = this.menu.getSlot(2).getItem();
        for (int i = 0; i < this.apexChoiceButtons.size(); i++) {
            ApexChoiceButton button = this.apexChoiceButtons.get(i);
            ItemStack offer = this.menu.getSlot(this.menu.apexOfferSlot(i)).getItem();
            ApexReforgeOffers.Cost cost = ApexReforgeOffers.cost(material, i);
            boolean affordable = cost.valid() && ApexReforgeOffers.canAfford(this.minecraft.player, material, sigils, i);
            button.active = !this.menu.apexProcessing() && !offer.isEmpty() && affordable;
            if (cost.valid()) {
                button.setTooltip(Tooltip.create(Component.translatable(
                    "container.apothic_ascension.apex.offer.tooltip",
                    ApexReforgeOffers.SAMPLE_BUDGETS[i], cost.materials(), cost.sigils(), cost.levels())));
            }
            else {
                button.setTooltip(Tooltip.create(Component.translatable("container.apothic_ascension.apex.offer.unavailable")));
            }
        }
    }

    private void renderSlotFrames(GuiGraphics graphics, int x, int y, int accent) {
        int end = this.menu.kind() == WorkstationKind.APEX_ASCENSION_BENCH
            ? AscensionWorkstationMenu.APEX_OFFER_START + AscensionWorkstationMenu.APEX_OFFER_COUNT
            : AscensionWorkstationMenu.RESULT_SLOT + 1;
        for (int i = 0; i < end; i++) {
            Slot slot = this.menu.getSlot(i);
            boolean output = i == AscensionWorkstationMenu.RESULT_SLOT;
            boolean offer = i >= AscensionWorkstationMenu.APEX_OFFER_START;
            slotFrame(graphics, x + slot.x - 1, y + slot.y - 1, output, offer, accent);
        }
    }

    private static void drawMotif(GuiGraphics graphics, int x, int y, int accent, WorkstationKind kind) {
        int cx = x + 97;
        int cy = y + 32;
        graphics.fill(cx - 18, cy, cx - 4, cy + 1, 0xFF4D432E);
        graphics.fill(cx + 4, cy, cx + 18, cy + 1, 0xFF4D432E);
        switch (kind) {
            case ASCENSION_FORGE -> {
                graphics.fill(cx - 2, cy - 4, cx, cy + 4, accent);
                graphics.fill(cx, cy - 2, cx + 5, cy, accent);
            }
            case GEM_RESONATOR -> {
                graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0x55200030 | (accent & 0x00FFFFFF));
                graphics.fill(cx - 1, cy - 5, cx + 1, cy + 5, accent);
            }
            case AFFIX_LOOM -> {
                for (int dx = -4; dx <= 4; dx += 2) graphics.fill(cx + dx, cy - 4, cx + dx + 1, cy + 5, accent);
            }
            case APEX_ASCENSION_BENCH -> { }
        }
    }

    private void renderGhostInputs(GuiGraphics graphics, int x, int y) {
        boolean apex = this.menu.kind() == WorkstationKind.APEX_ASCENSION_BENCH;
        for (int i = 0; i < AscensionWorkstationMenu.INPUT_SLOTS; i++) {
            Slot slot = this.menu.getSlot(i);
            if (slot.hasItem()) continue;
            ItemStack ghost = ghostStack(i);
            if (ghost.isEmpty()) continue;
            int gx = x + slot.x;
            int gy = y + slot.y;
            float alpha = apex ? 0.15F : 0.30F;
            float gray = apex ? 0.58F : 0.82F;
            RenderSystem.setShaderColor(gray, gray, gray, alpha);
            graphics.renderItem(ghost, gx, gy);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.fill(gx, gy, gx + 16, gy + 16, apex ? 0x66191D22 : 0x442A3038);
            int tick = apex ? 0x667F8792 : 0x887F8792;
            graphics.fill(gx, gy, gx + 3, gy + 1, tick);
            graphics.fill(gx, gy, gx + 1, gy + 3, tick);
            graphics.fill(gx + 13, gy + 15, gx + 16, gy + 16, tick);
            graphics.fill(gx + 15, gy + 13, gx + 16, gy + 16, tick);
        }
    }

    private ItemStack ghostStack(int slot) {
        return switch (this.menu.kind()) {
            case ASCENSION_FORGE -> switch (slot) {
                case 0 -> ModItems.LEGENDARY_MATERIAL.get().getDefaultInstance();
                case 1 -> ModItems.ASCENSION_CORE.get().getDefaultInstance();
                case 2 -> ModItems.STABILITY_THREAD.get().getDefaultInstance();
                default -> ItemStack.EMPTY;
            };
            case GEM_RESONATOR -> switch (slot) {
                case 0 -> new ItemStack(Items.AMETHYST_SHARD);
                case 1 -> ModItems.LEGENDARY_MATERIAL.get().getDefaultInstance();
                case 2 -> ModItems.GEM_ASCENSION_MATRIX.get().getDefaultInstance();
                default -> ItemStack.EMPTY;
            };
            case AFFIX_LOOM -> switch (slot) {
                case 0 -> ModItems.STABILITY_THREAD.get().getDefaultInstance();
                case 1 -> ModItems.EVOLUTION_CATALYST.get().getDefaultInstance();
                case 2 -> ModItems.ASCENSION_CORE.get().getDefaultInstance();
                default -> ItemStack.EMPTY;
            };
            case APEX_ASCENSION_BENCH -> switch (slot) {
                case 0 -> new ItemStack(Items.NETHERITE_CHESTPLATE);
                case 1 -> ModItems.TRANSCENDENT_MATERIAL.get().getDefaultInstance();
                case 2 -> Apoth.Items.SIGIL_OF_REBIRTH.value().getDefaultInstance();
                default -> ItemStack.EMPTY;
            };
        };
    }

    private void renderGhostTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < AscensionWorkstationMenu.INPUT_SLOTS; i++) {
            Slot slot = this.menu.getSlot(i);
            if (slot.hasItem()) continue;
            int sx = this.leftPos + slot.x;
            int sy = this.topPos + slot.y;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                graphics.renderTooltip(this.font,
                    Component.translatable("container.apothic_ascension." + this.menu.kind().key() + ".slot." + i),
                    mouseX, mouseY);
                return;
            }
        }
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y, boolean output, boolean offer, int accent) {
        int border = output ? GOLD : offer ? withAlpha(accent, 210) : SLOT_BORDER;
        graphics.fill(x, y, x + 20, y + 20, 0xFF090B0F);
        graphics.fill(x + 1, y + 1, x + 19, y + 19, border);
        graphics.fill(x + 2, y + 2, x + 18, y + 18, SLOT_INNER);
        if (output) {
            graphics.fill(x - 2, y - 2, x + 22, y, GOLD);
            graphics.fill(x - 2, y + 20, x + 22, y + 22, GOLD);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(this.font, this.title, this.imageWidth / 2, this.titleLabelY, GOLD);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MUTED, false);
    }

    private static final class ApexChoiceButton extends Button {
        private final int choice;

        private ApexChoiceButton(int x, int y, int choice, OnPress press) {
            super(x, y, 26, 12, Component.literal(OFFER_LABELS[choice]), press, DEFAULT_NARRATION);
            this.choice = choice;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int border;
            if (!this.active) border = 0xFF3B3D41;
            else if (this.isHoveredOrFocused()) border = GOLD;
            else border = this.choice >= 3 ? 0xFFC5A95E : 0xFF766A4C;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xEE090B0F);
            graphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.getWidth() - 1, this.getY() + this.getHeight() - 1, border);
            graphics.fill(this.getX() + 2, this.getY() + 2, this.getX() + this.getWidth() - 2, this.getY() + this.getHeight() - 2, 0xFF171A20);
            graphics.drawCenteredString(MinecraftHolder.font(), this.getMessage(), this.getX() + this.getWidth() / 2, this.getY() + 2,
                this.active ? 0xFFE8D9AE : 0xFF666970);
        }
    }

    /** Avoids capturing the outer screen in the static button class while keeping the widget allocation small. */
    private static final class MinecraftHolder {
        private static net.minecraft.client.gui.Font font() {
            return net.minecraft.client.Minecraft.getInstance().font;
        }
    }

    private static final class FocusButton extends Button {
        private final int family;
        private final BooleanSupplier selected;

        private FocusButton(int x, int y, int family, BooleanSupplier selected, OnPress press) {
            super(x, y, 22, 22, Component.translatable("item.apothic_ascension." + familyKey(family) + "_focus_sigil"), press, DEFAULT_NARRATION);
            this.family = family;
            this.selected = selected;
            this.setTooltip(Tooltip.create(this.getMessage()));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean chosen = this.selected.getAsBoolean();
            int border = chosen ? GOLD : (this.isHoveredOrFocused() ? 0xFFC5A95E : 0xFF665838);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xEE0B0E12);
            graphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.getWidth() - 1, this.getY() + this.getHeight() - 1, border);
            graphics.fill(this.getX() + 2, this.getY() + 2, this.getX() + this.getWidth() - 2, this.getY() + this.getHeight() - 2, 0xFF171A20);
            graphics.renderItem(ModItems.focusSigil(this.family).get().getDefaultInstance(), this.getX() + 3, this.getY() + 3);
        }

        private static String familyKey(int family) {
            return switch (family) {
                case 0 -> "attack";
                case 1 -> "defense";
                case 2 -> "agility";
                case 3 -> "sustain";
                case 4 -> "utility";
                default -> "attack";
            };
        }
    }
}
