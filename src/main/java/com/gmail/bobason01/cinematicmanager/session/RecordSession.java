package com.gmail.bobason01.cinematicmanager.session;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecordSession {

    private final CinematicManager plugin;
    private final Player player;
    private final String cinematicId;
    private final int startTick;
    private final CinematicAction.ActionType recordType;
    private final String targetSlot;
    private final List<Location> recordedPath;
    private final Location recordOrigin;
    private boolean isRecording;

    public RecordSession(CinematicManager plugin, Player player, String cinematicId, int startTick, CinematicAction.ActionType recordType, String targetSlot) {
        this.plugin = plugin;
        this.player = player;
        this.cinematicId = cinematicId;
        this.startTick = startTick;
        this.recordType = recordType;
        this.targetSlot = targetSlot;
        this.recordedPath = new ArrayList<>();

        CinematicData data = plugin.getDataManager().getCinematic(cinematicId);
        Location origin = null;
        if (data != null && targetSlot != null) {
            String targetKey = sanitize(targetSlot);
            for (List<CinematicAction> actions : data.getTimeline().values()) {
                for (CinematicAction action : actions) {
                    if (action.getType() == CinematicAction.ActionType.SPAWN_NPC
                            && sanitize(action.getValue()).equals(targetKey)) {
                        origin = action.getLocation();
                        break;
                    }
                }
                if (origin != null) break;
            }
        }

        this.recordOrigin = (origin != null) ? origin.clone() : player.getLocation().clone();
        this.isRecording = false;
    }

    public void start() {
        if (isRecording) return;
        isRecording = true;

        player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_RECORD_START));
        player.teleport(recordOrigin);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isRecording || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                if (player.isSneaking()) {
                    stop();
                    this.cancel();
                    return;
                }

                Location current = player.getLocation();
                // 월드 없는 상대 좌표로 저장 (재생 시 origin + offset)
                Location relative = new Location(
                        null,
                        current.getX() - recordOrigin.getX(),
                        current.getY() - recordOrigin.getY(),
                        current.getZ() - recordOrigin.getZ(),
                        current.getYaw(),
                        current.getPitch()
                );
                recordedPath.add(relative);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void stop() {
        if (!isRecording) return;
        isRecording = false;

        String recordId = UUID.randomUUID().toString().substring(0, 8);
        CinematicData data = plugin.getDataManager().getCinematic(cinematicId);
        if (data == null) return;

        data.addPathRecord(recordId, recordedPath);

        if (recordType == CinematicAction.ActionType.CAMERA) {
            data.addAction(startTick, new CinematicAction(CinematicAction.ActionType.CAMERA, recordId, recordOrigin, null));
        } else if (recordType == CinematicAction.ActionType.MOVE_NPC) {
            data.addAction(startTick, new CinematicAction(CinematicAction.ActionType.MOVE_NPC, recordId, recordOrigin, targetSlot));
        }

        plugin.getDataManager().saveCinematic(data);
        player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_RECORD_END));

        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getGuiManager().openStudioGUI(player, cinematicId, startTick / 180);
            }
        }.runTask(plugin);
    }

    private String sanitize(String id) {
        if (id == null) return "";
        return ChatColor.stripColor(id).toLowerCase().trim();
    }
}
