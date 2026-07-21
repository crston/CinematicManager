package com.gmail.bobason01.cinematicmanager.listener;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import com.gmail.bobason01.cinematicmanager.session.CinematicSession;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class CinematicControlListener implements Listener {

    private final CinematicManager plugin;

    public CinematicControlListener(CinematicManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getSessionManager().handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getSessionManager().stopSession(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectateTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) return;
        CinematicSession session = plugin.getSessionManager().getSession(event.getPlayer());
        if (session != null && session.isActive()) {
            event.setCancelled(true);
            if (session.isWaitingForInput()) {
                session.advanceDialogue();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // 대부분의 이동 이벤트는 세션 없음 → 맵 조회만 하고 즉시 탈출
        CinematicSession session = plugin.getSessionManager().getSession(event.getPlayer());
        if (session == null) return;
        if (session.enforceStaticCamera(event)) return;
        if (!session.isPaused() || session.isWaitingForInput()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // 미세 look 스킵: 블록 이동/유의미한 회전만
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && Math.abs(from.getYaw() - to.getYaw()) < 1.0
                && Math.abs(from.getPitch() - to.getPitch()) < 1.0) {
            return;
        }

        session.setPaused(false);
        event.getPlayer().sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_PAUSE_RESUME));
    }
}