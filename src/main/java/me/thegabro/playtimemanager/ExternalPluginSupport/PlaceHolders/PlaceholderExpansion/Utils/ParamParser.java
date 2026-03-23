package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils;

import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormatsConfiguration;

public class ParamParser {

    private final PlaytimeFormatsConfiguration formatsConfig;

    public record ParseResult(String params, PlaytimeFormat format) {}

    public ParamParser(PlaytimeFormatsConfiguration formatsConfig) {
        this.formatsConfig = formatsConfig;
    }

    public ParseResult parse(String raw) {
        int lastUnderscoreIndex = raw.lastIndexOf("_");

        if (lastUnderscoreIndex == -1) {
            int colonIndex = raw.indexOf(":");
            if (colonIndex != -1) {
                String formatName = raw.substring(colonIndex + 1);
                PlaytimeFormat format = formatsConfig.getFormat(formatName);
                if (format == null) format = formatsConfig.getFormat("default");
                return new ParseResult(raw.substring(0, colonIndex), format);
            }
            return new ParseResult(raw, formatsConfig.getFormat("default"));
        }

        int colonIndex = raw.indexOf(":", lastUnderscoreIndex);
        if (colonIndex == -1) {
            return new ParseResult(raw, formatsConfig.getFormat("default"));
        }

        String formatName = raw.substring(colonIndex + 1);
        PlaytimeFormat format = formatsConfig.getFormat(formatName);
        if (format == null) format = formatsConfig.getFormat("default");
        return new ParseResult(raw.substring(0, colonIndex), format);
    }
}