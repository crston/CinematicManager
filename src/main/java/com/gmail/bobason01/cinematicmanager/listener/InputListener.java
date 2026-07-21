package com.gmail.bobason01.cinematicmanager.listener;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import com.gmail.bobason01.cinematicmanager.session.CinematicSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
        CinematicSession session = plugin.getSessionManager().getSession(player);
        if (session == null || !session.isActive()) {
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
        CinematicSession session = plugin.getSessionManager().getSession(player);
        if (session == null || !session.isActive()) {
            return;
        }

        event.setCancelled(true);

        if (session.isWaitingForInput()) {
            return;
        }

        fPressTime.put(player.getUniqueId(), System.currentTimeMillis());
        checkSkipCondition(player);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        CinematicSession session = plugin.getSessionManager().getSession(player);
        if (session == null || !session.isActive()) {
            return;
        }

        event.setCancelled(true);

        if (!session.isWaitingForInput()) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // The proxy is a client-only fake block. Bukkit may report the real
        // server block (often AIR), so coordinate equality is not reliable.
        tryAdvance(player, session);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        handleEntityInteraction(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleEntityInteraction(event);
    }

    private void handleEntityInteraction(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        CinematicSession session = plugin.getSessionManager().getSession(player);
        if (session == null || !session.isActive()) return;

        // Spectator right-click normally attaches the camera to this entity.
        event.setCancelled(true);
        if (session.isWaitingForInput()) {
            tryAdvance(player, session);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        shiftPressTime.remove(uuid);
        fPressTime.remove(uuid);
    }

    private void tryAdvance(Player player, CinematicSession session) {
        if (session.advanceDialogue()) {
            fPressTime.remove(player.getUniqueId());
        }
    }

    private void checkSkipCondition(Player player) {
        boolean requireBoth = plugin.getConfig().getBoolean("skip-settings.require-both", false);
        boolean useShift = plugin.getConfig().getBoolean("skip-settings.use-shift", true);
        boolean useF = plugin.getConfig().getBoolean("skip-settings.use-f", false);

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
                player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_PAUSE_SKIP));
            }
        }
    }
}
