package com.gmail.bobason01.cinematicmanager.fx;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EnvironmentRecordManager {
    private final CinematicManager plugin;
    private final Map<UUID, EnvironmentRecorder> active = new ConcurrentHashMap<>();

    public EnvironmentRecordManager(CinematicManager plugin) {
        this.plugin = plugin;
    }

    public EnvironmentRecorder get(Player player) {
        return player == null ? null : active.get(player.getUniqueId());
    }

    public boolean isRecording(Player player) {
        return get(player) != null;
    }

    public void start(Player player, String cinematicId, int startTick) {
        if (player == null) return;
        EnvironmentRecorder previous = active.remove(player.getUniqueId());
        // previous finishes itself via sneak; force drop if somehow stuck
        if (previous != null) {
            plugin.getLogger().info("Replacing active environment recorder for " + player.getName());
        }
        EnvironmentRecorder recorder = new EnvironmentRecorder(plugin, player, cinematicId, startTick);
        active.put(player.getUniqueId(), recorder);
        recorder.start();
    }

    public void clear(Player player) {
        if (player != null) active.remove(player.getUniqueId());
    }

    public void unregister(Player player) {
        clear(player);
    }
}
