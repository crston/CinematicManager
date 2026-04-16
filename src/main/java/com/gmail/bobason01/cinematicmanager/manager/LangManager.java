package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

public class LangManager {

    private final CinematicManager plugin;
    private final Map<LangKey, String> langCache = new EnumMap<>(LangKey.class);
    private String prefix;

    public LangManager(CinematicManager plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        langCache.clear();
        String langType = plugin.getConfig().getString("language", "ko");
        File langFile = new File(plugin.getDataFolder(), "language" + File.separator + langType + ".yml");

        if (!langFile.exists()) {
            plugin.saveResource("language/ko.yml", false);
            plugin.saveResource("language/en.yml", false);
            langFile = new File(plugin.getDataFolder(), "language" + File.separator + langType + ".yml");
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(langFile);
        this.prefix = color(config.getString("prefix", "&6&lCinematic &8| &f"));

        for (LangKey key : LangKey.values()) {
            if (key == LangKey.PREFIX) continue;
            String path = key.getPath();

            if (config.isList(path)) {
                langCache.put(key, config.getStringList(path).stream()
                        .map(this::color)
                        .collect(Collectors.joining("\n")));
            } else {
                String val = config.getString(path);
                langCache.put(key, val != null ? color(val) : "§cMissing: " + path);
            }
        }
    }

    public String get(LangKey key) { return langCache.getOrDefault(key, "§cMissing: " + key.name()); }
    public String getPrefixed(LangKey key) { return prefix + get(key); }
    public String format(LangKey key, String... replacements) {
        String msg = get(key);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }
    public String sanitize(String text) { return text == null ? "" : ChatColor.stripColor(text).trim(); }
    private String color(String s) { return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s); }
}