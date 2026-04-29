package com.gmail.bobason01.cinematicmanager.listener;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import com.gmail.bobason01.cinematicmanager.session.CinematicSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class CinematicControlListener implements Listener {

    private final CinematicManager plugin;

    public CinematicControlListener(CinematicManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        CinematicSession session = plugin.getSessionManager().getSession(player);

        if (session != null && session.isPaused()) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;

            double distSq = from.distanceSquared(to);
            boolean rotated = Math.abs(from.getYaw() - to.getYaw()) > 0.1 ||
                    Math.abs(from.getPitch() - to.getPitch()) > 0.1;

            if (distSq > 0.0001 || rotated) {
                session.setPaused(false);
                // 다국어 메시지 출력
                player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_PAUSE_RESUME));
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        CinematicSession session = plugin.getSessionManager().getSession(player);

        if (session != null) {
            session.skip();
            // 다국어 메시지 출력
            player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_PAUSE_SKIP));
        }
    }
}