package me.jellysquid.mods.sodium.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import me.jellysquid.mods.sodium.client.gui.options.*;
import me.jellysquid.mods.sodium.client.gui.options.control.Control;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlElement;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import me.jellysquid.mods.sodium.client.gui.widgets.FlatButtonWidget;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

public class SodiumOptionsGUI extends Screen {
    private final List<OptionPage> pages = new ArrayList<>();

    private final List<ControlElement<?>> controls = new ArrayList<>();
    private final List<Widget> widgets = new ArrayList<>();

    private final Screen prevScreen;

    private OptionPage currentPage;

    private FlatButtonWidget applyButton, closeButton, undoButton;
    private boolean hasPendingChanges;
    private ControlElement<?> hoveredElement;

    public SodiumOptionsGUI(Screen prevScreen) {
        super(new TranslatableComponent("Sodium Options"));

        this.prevScreen = prevScreen;

        this.pages.add(SodiumGameOptionPages.general());
        this.pages.add(SodiumGameOptionPages.quality());
        this.pages.add(SodiumGameOptionPages.advanced());
    }

    public void setPage(OptionPage page) {
        this.currentPage = page;

        this.rebuildGUI();
    }

    @Override
    protected void init() {
        super.init();

        this.rebuildGUI();
    }

    private void rebuildGUI() {
        this.controls.clear();
        this.children.clear();
        this.widgets.clear();

        if (this.currentPage == null) {
            if (this.pages.isEmpty()) {
                throw new IllegalStateException("No pages are available?!");
            }

            // Just use the first page for now
            this.currentPage = this.pages.get(0);
        }

        this.rebuildGUIPages();
        this.rebuildGUIOptions();

        this.undoButton = new FlatButtonWidget(new Dim2i(this.width - 211, this.height - 26, 65, 20), I18n.get("sodium.options.buttons.undo"), this::undoChanges);
        this.applyButton = new FlatButtonWidget(new Dim2i(this.width - 142, this.height - 26, 65, 20), I18n.get("sodium.options.buttons.apply"), this::applyChanges);
        this.closeButton = new FlatButtonWidget(new Dim2i(this.width - 73, this.height - 26, 65, 20), I18n.get("sodium.options.buttons.close"), this::onClose);

        this.children.add(this.undoButton);
        this.children.add(this.applyButton);
        this.children.add(this.closeButton);

        for (GuiEventListener element : this.children) {
            if (element instanceof Widget) {
                this.widgets.add((Widget) element);
            }
        }
    }

    private void rebuildGUIPages() {
        int x = 6;
        int y = 6;

        for (OptionPage page : this.pages) {
            int width = 12 + this.font.width(page.getName());

            FlatButtonWidget button = new FlatButtonWidget(new Dim2i(x, y, width, 18), page.getName(), () -> this.setPage(page));
            button.setSelected(this.currentPage == page);

            x += width + 6;

            this.children.add(button);
        }
    }

    private void rebuildGUIOptions() {
        int x = 6;
        int y = 28;

        for (OptionGroup group : this.currentPage.getGroups()) {
            // Add each option's control element
            for (Option<?> option : group.getOptions()) {
                Control<?> control = option.getControl();
                ControlElement<?> element = control.createElement(new Dim2i(x, y, 200, 18));

                this.controls.add(element);
                this.children.add(element);

                // Move down to the next option
                y += 18;
            }

            // Add padding beneath each option group
            y += 4;
        }
    }

    @Override
    public void render(PoseStack matrixStack, int mouseX, int mouseY, float delta) {
        super.renderBackground(matrixStack);

        this.updateControls();

        for (Widget widget : this.widgets) {
            widget.render(matrixStack, mouseX, mouseY, delta);
        }

        if (this.hoveredElement != null) {
            this.renderOptionTooltip(matrixStack, this.hoveredElement);
        }
    }

    private void updateControls() {
        ControlElement<?> hovered = this.getActiveControls()
                .filter(ControlElement::isHovered)
                .findFirst()
                .orElse(null);

        boolean hasChanges = this.getAllOptions()
                .anyMatch(Option::hasChanged);

        for (OptionPage page : this.pages) {
            for (Option<?> option : page.getOptions()) {
                if (option.hasChanged()) {
                    hasChanges = true;
                }
            }
        }

        this.applyButton.setEnabled(hasChanges);
        this.undoButton.setVisible(hasChanges);
        this.closeButton.setEnabled(!hasChanges);

        this.hasPendingChanges = hasChanges;
        this.hoveredElement = hovered;
    }

    private Stream<Option<?>> getAllOptions() {
        return this.pages.stream()
                .flatMap(s -> s.getOptions().stream());
    }

    private Stream<ControlElement<?>> getActiveControls() {
        return this.controls.stream();
    }

    private void renderOptionTooltip(PoseStack matrixStack, ControlElement<?> element) {
        Dim2i dim = element.getDimensions();

        int textPadding = 3;
        int boxPadding = 3;

        int boxWidth = 200;
        int textWidth = boxWidth - (textPadding * 2);

        int boxY = dim.getOriginY();
        int boxX = dim.getLimitX() + boxPadding;

        Option<?> option = element.getOption();

        FormattedText title = new TextComponent(option.getName()).withStyle(ChatFormatting.GRAY);

        List<FormattedCharSequence> tooltip = new ArrayList<>(this.font.split(option.getTooltip(), boxWidth - (textPadding * 2)));
        OptionImpact impact = option.getImpact();

        if (impact != null) {
            tooltip.add(Language.getInstance().getVisualOrder(new TextComponent(ChatFormatting.GRAY + "Performance Impact: " + impact.toDisplayString())));
        }
        int boxHeight = (tooltip.size() * 12) + boxPadding;
        int boxYLimit = boxY + boxHeight;
        int boxYCutoff = this.height - 40;
        // If the box is going to be cutoff on the Y-axis, move it back up the difference
        if (boxYLimit > boxYCutoff) {
            boxY -= boxYLimit - boxYCutoff;
        }
        this.fillGradient(matrixStack, boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE0000000, 0xE0000000);

        for (int i = 0; i < tooltip.size(); i++) {
            this.font.draw(matrixStack, tooltip.get(i), boxX + textPadding, boxY + textPadding + (i * 12), 0xFFFFFFFF);
        }
    }

    private void applyChanges() {
        final HashSet<OptionStorage<?>> dirtyStorages = new HashSet<>();
        final EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);

        this.getAllOptions().forEach((option -> {
            if (!option.hasChanged()) {
                return;
            }

            option.applyChanges();

            flags.addAll(option.getFlags());
            dirtyStorages.add(option.getStorage());
        }));

        Minecraft client = Minecraft.getInstance();

        if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
            client.levelRenderer.allChanged();
        }

        if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
            client.updateMaxMipLevel(client.options.mipmapLevels);
            client.delayTextureReload();
        }

        for (OptionStorage<?> storage : dirtyStorages) {
            storage.save();
        }
    }

    private void undoChanges() {
        this.getAllOptions()
                .forEach(Option::reset);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_P && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            Minecraft.getInstance().setScreen(new VideoSettingsScreen(this.prevScreen, Minecraft.getInstance().options));

            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.hasPendingChanges;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.prevScreen);
    }
}
