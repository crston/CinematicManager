package com.gmail.bobason01.cinematicmanager.hook;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.util.PacketHelper;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Soft-depend AnimationManager playback for cinematic NPCs.
 * <p>
 * No server ArmorStands: baked clip arrays are sampled on the cinematic tick and
 * sent as viewer-only packet limbs. Other players never receive spawn/pose packets.
 */
public final class AnimationManagerHook {

    private static final double POS_EPS_SQ = 1.0E-8D;
    private static final float YAW_EPS = 0.01f;
    private static final float DEG2RAD = (float) (Math.PI / 180.0D);

    private final CinematicManager plugin;
    private final boolean enabled;

    private Method packedClip;
    private Method resolveLimbItems;
    private Method apiPlay;
    private Method apiStop;
    private Method apiIsPlaying;
    private Field clipTicks;
    private Field clipBones;
    private Field clipLoop;
    private Field clipPos;
    private Field clipRot;
    private Field clipHidden;

    private final ConcurrentHashMap<UUID, Play> byNpc = new ConcurrentHashMap<>();
    private Play[] plays = new Play[4];
    private int playCount;

    public AnimationManagerHook(CinematicManager plugin) {
        this.plugin = plugin;
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("AnimationManager") && resolveApi();
        if (enabled) {
            plugin.getLogger().info("AnimationManager hook enabled (viewer-only packet limbs).");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean resolveApi() {
        try {
            Class<?> api = Class.forName("com.gmail.bobason01.api.AnimationAPI");
            apiPlay = api.getMethod("play", Entity.class, String.class);
            apiStop = api.getMethod("stop", Entity.class);
            apiIsPlaying = api.getMethod("isPlaying", Entity.class);
            try {
                Class<?> clip = Class.forName("com.gmail.bobason01.api.PackedClip");
                packedClip = api.getMethod("packedClip", String.class);
                resolveLimbItems = api.getMethod("resolveLimbItems", Entity.class, clip, Consumer.class);
                clipTicks = clip.getField("ticks");
                clipBones = clip.getField("boneCount");
                clipLoop = clip.getField("loop");
                clipPos = clip.getField("pos");
                clipRot = clip.getField("rot");
                clipHidden = clip.getField("hidden");
            } catch (Throwable t) {
                plugin.getLogger().warning("AnimationManager packet clip API missing — using entity play fallback.");
                packedClip = null;
            }
            return apiPlay != null;
        } catch (Throwable t) {
            plugin.getLogger().warning("AnimationManager API resolve failed: " + t.getMessage());
            return false;
        }
    }

    public boolean play(Player viewer, Entity entity, String clipRef) {
        if (!enabled || viewer == null || entity == null || clipRef == null || clipRef.isBlank()) {
            return false;
        }
        String id = clipRef.trim();
        if (id.equalsIgnoreCase("STOP") || id.regionMatches(true, 0, "STOP:", 0, 5)) {
            stop(entity, viewer);
            return true;
        }
        if (byNpc.containsKey(entity.getUniqueId())) {
            stop(entity, viewer);
        }
        Object clip = null;
        if (packedClip != null) {
            try {
                clip = packedClip.invoke(null, id);
            } catch (Throwable t) {
                plugin.getLogger().warning("AnimationManager clip resolve failed (" + id + "): " + t.getMessage());
            }
        }
        preparePlayback(viewer, entity);
        if (clip == null) {
            return playViaApi(viewer, entity, id);
        }

        int bones;
        int ticks;
        byte loop;
        float[] pos;
        float[] rot;
        boolean[] hidden;
        try {
            bones = clipBones.getInt(clip);
            ticks = clipTicks.getInt(clip);
            loop = clipLoop.getByte(clip);
            pos = (float[]) clipPos.get(clip);
            rot = (float[]) clipRot.get(clip);
            hidden = (boolean[]) clipHidden.get(clip);
        } catch (Throwable t) {
            plugin.getLogger().warning("AnimationManager clip buffers unreadable: " + t.getMessage());
            return false;
        }
        if (bones <= 0 || ticks <= 0 || pos == null || rot == null || hidden == null) {
            plugin.getLogger().warning("AnimationManager: empty clip '" + id + "'");
            return false;
        }

        UUID npcId = entity.getUniqueId();
        Play play = new Play(viewer, entity, npcId, bones, ticks, loop, pos, rot, hidden);
        addPlay(play);

        try {
            resolveLimbItems.invoke(null, entity, clip, (Consumer<ItemStack[]>) items -> {
                Play current = byNpc.get(npcId);
                if (current != play || current.dead) return;
                if (!current.spawned) {
                    current.spawn(items);
                    if (!current.hasLimbs()) {
                        byNpc.remove(npcId, current);
                        removePlay(current);
                        playViaApi(viewer, entity, id);
                    }
                } else {
                    current.refreshItems(items);
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("AnimationManager skin resolve failed (" + id + "): " + t.getMessage());
            stop(entity, viewer);
            return false;
        }
        return true;
    }

    private void preparePlayback(Player viewer, Entity entity) {
        var lux = plugin.getLuxGesturesHook();
        if (lux != null && lux.isEnabled()) {
            lux.ensureStopped(entity, viewer);
        }
        var hmc = plugin.getHmcCosmeticsHook();
        if (hmc != null && hmc.isEnabled()) {
            hmc.suspendForGesture(entity, viewer);
        }
        if (viewer != null && viewer.isOnline() && entity.isValid()) {
            viewer.hideEntity(plugin, entity);
        }
        if (hmc != null && hmc.isEnabled()) {
            hmc.suspendForGesture(entity, viewer);
        }
    }

    private boolean playViaApi(Player viewer, Entity entity, String id) {
        if (apiPlay == null) {
            plugin.getLogger().warning("AnimationManager: cannot play '" + id + "'");
            return false;
        }
        try {
            boolean ok = Boolean.TRUE.equals(apiPlay.invoke(null, entity, id));
            if (!ok) {
                plugin.getLogger().warning("AnimationManager: unknown clip '" + id + "'");
                return false;
            }
            restrictLimbsToViewer(viewer, entity);
            Bukkit.getScheduler().runTask(plugin, () -> restrictLimbsToViewer(viewer, entity));
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("AnimationManager play failed (" + id + "): " + t.getMessage());
            return false;
        }
    }

    private void restrictLimbsToViewer(Player viewer, Entity entity) {
        if (viewer == null || entity == null || !entity.isValid()) return;
        org.bukkit.World world = entity.getWorld();
        if (world == null) return;
        if (viewer.isOnline()) {
            viewer.hideEntity(plugin, entity);
        }
        for (Entity nearby : world.getNearbyEntities(entity.getLocation(), 6.0, 6.0, 6.0)) {
            boolean limb = nearby.getScoreboardTags().contains("am_limb");
            boolean name = nearby.getScoreboardTags().contains("am_name");
            if (!limb && !name) continue;
            // Name plates on cinematic ArmorStand bases become "Armor Stand".
            if (name) {
                hideFromAll(nearby);
                continue;
            }
            viewer.showEntity(plugin, nearby);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                    other.hideEntity(plugin, nearby);
                }
            }
        }
    }

    private void hideFromAll(Entity entity) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.hideEntity(plugin, entity);
        }
    }

    public boolean isPlaying(Entity entity) {
        if (entity == null) return false;
        if (byNpc.containsKey(entity.getUniqueId())) return true;
        if (apiIsPlaying == null) return false;
        try {
            return Boolean.TRUE.equals(apiIsPlaying.invoke(null, entity));
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isPlayingFor(Player viewer) {
        if (viewer == null || playCount == 0) return false;
        UUID id = viewer.getUniqueId();
        for (int i = 0; i < playCount; i++) {
            Play play = plays[i];
            if (play.viewer.getUniqueId().equals(id) && !play.dead && play.keepsSession()) return true;
        }
        return false;
    }

    public void ensureStopped(Entity entity, Player viewer) {
        if (entity != null && isPlaying(entity)) {
            stop(entity, viewer);
        }
    }

    public void stop(Entity entity, Player viewer) {
        if (entity == null) return;
        Play play = byNpc.remove(entity.getUniqueId());
        if (play != null) {
            removePlay(play);
            play.destroy();
        }
        if (apiStop != null) {
            try {
                apiStop.invoke(null, entity);
            } catch (Throwable ignored) {
            }
        }
        restoreNpc(entity, play != null && play.viewer != null ? play.viewer : viewer);
    }

    public void tick(Player viewer) {
        if (!enabled || playCount == 0 || viewer == null) return;
        UUID id = viewer.getUniqueId();
        for (int i = 0; i < playCount; ) {
            Play play = plays[i];
            if (play.dead || !play.viewer.getUniqueId().equals(id)) {
                i++;
                continue;
            }
            if (!play.tick()) {
                byNpc.remove(play.npcId, play);
                removeAt(i);
                if (apiStop != null) {
                    try {
                        apiStop.invoke(null, play.npc);
                    } catch (Throwable ignored) {
                    }
                }
                restoreNpc(play.npc, play.viewer);
                continue;
            }
            i++;
        }
    }

    public void stopAll(Player viewer) {
        if (viewer == null) return;
        UUID id = viewer.getUniqueId();
        for (int i = playCount - 1; i >= 0; i--) {
            Play play = plays[i];
            if (play.viewer.getUniqueId().equals(id)) {
                byNpc.remove(play.npcId, play);
                removeAt(i);
                play.destroy();
                restoreNpc(play.npc, play.viewer);
            }
        }
    }

    public void shutdown() {
        for (int i = playCount - 1; i >= 0; i--) {
            Play play = plays[i];
            byNpc.remove(play.npcId, play);
            play.destroy();
            restoreNpc(play.npc, play.viewer);
        }
        playCount = 0;
        byNpc.clear();
    }

    private void restoreNpc(Entity entity, Player viewer) {
        plugin.getNpcManager().revealToViewer(viewer, entity);
        var hmc = plugin.getHmcCosmeticsHook();
        if (hmc != null && hmc.isEnabled() && entity != null) {
            hmc.resumeAfterGesture(entity, viewer);
        }
    }

    private void addPlay(Play play) {
        if (playCount == plays.length) {
            Play[] next = new Play[plays.length << 1];
            System.arraycopy(plays, 0, next, 0, playCount);
            plays = next;
        }
        plays[playCount++] = play;
        byNpc.put(play.npcId, play);
    }

    private void removePlay(Play play) {
        for (int i = 0; i < playCount; i++) {
            if (plays[i] == play) {
                removeAt(i);
                return;
            }
        }
    }

    private void removeAt(int i) {
        plays[i] = plays[--playCount];
        plays[playCount] = null;
    }

    private final class Play {
        final Player viewer;
        final Entity npc;
        final UUID npcId;
        final int boneCount;
        final int ticks;
        final byte loop;
        final float[] pos;
        final float[] rot;
        final boolean[] hidden;
        final int[] ids;
        final boolean[] holding;
        final Location loc = new Location(null, 0, 0, 0);
        ItemStack[] items;
        boolean spawned;
        boolean dead;
        int frame;
        double carry;
        double lastX = Double.NaN;
        double lastY;
        double lastZ;
        float lastYaw;
        int lastFrame = Integer.MIN_VALUE;

        Play(Player viewer, Entity npc, UUID npcId, int boneCount, int ticks, byte loop,
             float[] pos, float[] rot, boolean[] hidden) {
            this.viewer = viewer;
            this.npc = npc;
            this.npcId = npcId;
            this.boneCount = boneCount;
            this.ticks = ticks;
            this.loop = loop;
            this.pos = pos;
            this.rot = rot;
            this.hidden = hidden;
            this.ids = new int[boneCount];
            this.holding = new boolean[boneCount];
        }

        void spawn(ItemStack[] resolved) {
            if (dead || spawned || viewer == null || !viewer.isOnline() || !npc.isValid()) return;
            this.items = resolved;
            Location base = npc.getLocation(loc);
            float yaw = base.getYaw();
            double sin = Math.sin(yaw * DEG2RAD);
            double cos = Math.cos(yaw * DEG2RAD);
            int poseBase = 0;
            for (int i = 0; i < boneCount; i++) {
                int o = poseBase + i * 3;
                poseInto(o, base.getX(), base.getY(), base.getZ(), sin, cos, yaw);
                ItemStack hand = item(i);
                boolean hide = hidden[i];
                ids[i] = PacketHelper.spawnLimbStand(
                        viewer, loc, hide ? null : hand, rot[o], rot[o + 1], rot[o + 2]);
                holding[i] = !hide && hand != null && !hand.getType().isAir();
            }
            spawned = true;
            lastX = base.getX();
            lastY = base.getY();
            lastZ = base.getZ();
            lastYaw = yaw;
            lastFrame = 0;
            if (hasLimbs()) {
                viewer.hideEntity(plugin, npc);
            }
        }

        boolean hasLimbs() {
            for (int id : ids) if (id != 0) return true;
            return false;
        }

        boolean keepsSession() {
            return loop == 0 && frame < ticks;
        }

        void refreshItems(ItemStack[] resolved) {
            if (dead || !spawned) {
                this.items = resolved;
                return;
            }
            this.items = resolved;
            for (int i = 0; i < boneCount; i++) {
                if (ids[i] == 0) continue;
                boolean hide = hidden[wrapFrame() * boneCount + i];
                ItemStack hand = item(i);
                if (hide) {
                    if (holding[i]) {
                        PacketHelper.clearEquipment(viewer, ids[i], EquipmentSlot.MAIN_HAND);
                        holding[i] = false;
                    }
                } else {
                    PacketHelper.setEquipment(viewer, ids[i], EquipmentSlot.MAIN_HAND, hand);
                    holding[i] = hand != null && !hand.getType().isAir();
                }
            }
        }

        boolean tick() {
            if (dead) return false;
            if (viewer == null || !viewer.isOnline() || npc == null || !npc.isValid()) {
                destroy();
                return false;
            }
            carry += 1.0D;
            int step = (int) carry;
            if (step != 0) {
                carry -= step;
                frame += step;
            }
            if (loop == 0 && frame >= ticks) {
                destroy();
                return false;
            }
            int sampled = wrapFrame();
            Location base = npc.getLocation(loc);
            double bx = base.getX();
            double by = base.getY();
            double bz = base.getZ();
            float yaw = base.getYaw();
            boolean moved = moved(bx, by, bz, yaw);
            boolean poseChanged = sampled != lastFrame;
            if (!spawned || (!moved && !poseChanged)) {
                return true;
            }
            double sin = Math.sin(yaw * DEG2RAD);
            double cos = Math.cos(yaw * DEG2RAD);
            int poseBase = sampled * boneCount * 3;
            int hideBase = sampled * boneCount;
            for (int i = 0; i < boneCount; i++) {
                int id = ids[i];
                if (id == 0) continue;
                int o = poseBase + i * 3;
                poseInto(o, bx, by, bz, sin, cos, yaw);
                if (moved) {
                    PacketHelper.teleportFakeEntitySnapped(viewer, id, loc);
                }
                if (poseChanged) {
                    PacketHelper.setLimbArmPose(viewer, id, rot[o], rot[o + 1], rot[o + 2]);
                    boolean hide = hidden[hideBase + i];
                    if (hide) {
                        if (holding[i]) {
                            PacketHelper.clearEquipment(viewer, id, EquipmentSlot.MAIN_HAND);
                            holding[i] = false;
                        }
                    } else if (!holding[i]) {
                        ItemStack hand = item(i);
                        if (hand != null && !hand.getType().isAir()) {
                            PacketHelper.setEquipment(viewer, id, EquipmentSlot.MAIN_HAND, hand);
                            holding[i] = true;
                        }
                    }
                }
            }
            lastX = bx;
            lastY = by;
            lastZ = bz;
            lastYaw = yaw;
            lastFrame = sampled;
            return true;
        }

        void destroy() {
            if (dead) return;
            dead = true;
            if (spawned && viewer != null && viewer.isOnline()) {
                PacketHelper.destroyEntities(viewer, ids);
            }
            spawned = false;
        }

        private int wrapFrame() {
            if (frame < ticks) return Math.max(frame, 0);
            return loop == 2 ? frame % ticks : ticks - 1;
        }

        private boolean moved(double x, double y, double z, float yaw) {
            if (Double.isNaN(lastX)) return true;
            double dx = x - lastX;
            double dy = y - lastY;
            double dz = z - lastZ;
            if (dx * dx + dy * dy + dz * dz > POS_EPS_SQ) return true;
            float dyaw = yaw - lastYaw;
            if (dyaw < 0f) dyaw = -dyaw;
            return dyaw > YAW_EPS;
        }

        private void poseInto(int o, double bx, double by, double bz, double sin, double cos, float yaw) {
            float x = pos[o];
            float z = pos[o + 2];
            loc.setX(bx + x * cos - z * sin);
            loc.setY(by + pos[o + 1]);
            loc.setZ(bz + x * sin + z * cos);
            loc.setYaw(yaw);
            loc.setPitch(0f);
        }

        private ItemStack item(int bone) {
            return items == null || bone >= items.length ? null : items[bone];
        }
    }
}
