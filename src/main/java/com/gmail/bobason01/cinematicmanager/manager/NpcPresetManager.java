package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.NpcPreset;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class NpcPresetManager {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final CinematicManager plugin;
    private final File folder;
    private final Map<String, NpcPreset> presets = new ConcurrentHashMap<>();

    public NpcPresetManager(CinematicManager plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "npcs");
        if (!folder.exists()) folder.mkdirs();
        loadAll();
    }

    public void loadAll() {
        presets.clear();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            if (!isSafeId(id)) continue;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            presets.put(id, NpcPreset.deserialize(yaml, id));
        }
    }

    public boolean isSafeId(String id) {
        return id != null && SAFE_ID.matcher(id).matches();
    }

    public NpcPreset getPreset(String id) {
        return presets.get(id);
    }

    public Collection<NpcPreset> all() {
        return presets.values();
    }

    public boolean save(NpcPreset preset) {
        if (preset == null || !isSafeId(preset.getId())) return false;
        File file = new File(folder, preset.getId() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        preset.serialize(yaml);
        try {
            yaml.save(file);
            presets.put(preset.getId(), preset);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save NPC preset '" + preset.getId()
                    + "': " + exception.getMessage());
            return false;
        }
    }

    public boolean deletePreset(String id) {
        if (!isSafeId(id)) return false;
        presets.remove(id);
        try {
            return Files.deleteIfExists(new File(folder, id + ".yml").toPath());
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to delete NPC preset '" + id
                    + "': " + exception.getMessage());
            return false;
        }
    }
}
