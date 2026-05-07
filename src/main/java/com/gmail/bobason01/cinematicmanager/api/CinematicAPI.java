package com.gmail.bobason01.cinematicmanager.api;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import org.bukkit.entity.Player;
import java.util.Set;

public class CinematicAPI {

    private static CinematicManager getPlugin() {
        return CinematicManager.getInstance();
    }

    // 현재 플레이어가 시네마틱을 시청 중인지 확인합니다
    public static boolean isPlaying(Player player) {
        return getPlugin().getSessionManager().isPlaying(player);
    }

    // 특정 플레이어에게 시네마틱을 재생합니다
    public static void playCinematic(Player player, String id) {
        getPlugin().getSessionManager().startSession(player, id);
    }

    // 특정 플레이어의 시네마틱을 강제로 중단시킵니다
    public static void stopCinematic(Player player) {
        getPlugin().getSessionManager().stopSession(player);
    }

    // 해당 이름의 시네마틱 데이터가 존재하는지 확인합니다
    public static boolean cinematicExists(String id) {
        return getPlugin().getDataManager().getCinematic(id) != null;
    }

    // 서버에 존재하는 모든 시네마틱의 ID 목록을 반환합니다
    public static Set<String> getAvailableCinematics() {
        return getPlugin().getDataManager().getIds();
    }
}