package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import org.bukkit.ChatColor;
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
        String langType = plugin.getConfig().getString("language", "en");
        String fileName = langType + ".yml";

        File langFolder = new File(plugin.getDataFolder(), "language");
        if (!langFolder.exists()) langFolder.mkdirs();

        File langFile = new File(langFolder, fileName);

        // 1. 내부 리소스 로드 (기본값 비교용)
        YamlConfiguration defaultConfig = null;
        try (InputStream defStream = plugin.getResource("language/" + fileName)) {
            if (defStream != null) {
                defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not find default language resource: " + fileName);
        }

        // 2. 파일이 없으면 생성
        if (!langFile.exists()) {
            plugin.saveResource("language/" + fileName, false);
        }

        // 3. 현재 파일 로드
        YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);

        // 4. 자동 업데이트 로직 (누락된 키 자동 추가)
        if (defaultConfig != null) {
            boolean changed = false;
            for (String key : defaultConfig.getKeys(true)) {
                // 섹션이 아닌 실제 데이터 포인트만 체크
                if (!defaultConfig.isConfigurationSection(key) && !config.contains(key)) {
                    config.set(key, defaultConfig.get(key));
                    changed = true;
                }
            }
            if (changed) {
                try {
                    config.save(langFile);
                    plugin.getLogger().info("Updated " + fileName + " with missing language keys.");
                } catch (Exception e) {
                    plugin.getLogger().severe("Could not save updated language file: " + e.getMessage());
                }
            }
        }

        // 5. 캐시 갱신 (이미지의 MemorySection 방지 로직 포함)
        this.prefix = color(config.getString("prefix", "&6&lCinematic &8| &f"));

        for (LangKey key : LangKey.values()) {
            if (key == LangKey.PREFIX) continue;
            String path = key.getPath();

            // 리스트 형태(Lore 등) 처리
            if (config.isList(path)) {
                langCache.put(key, config.getStringList(path).stream()
                        .map(this::color)
                        .collect(Collectors.joining("\n")));
            }
            // 문자열 형태 처리
            else if (config.isString(path)) {
                langCache.put(key, color(config.getString(path)));
            }
            // 경로가 없거나 섹션일 경우 (MemorySection 출력 방지)
            else {
                langCache.put(key, "§c[Missing: " + path + "]");
            }
        }
    }

    public String get(LangKey key) {
        return langCache.getOrDefault(key, "§c[Error: " + key.name() + "]");
    }

    public String getPrefixed(LangKey key) {
        return prefix + get(key);
    }

    public String format(LangKey key, String... replacements) {
        String msg = get(key);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length && replacements[i] != null && replacements[i+1] != null) {
                msg = msg.replace(replacements[i], replacements[i + 1]);
            }
        }
        return msg;
    }

    /**
     * GUI 타이틀이나 아이템 이름을 위해 색상 코드를 제거한 순수 텍스트 반환
     */
    public String sanitize(String text) {
        if (text == null) return "";
        // 앰퍼샌드(&)를 섹션 기호(§)로 바꾼 후, 모든 색상 코드를 제거
        return ChatColor.stripColor(color(text)).trim();
    }

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}