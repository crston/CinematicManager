package com.gmail.bobason01.cinematicmanager.dialogue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One dialogue page: speaker, body text, optional choices that can chain to another cinematic.
 * Wire (value): {@code Speaker;Body>>>Label=>cine_a|Label2=>cine_b} pages joined by {@code ||}.
 */
public final class DialoguePage {
    private final String speaker;
    private final String text;
    private final List<DialogueChoice> choices;

    public DialoguePage(String speaker, String text, List<DialogueChoice> choices) {
        this.speaker = speaker == null ? "" : speaker;
        this.text = text == null ? "" : text;
        this.choices = choices == null || choices.isEmpty()
                ? List.of()
                : List.copyOf(choices);
    }

    public String speaker() { return speaker; }
    public String text() { return text; }
    public List<DialogueChoice> choices() { return choices; }
    public boolean hasChoices() { return !choices.isEmpty(); }

    public record DialogueChoice(String label, String cinematicId) {
        public DialogueChoice {
            label = label == null ? "" : label;
            cinematicId = cinematicId == null || cinematicId.isBlank() ? null : cinematicId.trim();
        }
    }

    public static List<DialoguePage> parseWire(String encoded, String pageSeparator) {
        if (encoded == null || encoded.isBlank()) {
            return List.of(new DialoguePage("", " ", List.of()));
        }
        String sep = pageSeparator == null || pageSeparator.isBlank() ? "||" : pageSeparator;
        String[] rawPages = encoded.split(java.util.regex.Pattern.quote(sep), -1);
        List<DialoguePage> out = new ArrayList<>(rawPages.length);
        for (String raw : rawPages) {
            if (raw == null || raw.isBlank()) continue;
            out.add(parseOne(raw.trim()));
        }
        if (out.isEmpty()) out.add(new DialoguePage("", " ", List.of()));
        return out;
    }

    private static DialoguePage parseOne(String raw) {
        String bodyPart = raw;
        List<DialogueChoice> choices = new ArrayList<>();
        int choiceIdx = raw.indexOf(">>>");
        if (choiceIdx >= 0) {
            bodyPart = raw.substring(0, choiceIdx);
            String choiceWire = raw.substring(choiceIdx + 3);
            for (String piece : choiceWire.split("\\|")) {
                if (piece == null || piece.isBlank()) continue;
                int arrow = piece.indexOf("=>");
                if (arrow < 0) {
                    choices.add(new DialogueChoice(piece.trim(), null));
                } else {
                    choices.add(new DialogueChoice(
                            piece.substring(0, arrow).trim(),
                            piece.substring(arrow + 2).trim()));
                }
            }
        }
        String speaker = "";
        String text = bodyPart;
        int semi = bodyPart.indexOf(';');
        if (semi >= 0) {
            speaker = bodyPart.substring(0, semi).trim();
            text = bodyPart.substring(semi + 1).trim();
            if (text.isEmpty()) {
                text = speaker;
                speaker = "";
            }
        }
        return new DialoguePage(speaker, text.isEmpty() ? " " : text, choices);
    }

    public static String encodeWire(List<DialoguePage> pages, String pageSeparator) {
        if (pages == null || pages.isEmpty()) return " ; ";
        String sep = pageSeparator == null || pageSeparator.isBlank() ? "||" : pageSeparator;
        List<String> parts = new ArrayList<>(pages.size());
        for (DialoguePage page : pages) {
            StringBuilder sb = new StringBuilder();
            sb.append(page.speaker()).append(';').append(page.text());
            if (page.hasChoices()) {
                sb.append(">>>");
                boolean first = true;
                for (DialogueChoice c : page.choices()) {
                    if (!first) sb.append('|');
                    first = false;
                    sb.append(c.label());
                    if (c.cinematicId() != null) sb.append("=>").append(c.cinematicId());
                }
            }
            parts.add(sb.toString());
        }
        return String.join(sep, parts);
    }

    public List<String> textLines(int maxLines) {
        String normalized = text.replace("\\n", "\n").replace("<br>", "\n");
        String[] split = normalized.split("\\R", -1);
        List<String> lines = new ArrayList<>();
        for (String s : split) {
            if (lines.size() >= maxLines) break;
            lines.add(s);
        }
        if (lines.isEmpty()) lines.add(" ");
        return Collections.unmodifiableList(lines);
    }

}
