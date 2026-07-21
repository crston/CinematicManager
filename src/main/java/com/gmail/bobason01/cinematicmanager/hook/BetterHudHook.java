package com.gmail.bobason01.cinematicmanager.hook;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import kr.toxicity.hud.api.BetterHudAPI;
import kr.toxicity.hud.api.bukkit.event.CustomPopupEvent;
import kr.toxicity.hud.api.bukkit.event.PluginReloadedEvent;
import kr.toxicity.hud.api.bukkit.update.BukkitEventUpdateEvent;
import kr.toxicity.hud.api.placeholder.HudPlaceholder;
import kr.toxicity.hud.api.player.HudPlayer;
import kr.toxicity.hud.api.popup.Popup;
import kr.toxicity.hud.api.popup.PopupUpdater;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BetterHud popup 연동 (핫패스 최소화: vars 재사용, wrap 캐시, update-only).
 */
public class BetterHudHook implements Listener {

    public static final String POPUP_NAME = "cinematic_dialogue";
    public static final String EVENT_NAME = "cinematic_dialogue";
    public static final String PLACEHOLDER = "cinematic";

    private final CinematicManager plugin;
    private final boolean enabled;
    private final Map<UUID, PopupUpdater> active = new ConcurrentHashMap<>();
    /** 플레이어별 재사용 vars (타이핑마다 CHM/HashMap 생성 금지) */
    private final Map<UUID, Map<String, String>> dialogueVars = new ConcurrentHashMap<>();
    private boolean placeholderRegistered = false;
    private volatile Popup cachedPopup;

    private boolean wrapEnabled = true;
    private int wrapMaxWidth = 24;
    private int wrapMaxLines = 4;

    private final StringBuilder wrapOut = new StringBuilder(128);
    private final StringBuilder wrapLine = new StringBuilder(64);

    public BetterHudHook(CinematicManager plugin) {
        this.plugin = plugin;
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("BetterHud");
        if (enabled) {
            refreshWrapConfig();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            exportConfigs();
            Bukkit.getScheduler().runTask(plugin, this::registerPlaceholder);
            plugin.getLogger().info("BetterHud hooked. Dialogue popup='" + POPUP_NAME + "'");
        } else {
            plugin.getLogger().info("BetterHud not found. Dialogue falls back to bossbar text.");
        }
    }

