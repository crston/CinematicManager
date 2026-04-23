package com.gmail.bobason01.cinematicmanager.listener;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import com.gmail.bobason01.cinematicmanager.manager.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatInputListener implements Listener {

    private final CinematicManager plugin;
    private final Map<Player, InputContext> inputQueue = new ConcurrentHashMap<>();

    public ChatInputListener(CinematicManager plugin) {
        this.plugin = plugin;
    }

    public void startCreationInput(Player player) {
        inputQueue.put(player, new InputContext("CREATE", null, 0, null, ""));
    }

    public void startTrackInput(Player player, String id, String type, int tick) {
        startTrackInput(player, id, type, tick, "");
    }

    public void startTrackInput(Player player, String id, String type, int tick, String prefix) {
        inputQueue.put(player, new InputContext(type, id, tick, player.getLocation().clone(), prefix));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        InputContext context = inputQueue.remove(player);
        if (context == null) return;

        event.setCancelled(true);
        final String message = event.getMessage();
        LangManager lang = plugin.getLangManager();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (context.type.equals("CREATE")) {
                plugin.getDataManager().createCinematic(message);
                player.sendMessage(lang.getPrefixed(LangKey.MSG_CREATE_SUCCESS));
                plugin.getGuiManager().openStudioGUI(player, message, 0);
                return;
            }

            if (context.type.equals("CUSTOM_TYPE")) {
                String typeStr = message.toUpperCase();
                try {
                    EntityType.valueOf(typeStr);
                    player.sendMessage(lang.format(LangKey.MSG_INPUT_CUSTOM_TYPE_CONFIRM, "{type}", typeStr));
                    player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_SPAWN_NPC_MOB));
                    startTrackInput(player, context.id, "SPAWN", context.tick, context.prefix + typeStr + ":");
                } catch (IllegalArgumentException e) {
                    player.sendMessage(lang.getPrefixed(LangKey.MSG_ERROR_INVALID_TYPE));
                    startTrackInput(player, context.id, "CUSTOM_TYPE", context.tick, context.prefix);
                }
                return;
            }

            CinematicData data = plugin.getDataManager().getCinematic(context.id);
            if (data == null) return;

            CinematicAction.ActionType actionType = null;
            String processedValue = message;
            String extra = null;

            switch (context.type) {
                case "SPAWN" -> {
                    actionType = CinematicAction.ActionType.SPAWN_NPC;
                    processedValue = context.prefix + message;
                }
                case "SOUND" -> actionType = CinematicAction.ActionType.SOUND;
                case "PARTICLE" -> actionType = CinematicAction.ActionType.PARTICLE;
                case "TITLE" -> actionType = CinematicAction.ActionType.TITLE;
                case "MESSAGE" -> actionType = CinematicAction.ActionType.MESSAGE;
                case "STATE" -> {
                    actionType = CinematicAction.ActionType.ANIMATION;
                    extra = getMetadata(player, "edit_npc_target");
                }
                case "STOP" -> {
                    actionType = CinematicAction.ActionType.ANIMATION;
                    processedValue = "STOP:" + message;
                    extra = getMetadata(player, "edit_npc_target");
                }
                case "SCALE" -> {
                    actionType = CinematicAction.ActionType.SCALE;
                    extra = getMetadata(player, "edit_npc_target");
                }
            }

            if (actionType != null) {
                CinematicAction action = new CinematicAction(actionType, processedValue, context.loc, extra);
                data.addAction(context.tick, action);
                plugin.getDataManager().saveCinematic(data);
                player.sendMessage(lang.getPrefixed(LangKey.MSG_SAVE_SUCCESS));
            }

            int targetPage = context.tick / 180;
            plugin.getGuiManager().openStudioGUI(player, context.id, targetPage);
        });
    }

    private String getMetadata(Player player, String key) {
        if (player.hasMetadata(key)) {
            List<MetadataValue> values = player.getMetadata(key);
            if (!values.isEmpty()) return values.get(0).asString();
        }
        return null;
    }

    private static class InputContext {
        final String type;
        final String id;
        final int tick;
        final Location loc;
        final String prefix;

        InputContext(String type, String id, int tick, Location loc, String prefix) {
            this.type = type;
            this.id = id;
            this.tick = tick;
            this.loc = loc;
            this.prefix = prefix;
        }
    }
}