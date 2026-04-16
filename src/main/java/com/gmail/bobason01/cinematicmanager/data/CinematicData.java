package com.gmail.bobason01.cinematicmanager.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CinematicData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final Map<Integer, List<CinematicAction>> timeline = new ConcurrentHashMap<>();
    private final Map<String, List<Location>> pathRecords = new ConcurrentHashMap<>();

    public CinematicData(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    public String getName() { return id; }

    public Map<Integer, List<CinematicAction>> getTimeline() {
        return timeline;
    }

    public List<Location> getPathRecord(String recordId) {
        return pathRecords.get(recordId);
    }

    public void addPathRecord(String recordId, List<Location> path) {
        pathRecords.put(recordId, new ArrayList<>(path));
    }

    public void addAction(int tick, CinematicAction action) {
        timeline.computeIfAbsent(tick, k -> new ArrayList<>()).add(action);
    }

    public int getLastTick() {
        if (timeline.isEmpty()) return 0;
        return Collections.max(timeline.keySet());
    }

    public CinematicAction getActionByTrack(int tick, CinematicAction.TrackType trackType) {
        List<CinematicAction> actions = timeline.get(tick);
        if (actions == null) return null;

        for (CinematicAction action : actions) {
            if (trackType == CinematicAction.TrackType.CAMERA && action.getType() == CinematicAction.ActionType.CAMERA) {
                return action;
            }
            if (trackType == CinematicAction.TrackType.EFFECT && isEffect(action.getType())) {
                return action;
            }
            if (trackType == CinematicAction.TrackType.ACTION && isEntityAction(action.getType())) {
                return action;
            }
        }
        return null;
    }

    public void removeActionsByTrack(int tick, CinematicAction.TrackType trackType) {
        List<CinematicAction> actions = timeline.get(tick);
        if (actions == null) return;

        actions.removeIf(action -> {
            if (trackType == CinematicAction.TrackType.CAMERA) return action.getType() == CinematicAction.ActionType.CAMERA;
            if (trackType == CinematicAction.TrackType.EFFECT) return isEffect(action.getType());
            if (trackType == CinematicAction.TrackType.ACTION) return isEntityAction(action.getType());
            return false;
        });

        if (actions.isEmpty()) timeline.remove(tick);
    }

    private boolean isEffect(CinematicAction.ActionType type) {
        return type == CinematicAction.ActionType.SOUND || type == CinematicAction.ActionType.PARTICLE ||
                type == CinematicAction.ActionType.TITLE || type == CinematicAction.ActionType.MESSAGE || type == CinematicAction.ActionType.COMMAND;
    }

    private boolean isEntityAction(CinematicAction.ActionType type) {
        return type == CinematicAction.ActionType.SPAWN_NPC || type == CinematicAction.ActionType.MOVE_NPC ||
                type == CinematicAction.ActionType.ANIMATION || type == CinematicAction.ActionType.SCALE ||
                type == CinematicAction.ActionType.HIDE_ENTITY || type == CinematicAction.ActionType.SHOW_ENTITY;
    }

    public void serialize(YamlConfiguration config) {
        ConfigurationSection timelineSection = config.createSection("timeline");
        for (Map.Entry<Integer, List<CinematicAction>> entry : timeline.entrySet()) {
            List<Map<String, Object>> actionList = new ArrayList<>();
            for (CinematicAction action : entry.getValue()) {
                Map<String, Object> map = new HashMap<>();
                map.put("type", action.getType().name());
                map.put("value", action.getValue());
                map.put("extra", action.getExtra());
                map.put("x", action.getX());
                map.put("y", action.getY());
                map.put("z", action.getZ());
                map.put("yaw", action.getYaw());
                map.put("pitch", action.getPitch());
                map.put("world", action.getWorldName());
                actionList.add(map);
            }
            timelineSection.set(entry.getKey().toString(), actionList);
        }

        ConfigurationSection pathSection = config.createSection("pathRecords");
        for (Map.Entry<String, List<Location>> entry : pathRecords.entrySet()) {
            List<String> locStrings = new ArrayList<>();
            for (Location loc : entry.getValue()) {
                locStrings.add(loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch());
            }
            pathSection.set(entry.getKey(), locStrings);
        }
    }

    public void deserialize(YamlConfiguration config) {
        timeline.clear();
        pathRecords.clear();
        ConfigurationSection timelineSection = config.getConfigurationSection("timeline");
        if (timelineSection != null) {
            for (String tickStr : timelineSection.getKeys(false)) {
                int tick = Integer.parseInt(tickStr);
                List<Map<?, ?>> actionMaps = timelineSection.getMapList(tickStr);
                for (Map<?, ?> map : actionMaps) {
                    CinematicAction.ActionType type = CinematicAction.ActionType.valueOf((String) map.get("type"));
                    String value = (String) map.get("value");
                    String extra = (String) map.get("extra");
                    String worldName = (String) map.get("world");
                    double x = (Double) map.get("x");
                    double y = (Double) map.get("y");
                    double z = (Double) map.get("z");
                    float yaw = ((Double) map.get("yaw")).floatValue();
                    float pitch = ((Double) map.get("pitch")).floatValue();

                    Location loc = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
                    addAction(tick, new CinematicAction(type, value, loc, extra));
                }
            }
        }
        ConfigurationSection pathSection = config.getConfigurationSection("pathRecords");
        if (pathSection != null) {
            for (String key : pathSection.getKeys(false)) {
                List<String> locStrings = pathSection.getStringList(key);
                List<Location> path = new ArrayList<>();
                for (String s : locStrings) {
                    String[] split = s.split(",");
                    path.add(new Location(Bukkit.getWorld(split[0]),
                            Double.parseDouble(split[1]), Double.parseDouble(split[2]), Double.parseDouble(split[3]),
                            Float.parseFloat(split[4]), Float.parseFloat(split[5])));
                }
                pathRecords.put(key, path);
            }
        }
    }
}