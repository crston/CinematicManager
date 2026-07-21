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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class RecordSession {

    private final CinematicManager plugin;
    private final Player player;
    private final String cinematicId;
    private final int startTick;
    private final CinematicAction.ActionType recordType;
    private final String targetSlot;
    private final PathBuffer recordedPath;
    private final Location recordOrigin;
    private final Location recordScratch = new Location(null, 0, 0, 0);
    private final int maxRecordingTicks;
    private boolean isRecording;

    public RecordSession(CinematicManager plugin, Player player, String cinematicId, int startTick, CinematicAction.ActionType recordType, String targetSlot) {
        this.plugin = plugin;
        this.player = player;
        this.cinematicId = cinematicId;
        this.startTick = startTick;
        this.recordType = recordType;
        this.targetSlot = targetSlot;
        this.recordedPath = new PathBuffer(600);
        this.maxRecordingTicks = Math.max(20,
                plugin.getConfig().getInt("performance.max-recording-ticks", 12000));

        CinematicData data = plugin.getDataManager().getCinematic(cinematicId);
        Location origin = null;
        int originTick = Integer.MIN_VALUE;
        if (data != null && targetSlot != null) {
            String targetKey = sanitize(targetSlot);
            for (var entry : data.getTimeline().entrySet()) {
                int tick = entry.getKey();
                if (tick > startTick || tick < originTick) continue;
                for (CinematicAction action : entry.getValue()) {
                    if (action.getType() == CinematicAction.ActionType.SPAWN_NPC
                            && sanitize(action.getValue()).equals(targetKey)) {
                        origin = action.getLocation();
                        originTick = tick;
                    }
                }
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
                if (!isRecording) {
                    this.cancel();
                    return;
                }
                if (!player.isOnline()) {
                    finish(false);
                    this.cancel();
                    return;
                }

                if (player.isSneaking()) {
                    stop();
                    this.cancel();
                    return;
                }

                if (recordedPath.size() >= maxRecordingTicks) {
                    stop();
                    this.cancel();
                    return;
                }

                player.getLocation(recordScratch);
                recordedPath.add(
                        recordScratch.getX() - recordOrigin.getX(),
                        recordScratch.getY() - recordOrigin.getY(),
                        recordScratch.getZ() - recordOrigin.getZ(),
                        recordScratch.getYaw(),
                        recordScratch.getPitch());
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void stop() {
        finish(true);
    }

    private void finish(boolean reopenGui) {
        if (!isRecording) return;
        isRecording = false;

        CinematicData data = plugin.getDataManager().getCinematic(cinematicId);
        if (data == null || recordedPath.size() == 0) return;

        String recordId = UUID.randomUUID().toString().substring(0, 8);
        data.addPathRecord(recordId, recordedPath.toLocations());

        if (recordType == CinematicAction.ActionType.CAMERA) {
            data.addAction(startTick, new CinematicAction(CinematicAction.ActionType.CAMERA, recordId, recordOrigin, null));
        } else if (recordType == CinematicAction.ActionType.MOVE_NPC) {
            data.addAction(startTick, new CinematicAction(CinematicAction.ActionType.MOVE_NPC, recordId, recordOrigin, targetSlot));
        }

        plugin.getDataManager().saveCinematic(data);
        if (player.isOnline()) {
            player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_RECORD_END));
        }

        if (!reopenGui || !player.isOnline()) return;
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

    /**
     * Allocation-free recording hot path. Bukkit Location objects are created
     * once, only when the completed path is committed.
     */
    private static final class PathBuffer {
        private double[] x;
        private double[] y;
        private double[] z;
        private float[] yaw;
        private float[] pitch;
        private int size;

        PathBuffer(int initialCapacity) {
            x = new double[initialCapacity];
            y = new double[initialCapacity];
            z = new double[initialCapacity];
            yaw = new float[initialCapacity];
            pitch = new float[initialCapacity];
        }

        int size() {
            return size;
        }

        void add(double x, double y, double z, float yaw, float pitch) {
            ensureCapacity(size + 1);
            this.x[size] = x;
            this.y[size] = y;
            this.z[size] = z;
            this.yaw[size] = yaw;
            this.pitch[size] = pitch;
            size++;
        }

        List<Location> toLocations() {
            List<Location> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                result.add(new Location(null, x[i], y[i], z[i], yaw[i], pitch[i]));
            }
            return result;
        }

        private void ensureCapacity(int required) {
            if (required <= x.length) return;
            int capacity = Math.max(required, x.length + (x.length >> 1));
            x = Arrays.copyOf(x, capacity);
            y = Arrays.copyOf(y, capacity);
            z = Arrays.copyOf(z, capacity);
            yaw = Arrays.copyOf(yaw, capacity);
            pitch = Arrays.copyOf(pitch, capacity);
        }
    }
}