    @EventHandler
    public void onBetterHudReloaded(PluginReloadedEvent event) {
        placeholderRegistered = false;
        cachedPopup = null;
        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshWrapConfig();
            exportConfigs();
            registerPlaceholder();
        });
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void refreshWrapConfig() {
        wrapEnabled = plugin.getConfig().getBoolean("dialogue.wrap.enabled", true);
        wrapMaxWidth = Math.max(8, plugin.getConfig().getInt("dialogue.wrap.max-width", 24));
        wrapMaxLines = Math.max(1, plugin.getConfig().getInt("dialogue.wrap.max-lines", 4));
    }

    private void registerPlaceholder() {
        if (!enabled) return;
        try {
            BetterHudAPI.inst().getPlaceholderManager().getStringContainer().addPlaceholder(
                    PLACEHOLDER,
                    HudPlaceholder.<String>builder()
                            .requiredArgsLength(1)
                            .function((args, reason) -> {
                                String key = args.get(0);
                                return hudPlayer -> {
                                    Map<String, String> vars = dialogueVars.get(hudPlayer.uuid());
                                    if (vars == null) return " ";
                                    String value = vars.get(key);
                                    return (value == null || value.isEmpty()) ? " " : value;
                                };
                            })
                            .build()
            );
            placeholderRegistered = true;
            plugin.getLogger().info("Registered BetterHud placeholder [" + PLACEHOLDER + ":key]");
        } catch (Throwable t) {
            if (!placeholderRegistered) {
                plugin.getLogger().warning("Failed to register BetterHud placeholder: " + t.getMessage());
            }
        }
    }

    private Popup resolvePopup() {
        Popup p = cachedPopup;
        if (p != null) return p;
        p = BetterHudAPI.inst().getPopupManager().getPopup(POPUP_NAME);
        cachedPopup = p;
        return p;
    }

    public void showDialogue(Player player, String speaker, String line, String hint) {
        if (!enabled || player == null) return;
        try {
            UUID uuid = player.getUniqueId();
            HudPlayer hudPlayer = BetterHudAPI.inst().getPlayerManager().getHudPlayer(uuid);
            if (hudPlayer == null) return;

            Popup popup = resolvePopup();
            if (popup == null) {
                plugin.getLogger().warning("BetterHud popup '" + POPUP_NAME + "' missing. Run /betterhud reload.");
                return;
            }

            Map<String, String> vars = dialogueVars.computeIfAbsent(uuid, u -> new HashMap<>(8));

            String body = buildBody(speaker, line);
            String display = wrapText(body);
            String[] lines = splitDisplayLines(display, 4);
            String l1 = padToWidth(lines[0], wrapMaxWidth);
            String l2 = blankAsSpace(lines[1]);
            String l3 = blankAsSpace(lines[2]);
            String l4 = blankAsSpace(lines[3]);

            boolean changed = putIfChanged(vars, "l1", l1)
                    | putIfChanged(vars, "l2", l2)
                    | putIfChanged(vars, "l3", l3)
                    | putIfChanged(vars, "l4", l4);

            // variableMap도 변경분만 (타이핑 중 putAll 폭주 방지)
            if (changed) {
                Map<String, String> vm = hudPlayer.getVariableMap();
                vm.put("l1", l1);
                vm.put("l2", l2);
                vm.put("l3", l3);
                vm.put("l4", l4);
            }

            PopupUpdater previous = active.get(uuid);
            if (previous != null) {
                if (changed) {
                    try {
                        previous.update();
                    } catch (Throwable ignored) {
                        active.remove(uuid);
                        // fall through → re-show
                    }
                    if (active.containsKey(uuid)) return;
                } else {
                    return;
                }
            }

            CustomPopupEvent event = new CustomPopupEvent(player, EVENT_NAME);
            event.getVariables().putAll(vars);
            Bukkit.getPluginManager().callEvent(event);

            PopupUpdater updater = popup.show(new BukkitEventUpdateEvent(event, popupKey(uuid)), hudPlayer);
            if (updater != null) {
                active.put(uuid, updater);
                updater.update();
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("BetterHud dialogue show failed: " + t.getMessage());
        }
    }

    private static boolean putIfChanged(Map<String, String> map, String key, String value) {
        String prev = map.put(key, value);
        return !Objects.equals(prev, value);
    }

    public void hideDialogue(Player player) {
        if (!enabled || player == null) return;
        try {
            UUID uuid = player.getUniqueId();
            dialogueVars.remove(uuid);
            PopupUpdater updater = active.remove(uuid);
            if (updater != null) updater.remove();
            HudPlayer hudPlayer = BetterHudAPI.inst().getPlayerManager().getHudPlayer(uuid);
            if (hudPlayer != null) {
                Map<String, String> vm = hudPlayer.getVariableMap();
                vm.remove("l1");
                vm.remove("l2");
                vm.remove("l3");
                vm.remove("l4");
                Popup popup = resolvePopup();
                if (popup != null) popup.hide(hudPlayer);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("BetterHud dialogue hide failed: " + t.getMessage());
        }
    }

    private Object popupKey(UUID uuid) {
        return "cinematic_dialogue:" + uuid;
    }

    private String buildBody(String speaker, String line) {
        String s = speaker == null ? "" : speaker.trim();
        String l = line == null ? "" : line.trim();
        if (!s.isEmpty() && !l.isEmpty()) return s + "\n" + l;
        if (!l.isEmpty()) return l;
        if (!s.isEmpty()) return s;
        return " ";
    }

    private String wrapText(String text) {
        if (text == null || text.isEmpty()) return " ";
        if (!wrapEnabled) return text;

        wrapOut.setLength(0);
        int linesUsed = 0;
        int start = 0;
        int len = text.length();
        while (start <= len && linesUsed < wrapMaxLines) {
            int nl = text.indexOf('\n', start);
            if (nl < 0) nl = len;
            String paragraph = text.substring(start, nl);
            String wrapped = wrapParagraph(paragraph, wrapMaxWidth, wrapMaxLines - linesUsed);
            if (wrapOut.length() > 0) wrapOut.append('\n');
            wrapOut.append(wrapped);
            linesUsed += countLines(wrapped);
            if (nl == len) break;
            start = nl + 1;
        }
        return wrapOut.length() == 0 ? " " : wrapOut.toString();
    }

    private String wrapParagraph(String paragraph, int maxWidth, int maxLines) {
        if (paragraph == null || paragraph.isEmpty()) return " ";
        if (maxLines <= 0) return "";

        wrapLine.setLength(0);
        StringBuilder out = new StringBuilder(paragraph.length() + 8);
        int width = 0;
        int lines = 1;

        for (int i = 0; i < paragraph.length(); ) {
            int cp = paragraph.codePointAt(i);
            i += Character.charCount(cp);
            int w = visualWidth(cp);
            if (cp == ' ' && width == 0) continue;

            if (width + w > maxWidth && width > 0) {
                if (lines >= maxLines) break;
                if (out.length() > 0) out.append('\n');
                out.append(wrapLine);
                wrapLine.setLength(0);
                width = 0;
                lines++;
                if (cp == ' ') continue;
            }
            wrapLine.appendCodePoint(cp);
            width += w;
        }
        if (wrapLine.length() > 0) {
            if (out.length() > 0) out.append('\n');
            out.append(wrapLine);
        }
        return out.length() == 0 ? " " : out.toString();
    }

    /** ASCII=1, 그 외(한글 등)=2 — UnicodeScript.of 호출 회피 */
    private static int visualWidth(int codePoint) {
        return codePoint <= 0x7F ? 1 : 2;
    }

    private static int countLines(String text) {
        int n = 1;
        for (int i = 0, len = text.length(); i < len; i++) {
            if (text.charAt(i) == '\n') n++;
        }
        return n;
    }

    private static String[] splitDisplayLines(String display, int max) {
        String[] out = new String[max];
        for (int i = 0; i < max; i++) out[i] = " ";
        if (display == null || display.isEmpty()) return out;
        int idx = 0;
        int start = 0;
        int len = display.length();
        while (idx < max && start <= len) {
            int nl = display.indexOf('\n', start);
            if (nl < 0) nl = len;
            if (nl > start) out[idx] = display.substring(start, nl);
            idx++;
            if (nl == len) break;
            start = nl + 1;
        }
        return out;
    }

    private static String blankAsSpace(String s) {
        return (s == null || s.isEmpty() || s.isBlank()) ? " " : s;
    }

    private String padToWidth(String text, int maxWidth) {
        String t = blankAsSpace(text);
        int w = visualWidthOf(t);
        if (w >= maxWidth) return t;
        int need = maxWidth - w;
        int left = need >>> 1;
        int right = need - left;
        return " ".repeat(left) + t + " ".repeat(right);
    }

    private static int visualWidthOf(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            w += visualWidth(cp);
        }
        return w;
    }

    private void exportConfigs() {
        File betterHud = new File(plugin.getDataFolder().getParentFile(), "BetterHud");
        if (!betterHud.exists()) {
            plugin.getLogger().warning("BetterHud folder not found yet. Start BetterHud once, then restart.");
            return;
        }

        copyResource("betterhud/texts/cinematic-dialogue.yml",
                new File(betterHud, "texts/cinematic-dialogue.yml"), true);
        copyResource("betterhud/layouts/cinematic-dialogue.yml",
                new File(betterHud, "layouts/cinematic-dialogue.yml"), true);
        copyResource("betterhud/popups/cinematic-dialogue.yml",
                new File(betterHud, "popups/cinematic-dialogue.yml"), true);

        copyResource("betterhud/backgrounds/cinematic_box.yml",
                new File(betterHud, "backgrounds/cinematic_box.yml"), true);
        File bgDir = new File(betterHud, "backgrounds/cinematic_box");
        copyResource("betterhud/backgrounds/cinematic_box/left.png", new File(bgDir, "left.png"), true);
        copyResource("betterhud/backgrounds/cinematic_box/body.png", new File(bgDir, "body.png"), true);
        copyResource("betterhud/backgrounds/cinematic_box/right.png", new File(bgDir, "right.png"), true);

        File left = new File(bgDir, "left.png");
        if (!left.exists() || left.length() < 8) {
            plugin.getLogger().warning("cinematic_box background export FAILED");
        } else {
            plugin.getLogger().info("Exported BetterHud dialogue box (9-slice background). "
                    + "Run /betterhud reload + re-apply resource pack.");
        }
    }

    private void copyResource(String jarPath, File dest, boolean overwrite) {
        try {
            if (dest.exists() && !overwrite) return;
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (InputStream in = plugin.getResource(jarPath)) {
                if (in == null) {
                    plugin.getLogger().warning("Missing jar resource: " + jarPath);
                    return;
                }
                Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to copy " + jarPath + ": " + e.getMessage());
        }
    }
}
