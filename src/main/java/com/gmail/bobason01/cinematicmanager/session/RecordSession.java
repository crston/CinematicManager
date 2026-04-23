package com.gmail.bobason01.cinematicmanager.session;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
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
    private final Location recordOrigin; // 기준점
    private boolean isRecording;

    public RecordSession(CinematicManager plugin, Player player, String cinematicId, int startTick, CinematicAction.ActionType recordType, String targetSlot) {
        this.plugin = plugin;
        this.player = player;
        this.cinematicId = cinematicId;
        this.startTick = startTick;
        this.recordType = recordType;
        this.targetSlot = targetSlot;
        this.recordedPath = new ArrayList<>();

        // [핵심] 기준점을 플레이어 위치가 아닌, 소환된 액션의 위치에서 가져옴
        CinematicData data = plugin.getDataManager().getCinematic(cinematicId);
        Location origin = null;
        if (data != null && targetSlot != null) {
            for (List<CinematicAction> actions : data.getTimeline().values()) {
                for (CinematicAction action : actions) {
                    if (action.getType() == CinematicAction.ActionType.SPAWN_NPC && action.getValue().equals(targetSlot)) {
                        origin = action.getLocation();
                        break;
                    }
                }
            }
        }

        // 소환 위치를 못 찾으면 현재 플레이어 위치를 기준점으로 (fallback)
        this.recordOrigin = (origin != null) ? origin.clone() : player.getLocation().clone();
        this.isRecording = false;
    }

    public void start() {
        if (isRecording) return;
        isRecording = true;

        player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_RECORD_START));

        // 녹화 시작 시 플레이어를 기준점으로 텔레포트 시켜서 오차 방지
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
                Location relative = current.clone();
                // 기준점(소환 위치)으로부터의 상대적 거리 기록
                relative.setX(current.getX() - recordOrigin.getX());
                relative.setY(current.getY() - recordOrigin.getY());
                relative.setZ(current.getZ() - recordOrigin.getZ());

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
}