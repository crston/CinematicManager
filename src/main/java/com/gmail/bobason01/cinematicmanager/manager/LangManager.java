package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
        String fileName = langType + ".yml";
        File langFile = new File(plugin.getDataFolder(), "language" + File.separator + fileName);

        // 1. 내부 리소스(jar 파일 안)에서 기본 파일 로드 (누락된 키 대조용)
        YamlConfiguration defaultConfig = null;
        InputStream defStream = plugin.getResource("language/" + fileName);
        if (defStream != null) {
            defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
        }

        // 2. 파일이 없으면 생성
        if (!langFile.exists()) {
            plugin.saveResource("language/" + fileName, false);
        }

        // 3. 현재 파일 로드
        YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);

        // 4. 자동 업데이트 로직: 내부 리소스에는 있는데 현재 파일에는 없는 키가 있다면 추가
        if (defaultConfig != null) {
            boolean changed = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defaultConfig.get(key));
                    changed = true;
                }
            }
            // 변경 사항이 있다면 파일에 다시 저장 (주석은 유지되지 않으니 주의)
            if (changed) {
                try {
                    config.save(langFile);
                    plugin.getLogger().info("Updated " + fileName + " with missing language keys.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // 5. 캐시 갱신
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

    public String get(LangKey key) {
        return langCache.getOrDefault(key, "§cMissing: " + key.name());
    }

    public String getPrefixed(LangKey key) {
        return prefix + get(key);
    }

    public String format(LangKey key, String... replacements) {
        String msg = get(key);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    public String sanitize(String text) {
        return text == null ? "" : ChatColor.stripColor(text).trim();
    }

    private String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}