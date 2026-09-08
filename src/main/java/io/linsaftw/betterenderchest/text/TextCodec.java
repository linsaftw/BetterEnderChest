package io.linsaftw.betterenderchest.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class TextCodec {
    private static final Pattern AMP_CODE = Pattern.compile("&[0-9a-fk-orA-FK-OR]");

    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer amp;
    private final LegacyComponentSerializer section;
    private final LegacyComponentSerializer display;

    public TextCodec() {
        this.miniMessage = MiniMessage.miniMessage();
        this.amp = LegacyComponentSerializer.legacyAmpersand();
        this.section = LegacyComponentSerializer.legacySection();
        this.display = LegacyComponentSerializer.legacySection();
    }

    public Component parse(String source) {
        if (source == null || source.isEmpty()) {
            return Component.empty();
        }
        if (source.indexOf('§') >= 0) {
            return section.deserialize(source);
        }
        if (AMP_CODE.matcher(source).find()) {
            return amp.deserialize(source);
        }
        if (looksLikeMiniMessage(source)) {
            try {
                return miniMessage.deserialize(source);
            } catch (Exception e) {
                return Component.text(source);
            }
        }
        return Component.text(source);
    }

    public Component parse(String source, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return parse(source);
        }
        if (source == null) {
            return Component.empty();
        }
        String resolved = source;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : miniMessage.escapeTags(entry.getValue());
            resolved = resolved.replace("{" + entry.getKey() + "}", value);
        }
        return parse(resolved);
    }

    public String toLegacy(String source) {
        return toLegacy(parse(source));
    }

    public String toLegacy(String source, Map<String, String> placeholders) {
        return toLegacy(parse(source, placeholders));
    }

    public String toLegacy(Component component) {
        if (component == null) {
            return "";
        }
        return display.serialize(component);
    }

    public String itemName(String source, Map<String, String> placeholders) {
        Component parsed = placeholders == null ? parse(source) : parse(source, placeholders);
        return display.serialize(parsed);
    }

    public List<String> itemLore(List<String> lines, Map<String, String> placeholders) {
        if (lines == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            Component parsed = placeholders == null ? parse(line) : parse(line, placeholders);
            Component noItalic = parsed.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
            out.add(display.serialize(noItalic));
        }
        return out;
    }

    public String inventoryTitle(String template, int page, int pages) {
        int safePage = Math.max(1, page);
        int safePages = Math.max(1, pages);
        if (template == null || template.isEmpty()) {
            return " (Page " + safePage + "/" + safePages + ")";
        }
        if (template.contains("{page}") || template.contains("{pages}")) {
            Map<String, String> map = new java.util.HashMap<>();
            map.put("page", String.valueOf(safePage));
            map.put("pages", String.valueOf(safePages));
            return toLegacy(parse(template, map));
        }
        return toLegacy(template) + " (Page " + safePage + "/" + safePages + ")";
    }

    private static boolean looksLikeMiniMessage(String source) {
        int open = source.indexOf('<');
        int close = source.indexOf('>', open + 1);
        return open >= 0 && close > open + 1;
    }
}
