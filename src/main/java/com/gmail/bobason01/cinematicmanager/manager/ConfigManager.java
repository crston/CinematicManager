package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import com.gmail.bobason01.cinematicmanager.io.CinematicDefinitionService;
import org.bukkit.Location;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {

    private final CinematicManager plugin;
    private final File dataFolder;
    private final File importFolder;
    private final File exportFolder;
    private final CinematicDefinitionService definitions;
    private final Map<String, CinematicData> cinematicCache = new ConcurrentHashMap<>();

    public ConfigManager(CinematicManager plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "cinematics");
        this.importFolder = new File(plugin.getDataFolder(), "imports");
        this.exportFolder = new File(plugin.getDataFolder(), "exports");
        this.definitions = new CinematicDefinitionService(plugin);
        if (!dataFolder.exists()) dataFolder.mkdirs();
        if (!importFolder.exists()) importFolder.mkdirs();
        if (!exportFolder.exists()) exportFolder.mkdirs();
        loadAll();
    }

    public void loadAll() {
        cinematicCache.clear();
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String name = stripYaml(file.getName());
            CinematicDefinitionService.LoadResult result = definitions.load(file, name);
            logDiagnostics(file.getName(), result.diagnostics());
            if (!result.valid()) {
                plugin.getLogger().warning("Skipped invalid cinematic: " + file.getName());
                continue;
            }
            cinematicCache.put(name, result.data());
            if (result.legacy()) {
                try {
                    definitions.backupLegacy(file);
                    definitions.saveAtomic(result.data(), file);
                    plugin.getLogger().info("Migrated legacy cinematic to schemaVersion 1: " + name);
                } catch (IOException exception) {
                    plugin.getLogger().warning("Could not migrate " + file.getName() + ": "
                            + exception.getMessage());
                }
            }
        }
    }

    public CinematicData getCinematic(String name) {
        return cinematicCache.get(name);
    }

    public Set<String> getIds() {
        return cinematicCache.keySet();
    }

    public boolean createCinematic(String name) {
        return createCinematic(name, null);
    }

    public boolean createCinematic(String name, Location origin) {
        if (!definitions.isSafeId(name)) {
            plugin.getLogger().warning("Rejected unsafe cinematic id: " + name);
            return false;
        }
        if (!cinematicCache.containsKey(name)) {
            CinematicData data = new CinematicData(name);
            data.setOrigin(origin);
            cinematicCache.put(name, data);
            saveCinematic(data);
        }
        return true;
    }

    public void create(String name) {
        createCinematic(name);
    }

    public void saveCinematic(CinematicData data) {
        if (data == null || !definitions.isSafeId(data.getName())) {
            plugin.getLogger().warning("Cannot save cinematic with an unsafe id.");
            return;
        }
        File file = new File(dataFolder, data.getName() + ".yml");
        try {
            definitions.saveAtomic(data, file);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save cinematic '" + data.getName()
                    + "': " + e.getMessage());
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
        if (!definitions.isSafeId(name)) return;
        cinematicCache.remove(name);
        File file = new File(dataFolder, name + ".yml");
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to delete cinematic '" + name + "': "
                    + exception.getMessage());
        }
    }

    public Map<String, CinematicData> getCinematicCache() {
        return cinematicCache;
    }

    public CinematicDefinitionService.LoadResult validateImport(String filename) {
        File source = resolveImportFile(filename);
        String id = source == null ? null : stripYaml(source.getName());
        return definitions.loadForImport(source, id);
    }

    public OperationResult importCinematic(String filename, boolean replace) {
        File source = resolveImportFile(filename);
        if (source == null) {
            return new OperationResult(false, "Use a .yml filename from the imports folder.", List.of());
        }
        String id = stripYaml(source.getName());
        CinematicDefinitionService.LoadResult result = definitions.loadForImport(source, id);
        if (!result.valid()) {
            return new OperationResult(false, "Validation failed.", result.diagnostics());
        }
        if (cinematicCache.containsKey(id) && !replace) {
            return new OperationResult(false,
                    "Cinematic '" + id + "' already exists. Add --replace to overwrite it.",
                    result.diagnostics());
        }
        File target = new File(dataFolder, id + ".yml");
        try {
            if (target.exists() && replace) {
                Files.copy(target.toPath(), new File(dataFolder, id + ".yml.import.bak").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            definitions.saveAtomic(result.data(), target);
            cinematicCache.put(id, result.data());
            return new OperationResult(true, "Imported cinematic '" + id + "'.", result.diagnostics());
        } catch (IOException exception) {
            return new OperationResult(false, "Import failed: " + exception.getMessage(),
                    result.diagnostics());
        }
    }

    public OperationResult exportCinematic(String id) {
        if (!definitions.isSafeId(id)) {
            return new OperationResult(false, "Invalid cinematic id.", List.of());
        }
        CinematicData data = cinematicCache.get(id);
        if (data == null) {
            return new OperationResult(false, "Cinematic '" + id + "' does not exist.", List.of());
        }
        try {
            File target = new File(exportFolder, id + ".yml");
            definitions.saveAtomic(data, target);
            return new OperationResult(true, "Exported to exports/" + target.getName(), List.of());
        } catch (IOException exception) {
            return new OperationResult(false, "Export failed: " + exception.getMessage(), List.of());
        }
    }

    public File getImportFolder() {
        return importFolder;
    }

    private File resolveImportFile(String filename) {
        if (filename == null || filename.contains("/") || filename.contains("\\")
                || !filename.toLowerCase().endsWith(".yml")) {
            return null;
        }
        File source = new File(importFolder, filename);
        try {
            if (!source.getCanonicalFile().getParentFile().equals(importFolder.getCanonicalFile())) {
                return null;
            }
        } catch (IOException exception) {
            return null;
        }
        return source;
    }

    private void logDiagnostics(String source,
                                List<CinematicDefinitionService.Diagnostic> diagnostics) {
        for (CinematicDefinitionService.Diagnostic diagnostic : diagnostics) {
            String message = source + ": " + diagnostic;
            if (diagnostic.severity() == CinematicDefinitionService.Severity.ERROR) {
                plugin.getLogger().warning(message);
            } else {
                plugin.getLogger().info(message);
            }
        }
    }

    private static String stripYaml(String filename) {
        return filename.toLowerCase().endsWith(".yml")
                ? filename.substring(0, filename.length() - 4) : filename;
    }

    public record OperationResult(boolean success, String message,
                                  List<CinematicDefinitionService.Diagnostic> diagnostics) {
    }
}