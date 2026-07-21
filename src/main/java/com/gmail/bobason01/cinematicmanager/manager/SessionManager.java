package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.api.event.CinematicStartEvent;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import com.gmail.bobason01.cinematicmanager.session.CinematicSession;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final CinematicManager plugin;
    private final Map<UUID, CinematicSession> sessions = new ConcurrentHashMap<>(32);
    private final Map<UUID, PendingRestore> pendingRestores = new ConcurrentHashMap<>();
    private final BukkitTask ticker;
    private boolean shuttingDown;

    public SessionManager(CinematicManager plugin) {
        this.plugin = plugin;
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSessions, 0L, 1L);
    }

    private void tickSessions() {
        if (sessions.isEmpty()) return;
        sessions.forEach((uuid, session) -> {
            try {
                session.tick();
            } catch (Throwable error) {
                plugin.getLogger().severe("Cinematic session failed for " + uuid + ": "
                        + error.getClass().getSimpleName() + ": " + error.getMessage());
                try {
                    session.stop();
                } catch (Throwable cleanupError) {
                    plugin.getLogger().severe("Cinematic cleanup also failed for " + uuid + ": "
                            + cleanupError.getMessage());
                }
            }
            if (!session.isActive()) {
                sessions.remove(uuid, session);
            }
        });
    }

    public boolean isPlaying(Player player) {
        if (player == null) return false;
        CinematicSession session = sessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    public CinematicSession getSession(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public void startSession(Player player, String id) {
        if (shuttingDown || player == null || !player.isOnline() || id == null) return;
        CinematicData data = plugin.getDataManager().getCinematic(id);
        if (data == null) return;

        // 시네마틱 시작 이벤트 호출
        CinematicStartEvent event = new CinematicStartEvent(player, id);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        UUID uuid = player.getUniqueId();

        CinematicSession oldSession = sessions.remove(uuid);
        if (oldSession != null) {
            oldSession.stop();
            // An end-event listener may intentionally have started another session.
            if (sessions.containsKey(uuid)) return;
        }

        CinematicSession session = new CinematicSession(plugin, player, data);
        sessions.put(uuid, session);
        session.start();
    }

    public void stopSession(Player player) {
        if (player == null) return;

        CinematicSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.stop();
        }
    }

    public void stopAll() {
        if (sessions.isEmpty()) return;

        CinematicSession[] snapshot = sessions.values().toArray(CinematicSession[]::new);
        sessions.clear();
        for (CinematicSession session : snapshot) {
            session.stop();
        }
    }

    public void shutdown() {
        shuttingDown = true;
        ticker.cancel();
        stopAll();
    }

    public void handlePlayerJoin(Player joiningPlayer) {
        if (joiningPlayer == null) return;
        UUID uuid = joiningPlayer.getUniqueId();
        PendingRestore restore = pendingRestores.get(uuid);
        if (restore != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!joiningPlayer.isOnline()) return;
                if (!pendingRestores.remove(uuid, restore)) return;
                joiningPlayer.setGameMode(restore.gameMode());
                joiningPlayer.teleport(restore.location());
            });
        }
        if (!sessions.isEmpty()) {
            sessions.values().forEach(session -> session.hideEntitiesFrom(joiningPlayer));
        }
    }

    public void queuePlayerRestore(UUID playerId, GameMode gameMode, Location location) {
        if (playerId == null || gameMode == null || location == null) return;
        pendingRestores.put(playerId, new PendingRestore(gameMode, location.clone()));
    }

    private record PendingRestore(GameMode gameMode, Location location) {
    }
}