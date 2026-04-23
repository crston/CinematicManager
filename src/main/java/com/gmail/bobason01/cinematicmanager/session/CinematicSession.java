package com.gmail.bobason01.cinematicmanager.session;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CinematicSession {

    private final CinematicManager plugin;
    private final Player player;
    private final CinematicData data;

    private boolean active = false;
    private boolean hasPapi = false;
    private BukkitTask ticker;
    private int currentTick = 0;
    private int maxTick = 0;

    private final Map<String, Entity> activeEntities = new ConcurrentHashMap<>();
    private final Map<String, Location> spawnLocations = new ConcurrentHashMap<>();
    private final Map<String, ActivePath> movingNpcs = new ConcurrentHashMap<>();

    private GameMode originalGameMode;
    private Location staticCameraLoc = null;
    private List<Location> cameraPath = null;
    private int cameraStep = 0;

    public CinematicSession(CinematicManager plugin, Player player, CinematicData data) {
        this.plugin = plugin;
        this.player = player;
        this.data = data;
        this.hasPapi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public CinematicSession(CinematicManager plugin, Player player, String id) {
        this(plugin, player, plugin.getConfigManager().getCinematic(id));
    }

    private String sanitize(String id) {
        if (id == null) return "";
        String clean = ChatColor.stripColor(id);
        if (clean.contains(">>")) clean = clean.split(">>")[1];
        else if (clean.contains("|")) clean = clean.split("\\|")[1];
        return clean.replace("[ID:", "").replace("[", "").replace("]", "").trim().toLowerCase();
    }

    public void start() {
        if (active || data == null) return;
        this.active = true;
        this.originalGameMode = player.getGameMode();
        this.maxTick = data.getLastTick();
        this.currentTick = 0;
        player.setGameMode(GameMode.SPECTATOR);
        runTicker();
    }

    private void runTicker() {
        this.ticker = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || !player.isOnline()) { stop(); return; }
                handleCameraPlayback();
                updateNpcMovements();
                List<CinematicAction> actions = data.getTimeline().get(currentTick);
                if (actions != null) {
                    for (CinematicAction action : actions) processAction(action);
                }
                if (currentTick > maxTick && movingNpcs.isEmpty()) { stop(); return; }
                currentTick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void updateNpcMovements() {
        if (movingNpcs.isEmpty()) return;
        Iterator<Map.Entry<String, ActivePath>> it = movingNpcs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActivePath> entry = it.next();
            ActivePath path = entry.getValue();
            Entity entity = findEntity(entry.getKey());
            int elapsed = currentTick - path.startTick;

            if (entity == null || !entity.isValid() || elapsed >= path.relativeLocations.size()) {
                it.remove();
                continue;
            }

            Location rel = path.relativeLocations.get(elapsed);
            // 기록된 상대 좌표를 NPC의 스폰 좌표(baseOrigin)에 더해 위치 복원
            Location finalLoc = path.baseOrigin.clone().add(rel.getX(), rel.getY(), rel.getZ());
            finalLoc.setYaw(rel.getYaw());
            finalLoc.setPitch(rel.getPitch());

            plugin.getNpcManager().move(player, entity, finalLoc);
        }
    }

    private Entity findEntity(String key) {
        if (key == null) return null;
        String searchKey = sanitize(key);

        if (activeEntities.containsKey(searchKey)) return activeEntities.get(searchKey);

        for (Map.Entry<String, Entity> entry : activeEntities.entrySet()) {
            if (entry.getKey().contains(searchKey) || searchKey.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void processAction(CinematicAction action) {
        switch (action.getType()) {
            case SPAWN_NPC -> handleSpawn(action);
            case MOVE_NPC -> handleNpcMove(action);
            case CAMERA -> handleCamera(action);
            case SOUND -> player.playSound(player.getLocation(), action.getValue(), 1f, 1f);
            case PARTICLE -> handleParticle(action);
            case TITLE -> handleTitle(action);
            case MESSAGE -> handleMessage(action);
            case COMMAND -> handleCommand(action);
            case HIDE_ENTITY -> handleHide(action);
            case SHOW_ENTITY -> handleShow(action);
            case ANIMATION -> {
                Entity e = findEntity(action.getExtra());
                if (e != null) {
                    String animValue = action.getValue();
                    if (hasPapi) animValue = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, animValue);
                    plugin.getNpcManager().playAnimation(player, e, animValue);
                }
            }
        }
    }

    private boolean isEntityType(String str) {
        if (str.equalsIgnoreCase("PLAYER")) return true;
        try {
            org.bukkit.entity.EntityType.valueOf(str.toUpperCase());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void handleSpawn(CinematicAction action) {
        String rawValue = action.getValue();
        String cleanKey = sanitize(rawValue);

        String spawnName = rawValue;
        if (hasPapi) spawnName = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, spawnName);

        Location loc = action.getLocation().clone();
        Entity npc = null;
        String lowerSpawn = spawnName.toLowerCase();

        if (lowerSpawn.contains("npc:")) {
            String val = spawnName.substring(lowerSpawn.indexOf("npc:") + 4);
            String[] split = val.split(":");

            String type = "PLAYER";
            String name = split[0];
            String skin = split.length > 1 ? split[1] : split[0];

            if (split.length >= 2 && isEntityType(split[0])) {
                type = split[0];
                name = split[1];
                skin = split.length > 2 ? split[2] : name;
            }

            npc = plugin.getNpcManager().spawnNPC(player, loc, type, name, skin);
        } else if (lowerSpawn.contains("mythicmobs:")) {
            npc = plugin.getNpcManager().spawnMythicMob(player, spawnName.substring(lowerSpawn.indexOf("mythicmobs:") + 11).trim(), loc);
        } else if (lowerSpawn.contains("modelengine:")) {
            npc = plugin.getNpcManager().spawnModelEngine(player, spawnName.substring(lowerSpawn.indexOf("modelengine:") + 12).trim(), loc);
        }

        if (npc != null) {
            activeEntities.put(cleanKey, npc);
            spawnLocations.put(cleanKey, loc);
        }
    }

    private void handleNpcMove(CinematicAction action) {
        String targetKey = sanitize(action.getExtra());
        List<Location> relativePath = data.getPathRecord(action.getValue());

        // 소환된 위치를 기준점으로 삼아 이동을 시작함
        Location origin = spawnLocations.get(targetKey);
        if (origin == null) {
            for (Map.Entry<String, Location> entry : spawnLocations.entrySet()) {
                if (entry.getKey().contains(targetKey) || targetKey.contains(entry.getKey())) {
                    origin = entry.getValue();
                    targetKey = entry.getKey();
                    break;
                }
            }
        }

        if (relativePath != null && !relativePath.isEmpty() && origin != null) {
            movingNpcs.put(targetKey, new ActivePath(relativePath, currentTick, origin));
        }
    }

    private void handleCamera(CinematicAction action) {
        if (action.getValue().equalsIgnoreCase("static")) {
            this.staticCameraLoc = action.getLocation();
            this.cameraPath = null;
        } else {
            List<Location> relativePath = data.getPathRecord(action.getValue());
            if (relativePath != null && !relativePath.isEmpty()) {
                Location origin = action.getLocation();
                List<Location> absolutePath = new ArrayList<>();
                for (Location rel : relativePath) {
                    Location abs = origin.clone().add(rel.getX(), rel.getY(), rel.getZ());
                    abs.setYaw(rel.getYaw());
                    abs.setPitch(rel.getPitch());
                    absolutePath.add(abs);
                }
                this.cameraPath = absolutePath;
                this.cameraStep = 0;
                this.staticCameraLoc = null;
            }
        }
    }

    private void handleCameraPlayback() {
        if (staticCameraLoc != null) player.teleport(staticCameraLoc);
        else if (cameraPath != null && cameraStep < cameraPath.size()) player.teleport(cameraPath.get(cameraStep++));
    }

    private void handleParticle(CinematicAction action) {
        try { player.spawnParticle(Particle.valueOf(action.getValue().toUpperCase()), action.getLocation(), 20, 0.5, 0.5, 0.5, 0.05); } catch (Exception ignored) {}
    }

    private void handleTitle(CinematicAction action) {
        String raw = action.getValue();
        if (hasPapi) raw = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, raw);
        String[] split = raw.split(";", 2);
        player.sendTitle(split[0].replace("&", "§"), split.length > 1 ? split[1].replace("&", "§") : "", 10, 70, 20);
    }

    private void handleMessage(CinematicAction action) {
        String msg = action.getValue();
        if (hasPapi) msg = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, msg);
        player.sendMessage(msg.replace("&", "§"));
    }

    private void handleCommand(CinematicAction action) {
        String cmd = action.getValue().replace("%player%", player.getName());
        if (hasPapi) cmd = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, cmd);
        if (cmd.startsWith("#")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring(1));
        else player.performCommand(cmd);
    }

    private void handleHide(CinematicAction action) {
        Entity e = findEntity(action.getValue());
        if (e != null) e.teleport(e.getLocation().add(0, -100, 0));
    }

    private void handleShow(CinematicAction action) {
        Entity e = findEntity(action.getExtra() != null ? action.getExtra() : action.getValue());
        if (e != null) e.teleport(action.getLocation());
    }

    public void stop() {
        if (!active) return;
        this.active = false;
        if (ticker != null) ticker.cancel();
        activeEntities.values().forEach(e -> plugin.getNpcManager().remove(e));
        activeEntities.clear(); movingNpcs.clear(); spawnLocations.clear();
        if (player.isOnline()) player.setGameMode(originalGameMode);
    }

    public void skip() { stop(); }
    public boolean isActive() { return active; }

    private static class ActivePath {
        final List<Location> relativeLocations;
        final int startTick;
        final Location baseOrigin;

        ActivePath(List<Location> relativeLocations, int startTick, Location baseOrigin) {
            this.relativeLocations = relativeLocations;
            this.startTick = startTick;
            this.baseOrigin = baseOrigin;
        }
    }
}