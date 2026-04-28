package com.gmail.bobason01.cinematicmanager.session;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import com.gmail.bobason01.cinematicmanager.manager.LangManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class CinematicSession {

    private final CinematicManager plugin;
    private final Player player;
    private final CinematicData data;

    private boolean active = false;
    private boolean paused = false;
    private boolean hasPapi = false;
    private BukkitTask ticker;
    private int currentTick = 0;
    private int maxTick = 0;

    private Location originLocation;
    private GameMode originalGameMode;

    private final Map<String, Entity> activeEntities = new ConcurrentHashMap<>();
    private final Map<String, Location> spawnLocations = new ConcurrentHashMap<>();
    private final Map<String, ActivePath> movingNpcs = new ConcurrentHashMap<>();

    private Location staticCameraLoc = null;
    private List<Location> cameraPath = null;
    private int cameraStep = 0;

    public CinematicSession(CinematicManager plugin, Player player, String id) {
        this(plugin, player, plugin.getDataManager().getCinematic(id));
    }

    public CinematicSession(CinematicManager plugin, Player player, CinematicData data) {
        this.plugin = plugin;
        this.player = player;
        this.data = data;
        this.hasPapi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public void start() {
        if (active || data == null) return;
        this.active = true;
        this.originLocation = player.getLocation().clone();
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
                if (paused) return;

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

    /**
     * 일시 중단 상태를 설정하고 다국어 메시지를 출력합니다.
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
        LangManager lang = plugin.getLangManager();

        if (paused) {
            // 다국어 처리된 타이틀 및 메시지 전송
            player.sendTitle(" ", lang.get(LangKey.MSG_PAUSE_TITLE), 0, 60, 10);
            player.sendMessage(lang.getPrefixed(LangKey.MSG_PAUSE_SUBTITLE));
        } else {
            // 재개 시 타이틀 제거
            player.sendTitle("", "", 0, 5, 0);
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void skip() {
        stop();
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
            case LIGHTNING -> handleLightning(action);
            case ANIMATION -> handleAnimation(action);
        }
    }

    private void handleSpawn(CinematicAction action) {
        String spawnName = action.getValue();
        if (hasPapi) spawnName = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, spawnName);

        Location loc = action.getLocation();
        Entity npc = null;
        String lower = spawnName.toLowerCase();

        if (lower.contains("npc:")) {
            String val = spawnName.substring(lower.indexOf("npc:") + 4);
            String[] split = val.split(":");
            String type = "PLAYER", name = split[0], skin = split.length > 1 ? split[1] : split[0];
            if (split.length >= 2 && isEntityType(split[0])) {
                type = split[0]; name = split[1]; skin = split.length > 2 ? split[2] : name;
            }
            npc = plugin.getNpcManager().spawnNPC(player, loc, type, name, skin);
        } else if (lower.contains("mythicmobs:")) {
            npc = plugin.getNpcManager().spawnMythicMob(player, spawnName.substring(lower.indexOf("mythicmobs:") + 11).trim(), loc);
        } else if (lower.contains("modelengine:")) {
            npc = plugin.getNpcManager().spawnModelEngine(player, spawnName.substring(lower.indexOf("modelengine:") + 12).trim(), loc);
        }

        if (npc != null) {
            String key = sanitize(action.getValue());
            activeEntities.put(key, npc);
            spawnLocations.put(key, loc);
        }
    }

    private void handleNpcMove(CinematicAction action) {
        String targetKey = sanitize(action.getExtra());
        List<Location> relativePath = data.getPathRecord(action.getValue());
        Location origin = spawnLocations.get(targetKey);
        if (relativePath != null && origin != null) {
            movingNpcs.put(targetKey, new ActivePath(relativePath, currentTick, origin));
        }
    }

    private void handleCamera(CinematicAction action) {
        if (action.getValue().equalsIgnoreCase("static")) {
            this.staticCameraLoc = action.getLocation();
            this.cameraPath = null;
        } else {
            List<Location> relativePath = data.getPathRecord(action.getValue());
            if (relativePath != null) {
                Location origin = action.getLocation();
                List<Location> absolutePath = new ArrayList<>();
                for (Location rel : relativePath) {
                    Location abs = origin.clone().add(rel.getX(), rel.getY(), rel.getZ());
                    abs.setYaw(rel.getYaw()); abs.setPitch(rel.getPitch());
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
        else if (cameraPath != null && cameraStep < cameraPath.size()) {
            player.teleport(cameraPath.get(cameraStep++));
        }
    }

    private void updateNpcMovements() {
        Iterator<Map.Entry<String, ActivePath>> it = movingNpcs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActivePath> entry = it.next();
            ActivePath path = entry.getValue();
            Entity entity = findEntity(entry.getKey());
            int elapsed = currentTick - path.startTick;
            if (entity == null || !entity.isValid() || elapsed >= path.relativeLocations.size()) {
                it.remove(); continue;
            }
            Location rel = path.relativeLocations.get(elapsed);
            Location finalLoc = path.baseOrigin.clone().add(rel.getX(), rel.getY(), rel.getZ());
            finalLoc.setYaw(rel.getYaw()); finalLoc.setPitch(rel.getPitch());
            plugin.getNpcManager().move(player, entity, finalLoc);
        }
    }

    private void handleAnimation(CinematicAction action) {
        Entity e = findEntity(action.getExtra());
        if (e != null) {
            String val = action.getValue();
            if (hasPapi) val = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, val);
            plugin.getNpcManager().playAnimation(player, e, val);
        }
    }

    private void handleCommand(CinematicAction action) {
        String cmd = action.getValue().replace("%player%", player.getName());
        if (hasPapi) cmd = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, cmd);
        final String finalCmd = cmd;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (finalCmd.startsWith("#")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd.substring(1).trim());
            else player.performCommand(finalCmd.startsWith("/") ? finalCmd.substring(1) : finalCmd);
        });
    }

    private void handleLightning(CinematicAction action) {
        Location loc = action.getLocation();
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        packet.getIntegers().write(0, ThreadLocalRandom.current().nextInt(100000, 200000));
        packet.getUUIDs().write(0, UUID.randomUUID());
        packet.getEntityTypeModifier().write(0, EntityType.LIGHTNING_BOLT);
        packet.getDoubles().write(0, loc.getX()).write(1, loc.getY()).write(2, loc.getZ());
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
            player.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 10f, 1f);
        } catch (Exception ignored) {}
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

    private void handleHide(CinematicAction action) {
        Entity e = findEntity(action.getValue());
        if (e != null) e.teleport(e.getLocation().add(0, -100, 0));
    }

    private void handleShow(CinematicAction action) {
        Entity e = findEntity(action.getExtra() != null ? action.getExtra() : action.getValue());
        if (e != null) e.teleport(action.getLocation());
    }

    private Entity findEntity(String key) {
        if (key == null) return null;
        String sk = sanitize(key);
        if (activeEntities.containsKey(sk)) return activeEntities.get(sk);
        for (Map.Entry<String, Entity> entry : activeEntities.entrySet()) {
            if (entry.getKey().contains(sk) || sk.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private String sanitize(String id) {
        if (id == null) return "";
        return ChatColor.stripColor(id).toLowerCase().trim();
    }

    private boolean isEntityType(String s) {
        try { EntityType.valueOf(s.toUpperCase()); return true; } catch (Exception e) { return false; }
    }

    public void stop() {
        if (!active) return;
        active = false;
        if (ticker != null) ticker.cancel();
        activeEntities.values().forEach(e -> plugin.getNpcManager().remove(e));
        activeEntities.clear(); movingNpcs.clear(); spawnLocations.clear();
        if (player.isOnline()) {
            player.setGameMode(originalGameMode);
            player.teleport(originLocation);
        }
    }

    public boolean isActive() {
        return active;
    }

    private static class ActivePath {
        final List<Location> relativeLocations;
        final int startTick;
        final Location baseOrigin;
        ActivePath(List<Location> rl, int st, Location bo) {
            this.relativeLocations = rl; this.startTick = st; this.baseOrigin = bo;
        }
    }
}