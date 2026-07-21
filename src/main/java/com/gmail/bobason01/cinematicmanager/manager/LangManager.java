package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LangManager {

    private final CinematicManager plugin;
    private final Map<LangKey, String> langCache = new EnumMap<>(LangKey.class);
    private final Map<LangKey, List<String>> listCache = new EnumMap<>(LangKey.class);
    private String prefix;

    public LangManager(CinematicManager plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        langCache.clear();
        listCache.clear();
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

        // 4. 자동 업데이트 로직 (누락 키 추가 + 구버전 [F] 문구 교체)
        if (defaultConfig != null) {
            boolean changed = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (defaultConfig.isConfigurationSection(key)) continue;

                if (!config.contains(key)) {
                    config.set(key, defaultConfig.get(key));
                    changed = true;
                    continue;
                }

                // 서버에 남은 옛 [F]/손바꾸기 안내를 jar 기본값으로 덮어씀
                if (containsLegacyFHint(config.get(key))) {
                    config.set(key, defaultConfig.get(key));
                    changed = true;
                }
            }
            if (changed) {
                try {
                    config.save(langFile);
                    plugin.getLogger().info("Updated " + fileName + " with missing/legacy language keys.");
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
                List<String> lines = config.getStringList(path).stream()
                        .map(this::color)
                        .collect(Collectors.toList());
                listCache.put(key, lines);
                langCache.put(key, String.join("\n", lines));
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

    public List<String> getList(LangKey key) {
        return listCache.getOrDefault(key, Collections.emptyList());
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

    private boolean containsLegacyFHint(Object value) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (containsLegacyFHint(item)) return true;
            }
            return false;
        }
        if (value == null) return false;
        String text = String.valueOf(value);
        return text.contains("[F]")
                || text.contains("Press F")
                || text.contains("press F")
                || text.contains("F (swap")
                || text.contains("F(손")
                || text.contains("F로")
                || text.contains("F で")
                || text.contains("F(手")
                || text.contains("[클릭]")
                || text.contains("[Click]")
                || text.contains("[クリック]")
                || text.contains("좌클릭/우클릭")
                || text.contains("Left/Right click")
                || text.contains("左クリック/右クリック")
                || text.contains("클릭으로")
                || text.contains("Click during")
                || text.contains("クリックで");
    }
}