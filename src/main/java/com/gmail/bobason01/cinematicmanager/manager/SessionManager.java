package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.session.CinematicSession;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final CinematicManager plugin;
    private final Map<UUID, CinematicSession> sessions = new ConcurrentHashMap<>(32);

    public SessionManager(CinematicManager plugin) {
        this.plugin = plugin;
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
        if (player == null || id == null) return;

        UUID uuid = player.getUniqueId();

        CinematicSession oldSession = sessions.get(uuid);
        if (oldSession != null) {
            oldSession.stop();
        }

        CinematicSession session = new CinematicSession(plugin, player, plugin.getDataManager().getCinematic(id));
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

        sessions.values().forEach(CinematicSession::stop);
        sessions.clear();
    }
}