package me.jellysquid.mods.sodium.client.gui.options;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;

public enum OptionImpact {
    LOW(ChatFormatting.GREEN, "sodium.option_impact.low"),
    MEDIUM(ChatFormatting.YELLOW, "sodium.option_impact.medium"),
    HIGH(ChatFormatting.GOLD, "sodium.option_impact.high"),
    EXTREME(ChatFormatting.RED, "sodium.option_impact.extreme"),
    VARIES(ChatFormatting.WHITE, "sodium.option_impact.varies");

    private final ChatFormatting color;
    private final String text;

    OptionImpact(ChatFormatting color, String text) {
        this.color = color;
        this.text = text;
    }

    public String toDisplayString() {
        return this.color + I18n.get(this.text);
    }
}
