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
        player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_INPUT_NAME));
    }

    public void startNpcPresetEdit(Player player, String presetId) {
        var preset = plugin.getNpcPresetManager().getPreset(presetId);
        if (preset == null) return;
        inputQueue.put(player, new InputContext("NPC_EDIT", presetId, 0, null, ""));
        LangManager lang = plugin.getLangManager();
        String provider = preset.getProvider() == null ? "vanilla" : preset.getProvider().toLowerCase();
        switch (provider) {
            case "mythicmobs" -> player.sendMessage(lang.format(
                    LangKey.MSG_INPUT_NPC_EDIT_MM, "{id}", presetId, "{mob}",
                    preset.getMobId() == null ? "" : preset.getMobId()));
            case "modelengine" -> player.sendMessage(lang.format(
                    LangKey.MSG_INPUT_NPC_EDIT_ME, "{id}", presetId, "{mob}",
                    preset.getMobId() == null ? "" : preset.getMobId()));
            default -> player.sendMessage(lang.format(
                    LangKey.MSG_INPUT_NPC_EDIT_VANILLA, "{id}", presetId,
                    "{type}", preset.getEntityType() == null ? "PLAYER" : preset.getEntityType(),
                    "{name}", preset.getName() == null ? presetId : preset.getName(),
                    "{skin}", preset.getSkin() == null || preset.getSkin().isBlank()
                            ? (preset.getName() == null ? presetId : preset.getName())
                            : preset.getSkin()));
        }
    }

    public void startTrackInput(Player player, String id, String type, int tick) {
        startTrackInput(player, id, type, tick, "");
    }

    public void startTrackInput(Player player, String id, String type, int tick, String prefix) {
        inputQueue.put(player, new InputContext(type, id, tick, player.getLocation().clone(), prefix));

        LangManager lang = plugin.getLangManager();
        switch (type) {
            case "SOUND" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_SOUND));
            case "PARTICLE" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_PARTICLE));
            case "TITLE" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_TITLE));
            case "MESSAGE" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_MESSAGE));
            case "DIALOGUE", "DIALOGUE_TITLE", "DIALOGUE_ACTIONBAR" ->
                    player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_DIALOGUE));
            case "WAIT" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_WAIT));
            case "COMMAND" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_COMMAND));
            case "CUSTOM_TYPE" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_CUSTOM_TYPE));
            case "NPC_PRESET" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_NPC_PRESET));
            case "NPC_CREATE_PLAYER" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_NPC_CREATE_PLAYER));
            case "NPC_CREATE_MM" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_NPC_CREATE_MM));
            case "NPC_CREATE_ME" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_NPC_CREATE_ME));
            case "STATE", "STOP" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_ANIMATION));
            case "GESTURE" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_GESTURE));
            case "AM_PLAY" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_AM));
            case "REMAP" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_REMAP));
            case "CHANGEPART" -> player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_CHANGEPART));
            case "SPAWN" -> {
                if (prefix.contains("mythicmobs")) player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_SPAWN_MM));
                else if (prefix.contains("modelengine")) player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_SPAWN_ME));
                else player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_SPAWN_NPC_PLAYER));
            }
        }
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
                plugin.getDataManager().createCinematic(message, player.getLocation());
                player.sendMessage(lang.getPrefixed(LangKey.MSG_CREATE_SUCCESS));
                plugin.getGuiManager().openStudioGUI(player, message, 0);
                return;
            }

            if (context.type.equals("NPC_PRESET")
                    || context.type.equals("NPC_CREATE_PLAYER")
                    || context.type.equals("NPC_CREATE_MM")
                    || context.type.equals("NPC_CREATE_ME")
                    || context.type.equals("NPC_EDIT")) {
                boolean ok = switch (context.type) {
                    case "NPC_CREATE_PLAYER" -> saveNpcCreatePlayer(player, message);
                    case "NPC_CREATE_MM" -> saveNpcCreateProvider(player, message, "mythicmobs");
                    case "NPC_CREATE_ME" -> saveNpcCreateProvider(player, message, "modelengine");
                    case "NPC_EDIT" -> saveNpcEdit(player, message, context.id);
                    default -> saveNpcPreset(player, message);
                };
                if (ok) player.sendMessage(lang.getPrefixed(LangKey.MSG_NPC_PRESET_SAVED));
                boolean pick = "true".equalsIgnoreCase(getMetadata(player, "npc_pick_mode"));
                int page = 0;
                try {
                    String pg = getMetadata(player, "npc_preset_page");
                    if (pg != null) page = Integer.parseInt(pg);
                } catch (NumberFormatException ignored) {}
                plugin.getGuiManager().openNpcPresetMenu(player, page, pick);
                return;
            }

            if (context.type.equals("CUSTOM_TYPE")) {
                try {
                    EntityType.valueOf(message.toUpperCase());
                    player.sendMessage(lang.format(LangKey.MSG_INPUT_CUSTOM_TYPE_CONFIRM, "{type}", message.toUpperCase()));
                    startTrackInput(player, context.id, "SPAWN", context.tick, context.prefix + message.toUpperCase() + ":");
                } catch (Exception e) {
                    player.sendMessage(lang.getPrefixed(LangKey.MSG_ERROR_INVALID_TYPE));
                    startTrackInput(player, context.id, "CUSTOM_TYPE", context.tick, context.prefix);
                }
                return;
            }

            String editId = getMetadata(player, "edit_id");
            String targetId = (editId != null) ? editId : context.id;
            int targetTick = context.tick;
            String metaTick = getMetadata(player, "edit_tick");
            if (metaTick != null) targetTick = Integer.parseInt(metaTick);

            CinematicData data = plugin.getDataManager().getCinematic(targetId);
            if (data == null) return;

            CinematicAction.ActionType actionType = switch (context.type) {
                case "SPAWN" -> CinematicAction.ActionType.SPAWN_NPC;
                case "SOUND" -> CinematicAction.ActionType.SOUND;
                case "PARTICLE" -> CinematicAction.ActionType.PARTICLE;
                case "TITLE" -> CinematicAction.ActionType.TITLE;
                case "MESSAGE" -> CinematicAction.ActionType.MESSAGE;
                case "DIALOGUE", "DIALOGUE_TITLE", "DIALOGUE_ACTIONBAR" -> CinematicAction.ActionType.DIALOGUE;
                case "WAIT" -> CinematicAction.ActionType.WAIT;
                case "COMMAND" -> CinematicAction.ActionType.COMMAND;
                case "STATE", "STOP" -> CinematicAction.ActionType.ANIMATION;
                case "GESTURE" -> CinematicAction.ActionType.GESTURE;
                case "AM_PLAY" -> CinematicAction.ActionType.AM_PLAY;
                case "REMAP" -> CinematicAction.ActionType.REMAP_MODEL;
                case "CHANGEPART" -> CinematicAction.ActionType.CHANGE_PART;
                default -> null;
            };

            if (actionType != null) {
                String val = context.prefix + message;
                String extra = null;
                if (context.type.equals("STATE") || context.type.equals("STOP")
                        || context.type.equals("REMAP") || context.type.equals("CHANGEPART")
                        || context.type.equals("GESTURE") || context.type.equals("AM_PLAY")) {
                    extra = getMetadata(player, "edit_npc_target");
                    if (context.type.equals("STOP")) val = "STOP:" + message;
                }
                if (context.type.equals("DIALOGUE_TITLE")) {
                    extra = "title";
                } else if (context.type.equals("DIALOGUE_ACTIONBAR")) {
                    extra = "actionbar";
                } else if (context.type.equals("DIALOGUE")) {
                    // Legacy single dialogue button → title only
                    extra = "title";
                }
                if (context.type.equals("WAIT") && (message.equals("-") || message.equalsIgnoreCase("none"))) {
                    val = "";
                }
                data.addAction(targetTick, new CinematicAction(actionType, val, context.loc, extra));
                plugin.getDataManager().saveCinematic(data);
                if (context.type.startsWith("DIALOGUE")) {
                    player.sendMessage(lang.getPrefixed(LangKey.MSG_DIALOGUE_ADDED));
                } else if (context.type.equals("WAIT")) {
                    player.sendMessage(lang.getPrefixed(LangKey.MSG_WAIT_ADDED));
                } else {
                    player.sendMessage(lang.getPrefixed(LangKey.MSG_SAVE_SUCCESS));
                }
            }
            plugin.getGuiManager().openStudioGUI(player, targetId, targetTick / 180);
        });
    }

    private boolean saveNpcEdit(Player player, String message, String presetId) {
        var preset = plugin.getNpcPresetManager().getPreset(presetId);
        if (preset == null) return false;
        String raw = message == null ? "" : message.trim();
        if (raw.isEmpty() || raw.equalsIgnoreCase("cancel") || raw.equals("-")) {
            return false;
        }
        String provider = preset.getProvider() == null ? "vanilla" : preset.getProvider().toLowerCase();
        switch (provider) {
            case "mythicmobs", "modelengine" -> preset.setMobId(raw);
            default -> {
                String[] npc = raw.split(":", 3);
                String type = npc[0].trim().toUpperCase();
                try {
                    EntityType.valueOf(type);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_ERROR_INVALID_TYPE));
                    return false;
                }
                preset.setProvider("vanilla");
                preset.setEntityType(type);
                if (npc.length >= 2 && !npc[1].isBlank()) {
                    preset.setName(npc[1].trim());
                }
                if (npc.length >= 3) {
                    String skin = npc[2].trim();
                    preset.setSkin(skin.isEmpty() ? preset.getName() : skin);
                } else if (npc.length == 2 && !npc[1].isBlank()
                        && (preset.getSkin() == null || preset.getSkin().isBlank())) {
                    preset.setSkin(preset.getName());
                }
            }
        }
        return plugin.getNpcPresetManager().save(preset);
    }

    private boolean saveNpcCreatePlayer(Player player, String message) {
        String raw = message == null ? "" : message.trim();
        if (raw.isEmpty()) {
            player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_INPUT_NPC_CREATE_PLAYER));
            return false;
        }
        String[] parts = raw.split(":", 2);
        String name = parts[0].trim();
        String skin = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : name;
        if (name.isEmpty()) return false;
        String id = uniqueNpcId(name);
        var preset = new com.gmail.bobason01.cinematicmanager.data.NpcPreset(id);
        preset.setProvider("vanilla");
        preset.setEntityType("PLAYER");
        preset.setName(name);
        preset.setSkin(skin);
        return plugin.getNpcPresetManager().save(preset);
    }

    private boolean saveNpcCreateProvider(Player player, String message, String provider) {
        String mobId = message == null ? "" : message.trim();
        if (mobId.isEmpty()) return false;
        String id = uniqueNpcId(mobId);
        var preset = new com.gmail.bobason01.cinematicmanager.data.NpcPreset(id);
        preset.setProvider(provider);
        preset.setMobId(mobId);
        return plugin.getNpcPresetManager().save(preset);
    }

    private String uniqueNpcId(String raw) {
        String base = raw.replaceAll("[^A-Za-z0-9_-]", "_");
        while (base.startsWith("_")) base = base.substring(1);
        if (base.isBlank()) base = "npc";
        if (base.length() > 48) base = base.substring(0, 48);
        String candidate = base;
        int n = 2;
        while (!plugin.getNpcPresetManager().isSafeId(candidate)
                || plugin.getNpcPresetManager().getPreset(candidate) != null) {
            String suffix = "_" + n++;
            candidate = base.substring(0, Math.min(base.length(), 64 - suffix.length())) + suffix;
        }
        return candidate;
    }

    private boolean saveNpcPreset(Player player, String message) {
        // Legacy format still accepted: id;vanilla;PLAYER:Name:Skin
        String[] parts = message.split(";", 3);
        if (parts.length < 3) {
            player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_INPUT_NPC_PRESET));
            return false;
        }
        String id = parts[0].trim();
        String provider = parts[1].trim().toLowerCase();
        String payload = parts[2].trim();
        if (!plugin.getNpcPresetManager().isSafeId(id)) {
            player.sendMessage("§cInvalid id.");
            return false;
        }
        var preset = new com.gmail.bobason01.cinematicmanager.data.NpcPreset(id);
        preset.setProvider(provider);
        switch (provider) {
            case "mythicmobs", "modelengine" -> preset.setMobId(payload);
            default -> {
                String[] npc = payload.split(":", 3);
                preset.setProvider("vanilla");
                preset.setEntityType(npc.length > 0 ? npc[0] : "PLAYER");
                preset.setName(npc.length > 1 ? npc[1] : id);
                preset.setSkin(npc.length > 2 ? npc[2] : preset.getName());
            }
        }
        return plugin.getNpcPresetManager().save(preset);
    }

    private String getMetadata(Player player, String key) {
        if (player.hasMetadata(key)) {
            List<MetadataValue> values = player.getMetadata(key);
            for (MetadataValue v : values) if (v.getOwningPlugin().equals(plugin)) return v.asString();
        }
        return null;
    }

    private static class InputContext {
        final String type, id, prefix;
        final int tick;
        final Location loc;
        InputContext(String type, String id, int tick, Location loc, String prefix) {
            this.type = type; this.id = id; this.tick = tick; this.loc = loc; this.prefix = prefix;
        }
    }
}