package com.gmail.bobason01.cinematicmanager.listener;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.session.CinematicSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InputListener implements Listener {

    private final CinematicManager plugin;
    private final Map<UUID, Long> shiftPressTime;
    private final Map<UUID, Long> fPressTime;

    public InputListener(CinematicManager plugin) {
        this.plugin = plugin;
        this.shiftPressTime = new HashMap<>();
        this.fPressTime = new HashMap<>();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getSessionManager().isPlaying(player)) {
            return;
        }

        event.setCancelled(true);

        if (event.isSneaking()) {
            shiftPressTime.put(player.getUniqueId(), System.currentTimeMillis());
            checkSkipCondition(player);
        } else {
            shiftPressTime.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getSessionManager().isPlaying(player)) {
            return;
        }

        event.setCancelled(true);
        fPressTime.put(player.getUniqueId(), System.currentTimeMillis());
        checkSkipCondition(player);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSessionManager().isPlaying(player)) {
            event.setCancelled(true);
        }
    }

    private void checkSkipCondition(Player player) {
        // 에러 해결: ConfigManager 대신 성능이 더 빠르고 직관적인 Bukkit 기본 API를 직접 호출
        boolean requireBoth = plugin.getConfig().getBoolean("skip.require_both", false);
        boolean useShift = plugin.getConfig().getBoolean("skip.use_shift", true);
        boolean useF = plugin.getConfig().getBoolean("skip.use_f", true);

        UUID uuid = player.getUniqueId();
        boolean shiftPressed = shiftPressTime.containsKey(uuid);
        boolean fPressed = fPressTime.containsKey(uuid);

        long currentTime = System.currentTimeMillis();

        if (fPressed && (currentTime - fPressTime.get(uuid) > 500)) {
            fPressed = false;
            fPressTime.remove(uuid);
        }

        boolean shouldSkip = false;

        if (requireBoth) {
            if (shiftPressed && fPressed) {
                shouldSkip = true;
            }
        } else {
            if ((useShift && shiftPressed) || (useF && fPressed)) {
                shouldSkip = true;
            }
        }

        if (shouldSkip) {
            shiftPressTime.remove(uuid);
            fPressTime.remove(uuid);
            CinematicSession session = plugin.getSessionManager().getSession(player);
            if (session != null) {
                session.skip();
            }
        }
    }
}