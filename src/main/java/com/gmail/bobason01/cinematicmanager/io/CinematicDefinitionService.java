package com.gmail.bobason01.cinematicmanager.io;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stable, versioned authoring format used by AI tools and administrators.
 * It compiles explicit YAML fields into the plugin's existing runtime model.
 */
public final class CinematicDefinitionService {
    public static final int SCHEMA_VERSION = 1;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final CinematicManager plugin;

    public CinematicDefinitionService(CinematicManager plugin) {
        this.plugin = plugin;
    }

    public enum Severity { ERROR, WARNING }

    public record Diagnostic(Severity severity, String code, String path, String message) {
        @Override
        public String toString() {
            return severity + " " + code + " at " + path + ": " + message;
        }
    }

    public record LoadResult(CinematicData data, List<Diagnostic> diagnostics, boolean legacy) {
        public boolean valid() {
            return data != null && diagnostics.stream().noneMatch(d -> d.severity() == Severity.ERROR);
        }
    }

    public boolean isSafeId(String id) {
        return id != null && SAFE_ID.matcher(id).matches();
    }

    public LoadResult load(File file, String expectedId) {
        return load(file, expectedId, false);
    }

    public LoadResult loadForImport(File file, String expectedId) {
        return load(file, expectedId, true);
    }

    private LoadResult load(File file, String expectedId, boolean enforceImportPolicy) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (file == null || !file.isFile()) {
            diagnostics.add(error("file.missing", "$", "File does not exist."));
            return new LoadResult(null, List.copyOf(diagnostics), false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            diagnostics.add(error("yaml.invalid", "$", exception.getMessage()));
            return new LoadResult(null, List.copyOf(diagnostics), false);
        }
        boolean legacy = !yaml.contains("schemaVersion");
        if (legacy && enforceImportPolicy) {
            diagnostics.add(error("schema.required", "schemaVersion",
                    "AI imports must use the versioned schema. Legacy files are migrated only from the cinematics folder."));
            return new LoadResult(null, List.copyOf(diagnostics), true);
        }
        CinematicData data = legacy
                ? parseLegacy(yaml, expectedId, diagnostics)
                : parseV1(yaml, expectedId, diagnostics, enforceImportPolicy);
        return new LoadResult(data, List.copyOf(diagnostics), legacy);
    }

    public YamlConfiguration serialize(CinematicData data) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schemaVersion", SCHEMA_VERSION);
        yaml.set("id", data.getId());
        Location origin = data.getOrigin();
        if (origin == null) origin = inferOrigin(data);
        if (origin != null) yaml.set("origin", locationMap(origin, true));

