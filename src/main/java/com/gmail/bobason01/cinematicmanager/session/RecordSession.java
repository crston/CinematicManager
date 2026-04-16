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
        this.recordOrigin = player.getLocation().clone(); // 기준점 저장
        this.isRecording = false;
    }

    public void start() {
        if (isRecording) return;
        isRecording = true;

        player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_RECORD_START));

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

                // 현재 위치에서 기준점을 뺀 상대 좌표를 기록용 리스트에 추가
                Location current = player.getLocation();
                Location relative = current.clone();
                relative.setX(current.getX() - recordOrigin.getX());
                relative.setY(current.getY() - recordOrigin.getY());
                relative.setZ(current.getZ() - recordOrigin.getZ());
                // 회전값(Yaw, Pitch)은 그대로 유지

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
            // 카메라는 클릭한 지점(recordOrigin)에서 상대 경로가 시작되도록 설정
            data.addAction(startTick, new CinematicAction(CinematicAction.ActionType.CAMERA, recordId, recordOrigin, null));
        } else if (recordType == CinematicAction.ActionType.MOVE_NPC) {
            // NPC 이동은 NPC가 스폰된 위치를 기준으로 상대 경로가 시작됨
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