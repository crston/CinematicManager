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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        if (data != null && recordType == CinematicAction.ActionType.MOVE_NPC && targetSlot != null) {
            // Spawn → prior MOVE/SHOW chain so the next path continues from the NPC's last pose.
            origin = resolveNpcPoseAt(data, targetSlot, startTick);
        } else if (data != null && targetSlot != null) {
            origin = findLatestSpawn(data, targetSlot, startTick);
        }

        this.recordOrigin = (origin != null) ? origin.clone() : player.getLocation().clone();
        this.isRecording = false;
    }

    public void start() {
        if (isRecording) return;
        isRecording = true;

        player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_RECORD_START));
        if (recordOrigin.getWorld() != null) {
            player.teleport(recordOrigin);
        }

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

    /**
     * World pose of {@code targetSlot} just before {@code startTick}: spawn, then each prior
     * MOVE/SHOW for that NPC applied in tick order.
     */
    private Location resolveNpcPoseAt(CinematicData data, String targetSlot, int startTick) {
        String targetKey = sanitize(targetSlot);
        Location pose = null;

        List<Map.Entry<Integer, List<CinematicAction>>> ticks = new ArrayList<>(data.getTimeline().entrySet());
        ticks.sort(Comparator.comparingInt(Map.Entry::getKey));

        for (Map.Entry<Integer, List<CinematicAction>> entry : ticks) {
            int tick = entry.getKey();
            if (tick > startTick) break;
            for (CinematicAction action : entry.getValue()) {
                if (action.getType() == CinematicAction.ActionType.SPAWN_NPC
                        && sanitize(action.getValue()).equals(targetKey)) {
                    Location loc = action.getLocation();
                    if (loc != null) pose = loc.clone();
                    continue;
                }
                if (action.getType() == CinematicAction.ActionType.SHOW_ENTITY) {
                    String showKey = sanitize(action.getExtra() != null ? action.getExtra() : action.getValue());
                    if (showKey.equals(targetKey) && action.getLocation() != null) {
                        pose = action.getLocation().clone();
                    }
                    continue;
                }
                if (action.getType() != CinematicAction.ActionType.MOVE_NPC) continue;
                if (!sanitize(action.getExtra()).equals(targetKey)) continue;

                List<Location> path = data.getPathRecord(action.getValue());
                if (path == null || path.isEmpty()) continue;

                Location origin = action.getLocation();
                if (origin == null) origin = pose;
                if (origin == null || origin.getWorld() == null) continue;

                Location last = path.get(path.size() - 1);
                pose = new Location(
                        origin.getWorld(),
                        origin.getX() + last.getX(),
                        origin.getY() + last.getY(),
                        origin.getZ() + last.getZ(),
                        last.getYaw(),
                        last.getPitch());
            }
        }
        return pose;
    }

    private Location findLatestSpawn(CinematicData data, String targetSlot, int startTick) {
        String targetKey = sanitize(targetSlot);
        Location origin = null;
        int originTick = Integer.MIN_VALUE;
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
        return origin;
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
