package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {

    private final CinematicManager plugin;
    private final File dataFolder;
    private final Map<String, CinematicData> cinematicCache = new ConcurrentHashMap<>();

    public ConfigManager(CinematicManager plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "cinematics");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        loadAll();
    }

    public void loadAll() {
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String name = file.getName().replace(".yml", "");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            CinematicData data = new CinematicData(name);
            data.deserialize(config);
            cinematicCache.put(name, data);
        }
    }

    public CinematicData getCinematic(String name) {
        return cinematicCache.get(name);
    }

    public Set<String> getIds() {
        return cinematicCache.keySet();
    }

    public void createCinematic(String name) {
        if (!cinematicCache.containsKey(name)) {
            CinematicData data = new CinematicData(name);
            cinematicCache.put(name, data);
            saveCinematic(data);
        }
    }

    public void create(String name) {
        createCinematic(name);
    }

    public void saveCinematic(CinematicData data) {
        File file = new File(dataFolder, data.getName() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        data.serialize(config);
        try {
            config.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveCinematic(String name) {
        CinematicData data = cinematicCache.get(name);
        if (data != null) {
            saveCinematic(data);
        }
    }

    public void saveAll() {
        for (CinematicData data : cinematicCache.values()) {
            saveCinematic(data);
        }
    }

    public void deleteCinematic(String name) {
        cinematicCache.remove(name);
        File file = new File(dataFolder, name + ".yml");
        if (file.exists()) file.delete();
    }

    public Map<String, CinematicData> getCinematicCache() {
        return cinematicCache;
    }
}