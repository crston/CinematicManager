package com.gmail.bobason01.cinematicmanager.listener;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import com.gmail.bobason01.cinematicmanager.session.CinematicSession;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class CinematicControlListener implements Listener {

    private final CinematicManager plugin;

    public CinematicControlListener(CinematicManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // 대부분의 이동 이벤트는 세션 없음 → 맵 조회만 하고 즉시 탈출
        CinematicSession session = plugin.getSessionManager().getSession(event.getPlayer());
        if (session == null || !session.isPaused() || session.isWaitingForInput()) return;

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