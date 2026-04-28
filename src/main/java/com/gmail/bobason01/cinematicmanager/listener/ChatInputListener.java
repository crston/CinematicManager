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
import org.bukkit.metadata.FixedMetadataValue;
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
                try {
                    EntityType.valueOf(message.toUpperCase());
                    player.sendMessage(lang.format(LangKey.MSG_INPUT_CUSTOM_TYPE_CONFIRM, "{type}", message.toUpperCase()));
                    player.sendMessage(lang.getPrefixed(LangKey.MSG_INPUT_SPAWN_NPC_MOB));
                    startTrackInput(player, context.id, "SPAWN", context.tick, context.prefix + message.toUpperCase() + ":");
                } catch (Exception e) {
                    player.sendMessage(lang.getPrefixed(LangKey.MSG_ERROR_INVALID_TYPE));
                    startTrackInput(player, context.id, "CUSTOM_TYPE", context.tick, context.prefix);
                }
                return;
            }

            // [핵심] 현재 편집 중인 시네마틱 ID와 틱 정보를 메타데이터에서 재검증
            String editId = getMetadata(player, "edit_id");
            String targetId = (editId != null) ? editId : context.id;

            // 틱 정보는 메타데이터에 있다면 최우선적으로 사용 (다른 트랙 생성 방지)
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
                case "COMMAND" -> CinematicAction.ActionType.COMMAND;
                case "STATE", "STOP" -> CinematicAction.ActionType.ANIMATION;
                default -> null;
            };

            if (actionType != null) {
                String val = context.prefix + message;
                String extra = null;
                if (context.type.equals("STATE") || context.type.equals("STOP")) {
                    extra = getMetadata(player, "edit_npc_target");
                    if (context.type.equals("STOP")) val = "STOP:" + message;
                }

                // 지정된 정확한 틱 위치에 액션 추가
                data.addAction(targetTick, new CinematicAction(actionType, val, context.loc, extra));
                plugin.getDataManager().saveCinematic(data);
                player.sendMessage(lang.getPrefixed(LangKey.MSG_SAVE_SUCCESS));
            }

            // 입력 완료 후 스튜디오로 복귀 (현재 페이지 계산)
            int targetPage = targetTick / 180;
            plugin.getGuiManager().openStudioGUI(player, targetId, targetPage);
        });
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