package com.gmail.bobason01.cinematicmanager.listener;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import com.gmail.bobason01.cinematicmanager.session.CinematicSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InputListener implements Listener {

    private static final long ADVANCE_COOLDOWN_MS = 250L;

    private final CinematicManager plugin;
    private final Map<UUID, Long> shiftPressTime;
    private final Map<UUID, Long> fPressTime;
    private final Map<UUID, Long> advanceCooldown;

    public InputListener(CinematicManager plugin) {
        this.plugin = plugin;
        this.shiftPressTime = new HashMap<>();
        this.fPressTime = new HashMap<>();
        this.advanceCooldown = new HashMap<>();
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

        if (action == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && !session.isClickProxy(event.getClickedBlock().getLocation())) {
            return;
        }

        tryAdvance(player, session);
    }

    private void tryAdvance(Player player, CinematicSession session) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = advanceCooldown.get(uuid);
        if (last != null && now - last < ADVANCE_COOLDOWN_MS) {
            return;
        }
        if (session.advanceDialogue()) {
            advanceCooldown.put(uuid, now);
            fPressTime.remove(uuid);
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