        Map<String, String> actorIds = buildActorIds(data);
        List<Map<String, Object>> actions = new ArrayList<>();
        data.getTimeline().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    for (CinematicAction action : entry.getValue()) {
                        actions.add(serializeAction(entry.getKey(), action, actorIds));
                    }
                });
        yaml.set("actions", actions);

        Map<String, Object> paths = new LinkedHashMap<>();
        data.getPathRecords().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    List<Map<String, Object>> points = new ArrayList<>();
                    for (Location location : entry.getValue()) {
                        points.add(locationMap(location, location.getWorld() != null));
                    }
                    paths.put(entry.getKey(), points);
                });
        yaml.set("paths", paths);
        return yaml;
    }

    public void saveAtomic(CinematicData data, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        File temp = new File(parent, target.getName() + ".tmp");
        serialize(data).save(temp);
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    public void backupLegacy(File source) throws IOException {
        File backup = new File(source.getParentFile(), source.getName() + ".legacy.bak");
        if (!backup.exists()) {
            Files.copy(source.toPath(), backup.toPath());
        }
    }

    private CinematicData parseV1(YamlConfiguration yaml, String expectedId,
                                  List<Diagnostic> diagnostics, boolean enforceImportPolicy) {
        int version = yaml.getInt("schemaVersion", -1);
        if (version != SCHEMA_VERSION) {
            diagnostics.add(error("schema.unsupported", "schemaVersion",
                    "Supported version is " + SCHEMA_VERSION + ", received " + version + "."));
            return null;
        }
        for (String key : yaml.getKeys(false)) {
            if (!Set.of("schemaVersion", "id", "origin", "actions", "paths").contains(key)) {
                diagnostics.add(error("field.unknown", key, "Unknown top-level field."));
            }
        }

        if (!yaml.isString("id")) {
            diagnostics.add(error("field.required", "id", "Document id is required."));
            return null;
        }
        if (!(yaml.get("actions") instanceof List<?>)) {
            diagnostics.add(error("field.required", "actions", "Actions must be a list."));
            return null;
        }
        String id = yaml.getString("id");
        if (expectedId != null && id != null && !expectedId.equals(id)) {
            diagnostics.add(error("id.mismatch", "id",
                    "Document id must match file id '" + expectedId + "'."));
        }
        if (!isSafeId(id)) {
            diagnostics.add(error("id.invalid", "id",
                    "Use 1-64 letters, numbers, underscores, or hyphens."));
            return null;
        }

        CinematicData data = new CinematicData(id);
        ConfigurationSection originSection = yaml.getConfigurationSection("origin");
        if (originSection != null) {
            data.setOrigin(readLocation(originSection.getValues(false), "origin", true, diagnostics));
        } else if (yaml.contains("origin")) {
            diagnostics.add(error("origin.object.required", "origin", "Origin must be an object."));
        }
        parsePathsV1(yaml, data, diagnostics);
        List<Map<?, ?>> actionMaps = yaml.getMapList("actions");
        List<?> rawActions = yaml.getList("actions");
        if (rawActions != null && rawActions.size() != actionMaps.size()) {
            diagnostics.add(error("action.object.required", "actions",
                    "Every action list item must be an object."));
        }
        Map<String, String> actors = collectActors(actionMaps, diagnostics);
        for (int index = 0; index < actionMaps.size(); index++) {
            parseActionV1(actionMaps.get(index), index, actors, data, diagnostics,
                    enforceImportPolicy);
        }
        validateReferences(data, diagnostics);
        if (data.getOrigin() == null) data.setOrigin(inferOrigin(data));
        return data;
    }

    private void parsePathsV1(YamlConfiguration yaml, CinematicData data,
                              List<Diagnostic> diagnostics) {
        ConfigurationSection paths = yaml.getConfigurationSection("paths");
        if (paths == null) {
            if (yaml.contains("paths")) {
                diagnostics.add(error("paths.object.required", "paths", "Paths must be an object."));
            }
            return;
        }
        for (String pathId : paths.getKeys(false)) {
            String base = "paths." + pathId;
            if (!isSafeId(pathId)) {
                diagnostics.add(error("path.id.invalid", base, "Path id uses unsafe characters."));
                continue;
            }
            List<Map<?, ?>> pointMaps = paths.getMapList(pathId);
            List<Location> points = new ArrayList<>();
            for (int i = 0; i < pointMaps.size(); i++) {
                Location point = readLocation(pointMaps.get(i), base + "[" + i + "]",
                        false, diagnostics);
                if (point != null) points.add(point);
            }
            if (points.isEmpty()) {
                diagnostics.add(error("path.empty", base, "A path must contain at least one point."));
            } else {
                data.addPathRecord(pathId, points);
            }
        }
    }

    private Map<String, String> collectActors(List<Map<?, ?>> actions,
                                               List<Diagnostic> diagnostics) {
        Map<String, String> actors = new LinkedHashMap<>();
        for (int i = 0; i < actions.size(); i++) {
            Map<?, ?> action = actions.get(i);
            if (!"spawn_npc".equals(normalized(action.get("type")))) continue;
            String actorId = text(action.get("actorId"));
            String path = "actions[" + i + "]";
            if (!isSafeId(actorId)) {
                diagnostics.add(error("actor.id.invalid", path + ".actorId",
                        "Actor id must use safe id characters."));
                continue;
            }
            String encoded = encodeSource(map(action.get("source")), path + ".source", diagnostics);
            if (encoded != null) {
                String previous = actors.putIfAbsent(actorId, encoded);
                if (previous != null && !previous.equals(encoded)) {
                    diagnostics.add(error("actor.id.duplicate", path + ".actorId",
                            "Actor id '" + actorId + "' is declared with a different source."));
                }
            }
        }
        return actors;
    }

    private void parseActionV1(Map<?, ?> map, int index, Map<String, String> actors,
                               CinematicData data, List<Diagnostic> diagnostics,
                               boolean enforceImportPolicy) {
        String base = "actions[" + index + "]";
        Integer tick = integer(map.get("tick"));
        if (tick == null || tick < 0) {
            diagnostics.add(error("action.tick.invalid", base + ".tick",
                    "Tick must be a non-negative integer."));
            return;
        }
        String typeName = normalized(map.get("type"));
        CinematicAction.ActionType type;
        try {
            type = CinematicAction.ActionType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            diagnostics.add(error("action.type.unknown", base + ".type",
                    "Unknown action type '" + typeName + "'."));
            return;
        }
        validateActionKeys(map, type, base, diagnostics);

        String value = null;
        String extra = null;
        Location location = null;
        switch (type) {
            case SPAWN_NPC -> {
                String actorId = text(map.get("actorId"));
                value = actors.get(actorId);
                location = readLocation(map(map.get("location")), base + ".location", true, diagnostics);
                if (value == null) required(diagnostics, base + ".source", "A valid source is required.");
            }
            case MOVE_NPC -> {
                value = requiredText(map, "pathId", base, diagnostics);
                extra = resolveActor(map, actors, base, diagnostics);
                location = readLocation(map(map.get("origin")), base + ".origin", true, diagnostics);
            }
            case CAMERA -> {
                String mode = normalized(map.get("mode"));
                if ("static".equals(mode)) {
                    value = "static";
                    location = readLocation(map(map.get("location")), base + ".location", true, diagnostics);
                } else if ("path".equals(mode)) {
                    value = requiredText(map, "pathId", base, diagnostics);
                    location = readLocation(map(map.get("origin")), base + ".origin", true, diagnostics);
                } else {
                    diagnostics.add(error("camera.mode.invalid", base + ".mode",
                            "Mode must be static or path."));
                }
            }
            case SOUND -> value = requiredText(map, "sound", base, diagnostics);
            case PARTICLE -> {
                value = requiredText(map, "particle", base, diagnostics);
                location = readLocation(map(map.get("location")), base + ".location", true, diagnostics);
                validateEnum(value, org.bukkit.Particle.class, base + ".particle", diagnostics);
            }
            case TITLE -> value = text(map.get("title")) + ";" + text(map.get("subtitle"));
            case MESSAGE -> value = requiredText(map, "text", base, diagnostics);
            case COMMAND -> {
                value = requiredText(map, "command", base, diagnostics);
                String executor = normalized(map.get("executor"));
                if (value != null && value.startsWith("#")) {
                    diagnostics.add(error("command.prefix.denied", base + ".command",
                            "Do not prefix commands with '#'; select executor explicitly."));
                }
                if ("console".equals(executor)) {
                    if (enforceImportPolicy
                            && !plugin.getConfig().getBoolean("ai-import.allow-console-commands", false)) {
                        diagnostics.add(error("command.console.denied", base + ".executor",
                                "Console commands are disabled by ai-import.allow-console-commands."));
                    }
                    value = "#" + value;
                } else if (executor.isEmpty()) {
                    required(diagnostics, base + ".executor", "Executor is required.");
                } else if (!"player".equals(executor)) {
                    diagnostics.add(error("command.executor.invalid", base + ".executor",
                            "Executor must be player or console."));
                }
            }
            case HIDE_ENTITY -> value = resolveActor(map, actors, base, diagnostics);
            case SHOW_ENTITY -> {
                value = resolveActor(map, actors, base, diagnostics);
                location = readLocation(map(map.get("location")), base + ".location", true, diagnostics);
            }
            case ANIMATION -> {
                value = requiredText(map, "animation", base, diagnostics);
                extra = resolveActor(map, actors, base, diagnostics);
            }
            case LIGHTNING -> location = readLocation(map(map.get("location")),
                    base + ".location", true, diagnostics);
            case DIALOGUE -> {
                value = encodeDialogue(map.get("pages"), base + ".pages", diagnostics);
                extra = displayMode(map.get("displayMode"), base, diagnostics);
            }
            case WAIT -> {
                value = requiredText(map, "prompt", base, diagnostics);
                extra = displayMode(map.get("displayMode"), base, diagnostics);
            }
        }
        if (value == null && type != CinematicAction.ActionType.LIGHTNING) return;
        data.addAction(tick, new CinematicAction(type, value, location, extra));
    }

    private CinematicData parseLegacy(YamlConfiguration yaml, String id,
                                      List<Diagnostic> diagnostics) {
        if (!isSafeId(id)) {
            diagnostics.add(error("id.invalid", "id", "Legacy filename is not a safe cinematic id."));
            return null;
        }
        if (!yaml.contains("timeline") && !yaml.contains("pathRecords")) {
            diagnostics.add(error("legacy.structure.invalid", "$",
                    "Legacy documents require timeline or pathRecords."));
            return null;
        }
        CinematicData data = new CinematicData(id);
        ConfigurationSection timeline = yaml.getConfigurationSection("timeline");
        if (timeline != null) {
            List<String> ticks = new ArrayList<>(timeline.getKeys(false));
            ticks.sort(Comparator.comparingInt(this::parseTickForSort));
            for (String tickKey : ticks) {
                Integer tick = integer(tickKey);
                if (tick == null || tick < 0) {
                    diagnostics.add(error("legacy.tick.invalid", "timeline." + tickKey,
                            "Tick must be a non-negative integer."));
                    continue;
                }
                List<Map<?, ?>> maps = timeline.getMapList(tickKey);
                for (int i = 0; i < maps.size(); i++) {
                    String base = "timeline." + tickKey + "[" + i + "]";
                    Map<?, ?> map = maps.get(i);
                    try {
                        CinematicAction.ActionType type = CinematicAction.ActionType.valueOf(
                                text(map.get("type")).toUpperCase(Locale.ROOT));
                        String value = nullableText(map.get("value"));
                        String extra = nullableText(map.get("extra"));
                        Location location = readLegacyLocation(map, base, diagnostics);
                        if (location != null) {
                            data.addAction(tick, new CinematicAction(type, value, location, extra));
                        }
                    } catch (Exception exception) {
                        diagnostics.add(error("legacy.action.invalid", base, exception.getMessage()));
                    }
                }
            }
        }
        ConfigurationSection paths = yaml.getConfigurationSection("pathRecords");
        if (paths != null) {
            for (String pathId : paths.getKeys(false)) {
                List<Location> points = new ArrayList<>();
                List<String> encoded = paths.getStringList(pathId);
                for (int i = 0; i < encoded.size(); i++) {
                    String[] parts = encoded.get(i).split(",", -1);
                    try {
                        if (parts.length != 6) throw new IllegalArgumentException("Expected 6 CSV fields.");
                        World world = parts[0].isEmpty() ? null : Bukkit.getWorld(parts[0]);
                        points.add(new Location(world, Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                                Float.parseFloat(parts[4]), Float.parseFloat(parts[5])));
                    } catch (Exception exception) {
                        diagnostics.add(error("legacy.path.invalid",
                                "pathRecords." + pathId + "[" + i + "]", exception.getMessage()));
                    }
                }
                if (!points.isEmpty()) data.addPathRecord(pathId, points);
            }
        }
        diagnostics.add(new Diagnostic(Severity.WARNING, "legacy.migrated", "$",
                "Legacy document was loaded and will be migrated to schemaVersion 1."));
        data.setOrigin(inferOrigin(data));
        return data;
    }

    private Location readLegacyLocation(Map<?, ?> map, String path,
                                        List<Diagnostic> diagnostics) {
        String worldName = text(map.get("world"));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            diagnostics.add(error("world.unknown", path + ".world",
                    "World '" + worldName + "' is not loaded."));
            return null;
        }
        Double x = decimal(map.get("x"));
        Double y = decimal(map.get("y"));
        Double z = decimal(map.get("z"));
        Double yaw = decimal(map.get("yaw"));
        Double pitch = decimal(map.get("pitch"));
        if (x == null || y == null || z == null || yaw == null || pitch == null) {
            diagnostics.add(error("location.number.invalid", path, "Location fields must be numbers."));
            return null;
        }
        return new Location(world, x, y, z, yaw.floatValue(), pitch.floatValue());
    }

    private Location readLocation(Map<?, ?> map, String path, boolean worldRequired,
                                  List<Diagnostic> diagnostics) {
        if (map == null) {
            required(diagnostics, path, "Location object is required.");
            return null;
        }
        validateObjectKeys(map, Set.of("world", "x", "y", "z", "yaw", "pitch"),
                path, diagnostics);
        Double x = decimal(map.get("x"));
        Double y = decimal(map.get("y"));
        Double z = decimal(map.get("z"));
        Double yaw = map.containsKey("yaw") ? decimal(map.get("yaw")) : 0.0;
        Double pitch = map.containsKey("pitch") ? decimal(map.get("pitch")) : 0.0;
        if (!finite(x) || !finite(y) || !finite(z) || !finite(yaw) || !finite(pitch)) {
            diagnostics.add(error("location.number.invalid", path,
                    "Location fields must be finite numbers."));
            return null;
        }
        String worldName = text(map.get("world"));
        World world = worldName.isEmpty() ? null : Bukkit.getWorld(worldName);
        if (worldRequired && world == null) {
            diagnostics.add(error("world.unknown", path + ".world",
                    "A loaded world name is required."));
            return null;
        }
        return new Location(world, x, y, z, yaw.floatValue(), pitch.floatValue());
    }

    private void validateReferences(CinematicData data, List<Diagnostic> diagnostics) {
        Set<String> pathIds = data.getPathRecords().keySet();
        Map<String, int[]> actorSpawns = new HashMap<>();
        data.getTimeline().forEach((tick, actions) -> {
            for (int i = 0; i < actions.size(); i++) {
                if (actions.get(i).getType() == CinematicAction.ActionType.SPAWN_NPC) {
                    int[] candidate = new int[]{tick, i};
                    actorSpawns.merge(actions.get(i).getValue(), candidate, (left, right) ->
                            left[0] < right[0] || (left[0] == right[0] && left[1] <= right[1])
                                    ? left : right);
                }
            }
        });
        data.getTimeline().forEach((tick, actions) -> {
            for (int i = 0; i < actions.size(); i++) {
                CinematicAction action = actions.get(i);
                if ((action.getType() == CinematicAction.ActionType.MOVE_NPC
                        || (action.getType() == CinematicAction.ActionType.CAMERA
                        && !"static".equalsIgnoreCase(action.getValue())))
                        && !pathIds.contains(action.getValue())) {
                    diagnostics.add(error("path.reference.unknown",
                            "timeline." + tick + "[" + i + "]",
                            "Path '" + action.getValue() + "' does not exist."));
                }
                String target = switch (action.getType()) {
                    case MOVE_NPC, ANIMATION -> action.getExtra();
                    case HIDE_ENTITY -> action.getValue();
                    case SHOW_ENTITY -> action.getExtra() != null
                            ? action.getExtra() : action.getValue();
                    default -> null;
                };
                if (target != null) {
                    int[] spawn = actorSpawns.get(target);
                    if (spawn == null || spawn[0] > tick || (spawn[0] == tick && spawn[1] >= i)) {
                        diagnostics.add(error("actor.reference.before_spawn",
                                "timeline." + tick + "[" + i + "]",
                                "Actor must be spawned before it is used."));
                    }
                }
            }
        });
    }

    private void validateActionKeys(Map<?, ?> map, CinematicAction.ActionType type, String base,
                                    List<Diagnostic> diagnostics) {
        Set<String> allowed = new LinkedHashSet<>(Set.of("tick", "type"));
        switch (type) {
            case SPAWN_NPC -> allowed.addAll(Set.of("actorId", "source", "location"));
            case MOVE_NPC -> allowed.addAll(Set.of("actorId", "pathId", "origin"));
            case CAMERA -> allowed.addAll(Set.of("mode", "pathId", "origin", "location"));
            case SOUND -> allowed.add("sound");
            case PARTICLE -> allowed.addAll(Set.of("particle", "location"));
            case TITLE -> allowed.addAll(Set.of("title", "subtitle"));
            case MESSAGE -> allowed.add("text");
            case COMMAND -> allowed.addAll(Set.of("executor", "command"));
            case HIDE_ENTITY -> allowed.add("actorId");
            case SHOW_ENTITY -> allowed.addAll(Set.of("actorId", "location"));
            case ANIMATION -> allowed.addAll(Set.of("actorId", "animation"));
            case LIGHTNING -> allowed.add("location");
            case DIALOGUE -> allowed.addAll(Set.of("displayMode", "pages"));
            case WAIT -> allowed.addAll(Set.of("displayMode", "prompt"));
        }
        for (Object key : map.keySet()) {
            String name = String.valueOf(key);
            if (!allowed.contains(name)) {
                diagnostics.add(error("field.unknown", base + "." + name,
                        "Unknown field for " + type.name().toLowerCase(Locale.ROOT) + "."));
            }
        }
    }

    private Map<String, Object> serializeAction(int tick, CinematicAction action,
                                                Map<String, String> actorIds) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tick", tick);
        out.put("type", action.getType().name().toLowerCase(Locale.ROOT));
        switch (action.getType()) {
            case SPAWN_NPC -> {
                out.put("actorId", actorIds.get(action.getValue()));
                out.put("source", decodeSource(action.getValue()));
                out.put("location", actionLocationMap(action));
            }
            case MOVE_NPC -> {
                out.put("actorId", actorIds.getOrDefault(action.getExtra(), safeActorId(action.getExtra())));
                out.put("pathId", action.getValue());
                out.put("origin", actionLocationMap(action));
            }
            case CAMERA -> {
                if ("static".equalsIgnoreCase(action.getValue())) {
                    out.put("mode", "static");
                    out.put("location", actionLocationMap(action));
                } else {
                    out.put("mode", "path");
                    out.put("pathId", action.getValue());
                    out.put("origin", actionLocationMap(action));
                }
            }
            case SOUND -> out.put("sound", action.getValue());
            case PARTICLE -> {
                out.put("particle", action.getValue());
                out.put("location", actionLocationMap(action));
            }
            case TITLE -> {
                String[] title = text(action.getValue()).split(";", 2);
                out.put("title", title[0]);
                out.put("subtitle", title.length > 1 ? title[1] : "");
            }
            case MESSAGE -> out.put("text", action.getValue());
            case COMMAND -> {
                String command = text(action.getValue());
                boolean console = command.startsWith("#");
                out.put("executor", console ? "console" : "player");
                out.put("command", console ? command.substring(1) : command);
            }
            case HIDE_ENTITY -> out.put("actorId",
                    actorIds.getOrDefault(action.getValue(), safeActorId(action.getValue())));
            case SHOW_ENTITY -> {
                String target = action.getExtra() != null ? action.getExtra() : action.getValue();
                out.put("actorId", actorIds.getOrDefault(target, safeActorId(target)));
                out.put("location", actionLocationMap(action));
            }
            case ANIMATION -> {
                out.put("actorId",
                        actorIds.getOrDefault(action.getExtra(), safeActorId(action.getExtra())));
                out.put("animation", action.getValue());
            }
            case LIGHTNING -> out.put("location", actionLocationMap(action));
            case DIALOGUE -> {
                List<Map<String, Object>> pages = new ArrayList<>();
                String separator = plugin.getConfig().getString("dialogue.page-separator", "||");
                for (String page : text(action.getValue()).split(Pattern.quote(separator), -1)) {
                    String[] parts = page.split(";", 2);
                    Map<String, Object> pageMap = new LinkedHashMap<>();
                    pageMap.put("speaker", parts.length > 1 ? parts[0] : "");
                    pageMap.put("text", parts.length > 1 ? parts[1] : parts[0]);
                    pages.add(pageMap);
                }
                out.put("displayMode", action.getExtra() == null ? "default" : action.getExtra());
                out.put("pages", pages);
            }
            case WAIT -> {
                out.put("prompt", action.getValue());
                out.put("displayMode", action.getExtra() == null ? "default" : action.getExtra());
            }
        }
        return out;
    }

    private Map<String, String> buildActorIds(CinematicData data) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        int sequence = 1;
        for (Map.Entry<Integer, List<CinematicAction>> entry :
                data.getTimeline().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            for (CinematicAction action : entry.getValue()) {
                if (action.getType() != CinematicAction.ActionType.SPAWN_NPC
                        || result.containsKey(action.getValue())) continue;
                String candidate = sourceSuggestedId(action.getValue());
                if (!isSafeId(candidate) || used.contains(candidate)) {
                    do candidate = "actor-" + sequence++; while (used.contains(candidate));
                }
                used.add(candidate);
                result.put(action.getValue(), candidate);
            }
        }
        return result;
    }

    private String encodeSource(Map<?, ?> source, String path, List<Diagnostic> diagnostics) {
        if (source == null) {
            required(diagnostics, path, "Source object is required.");
            return null;
        }
        String provider = normalized(source.get("provider"));
        if ("vanilla".equals(provider)) {
            validateObjectKeys(source, Set.of("provider", "entityType", "name", "skin"),
                    path, diagnostics);
            String type = text(source.get("entityType"));
            String name = text(source.get("name"));
            if (type.isEmpty() || name.isEmpty()) {
                required(diagnostics, path, "vanilla source requires entityType and name.");
                return null;
            }
            try {
                EntityType.valueOf(type.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(error("source.entity_type.unknown", path + ".entityType",
                        "Unknown Bukkit entity type '" + type + "'."));
                return null;
            }
            String skin = text(source.get("skin"));
            return "npc:" + type.toUpperCase(Locale.ROOT) + ":" + name
                    + (skin.isEmpty() ? "" : ":" + skin);
        }
        if ("mythicmobs".equals(provider) || "modelengine".equals(provider)) {
            validateObjectKeys(source, Set.of("provider", "id"), path, diagnostics);
            String id = text(source.get("id"));
            if (id.isEmpty()) {
                required(diagnostics, path + ".id", "Provider id is required.");
                return null;
            }
            String pluginName = "mythicmobs".equals(provider) ? "MythicMobs" : "ModelEngine";
            if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
                diagnostics.add(new Diagnostic(Severity.WARNING, "source.plugin.missing",
                        path + ".provider", pluginName + " is not currently enabled."));
            }
            return provider + ":" + id;
        }
        diagnostics.add(error("source.provider.invalid", path + ".provider",
                "Provider must be vanilla, mythicmobs, or modelengine."));
        return null;
    }

    private Map<String, Object> decodeSource(String value) {
        Map<String, Object> source = new LinkedHashMap<>();
        String[] parts = text(value).split(":", 4);
        if (parts.length >= 3 && "npc".equalsIgnoreCase(parts[0])) {
            source.put("provider", "vanilla");
            source.put("entityType", parts[1]);
            source.put("name", parts[2]);
            if (parts.length == 4 && !parts[3].isEmpty()) source.put("skin", parts[3]);
        } else if (parts.length >= 2) {
            source.put("provider", parts[0].toLowerCase(Locale.ROOT));
            source.put("id", text(value).substring(parts[0].length() + 1));
        } else {
            source.put("provider", "vanilla");
            source.put("entityType", "PLAYER");
            source.put("name", text(value));
        }
        return source;
    }

    private String encodeDialogue(Object pagesValue, String path, List<Diagnostic> diagnostics) {
        if (!(pagesValue instanceof List<?> pages) || pages.isEmpty()) {
            required(diagnostics, path, "At least one dialogue page is required.");
            return null;
        }
        String separator = plugin.getConfig().getString("dialogue.page-separator", "||");
        List<String> encoded = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            Map<?, ?> page = map(pages.get(i));
            if (page == null) {
                required(diagnostics, path + "[" + i + "]", "Page must be an object.");
                continue;
            }
            validateObjectKeys(page, Set.of("speaker", "text"), path + "[" + i + "]",
                    diagnostics);
            String speaker = text(page.get("speaker"));
            String line = text(page.get("text"));
            if (line.isEmpty()) required(diagnostics, path + "[" + i + "].text", "Text is required.");
            if (speaker.contains(";") || line.contains(separator)) {
                diagnostics.add(error("dialogue.separator.conflict", path + "[" + i + "]",
                        "speaker cannot contain ';' and text cannot contain the page separator."));
            }
            encoded.add(speaker + ";" + line);
        }
        return String.join(separator, encoded);
    }

    private String displayMode(Object value, String base, List<Diagnostic> diagnostics) {
        String mode = normalized(value);
        if (mode.isEmpty() || "default".equals(mode)) return null;
        if (!Set.of("title", "actionbar", "both", "betterhud", "bossbar").contains(mode)) {
            diagnostics.add(error("display_mode.invalid", base + ".displayMode",
                    "Use default, title, actionbar, both, betterhud, or bossbar."));
        }
        return mode;
    }

    private String resolveActor(Map<?, ?> action, Map<String, String> actors, String base,
                                List<Diagnostic> diagnostics) {
        String actorId = text(action.get("actorId"));
        String target = actors.get(actorId);
        if (target == null) {
            diagnostics.add(error("actor.reference.unknown", base + ".actorId",
                    "Actor '" + actorId + "' has no spawn_npc declaration."));
        }
        return target;
    }

    private String requiredText(Map<?, ?> map, String key, String base,
                                List<Diagnostic> diagnostics) {
        String value = text(map.get(key));
        if (value.isEmpty()) {
            required(diagnostics, base + "." + key, "Value is required.");
            return null;
        }
        return value;
    }

    private void validateEnum(String value, Class<? extends Enum<?>> enumType, String path,
                              List<Diagnostic> diagnostics) {
        if (value == null) return;
        try {
            Enum.valueOf(enumType.asSubclass(Enum.class), value.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            diagnostics.add(error("enum.unknown", path, "Unknown value '" + value + "'."));
        }
    }

    private void validateObjectKeys(Map<?, ?> map, Set<String> allowed, String path,
                                    List<Diagnostic> diagnostics) {
        for (Object key : map.keySet()) {
            String name = String.valueOf(key);
            if (!allowed.contains(name)) {
                diagnostics.add(error("field.unknown", path + "." + name, "Unknown field."));
            }
        }
    }

    private Map<String, Object> actionLocationMap(CinematicAction action) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", action.getWorldName());
        map.put("x", action.getX());
        map.put("y", action.getY());
        map.put("z", action.getZ());
        map.put("yaw", action.getYaw());
        map.put("pitch", action.getPitch());
        return map;
    }

    private Location inferOrigin(CinematicData data) {
        List<Map.Entry<Integer, List<CinematicAction>>> entries =
                new ArrayList<>(data.getTimeline().entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (Map.Entry<Integer, List<CinematicAction>> entry : entries) {
            if (entry.getKey() != 0) continue;
            for (CinematicAction action : entry.getValue()) {
                if (action.getType() == CinematicAction.ActionType.CAMERA) {
                    Location location = action.getLocation();
                    if (location != null) return location;
                }
            }
        }
        for (Map.Entry<Integer, List<CinematicAction>> entry : entries) {
            if (entry.getKey() != 0) continue;
            for (CinematicAction action : entry.getValue()) {
                if (action.getType() != CinematicAction.ActionType.SPAWN_NPC
                        && action.getType() != CinematicAction.ActionType.MOVE_NPC
                        && action.getType() != CinematicAction.ActionType.PARTICLE
                        && action.getType() != CinematicAction.ActionType.LIGHTNING
                        && action.getType() != CinematicAction.ActionType.SHOW_ENTITY) {
                    continue;
                }
                Location location = action.getLocation();
                if (location != null) return location;
            }
        }
        return null;
    }

    private Map<String, Object> locationMap(Location location, boolean includeWorld) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (includeWorld) map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        return map;
    }

    private String sourceSuggestedId(String source) {
        String[] parts = text(source).split(":");
        String raw = parts.length >= 3 && "npc".equalsIgnoreCase(parts[0])
                ? parts[2] : parts[parts.length - 1];
        return safeActorId(raw);
    }

    private String safeActorId(String raw) {
        String id = text(raw).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        return id.isEmpty() ? "actor" : id;
    }

    private int parseTickForSort(String tick) {
        Integer value = integer(tick);
        return value == null ? Integer.MAX_VALUE : value;
    }

    private static Diagnostic error(String code, String path, String message) {
        return new Diagnostic(Severity.ERROR, code, path,
                message == null || message.isBlank() ? "Invalid value." : message);
    }

    private static void required(List<Diagnostic> diagnostics, String path, String message) {
        diagnostics.add(error("field.required", path, message));
    }

    private static String normalized(Object value) {
        return text(value).toLowerCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String nullableText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            double decimal = number.doubleValue();
            if (!Double.isFinite(decimal) || decimal != Math.rint(decimal)
                    || decimal < Integer.MIN_VALUE || decimal > Integer.MAX_VALUE) {
                return null;
            }
            return (int) decimal;
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double decimal(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }
}
