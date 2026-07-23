package com.gmail.bobason01.cinematicmanager.fx;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.util.PacketHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Viewer-only playback of an EnvironmentClip.
 * ModelEngine skill bases are re-spawned as real ME entities (hidden from others);
 * other types use fake packets.
 */
public final class EnvironmentPlayer {
    private final CinematicManager plugin;
    private final Player viewer;
    private final EnvironmentClip clip;
    private final Location origin;
    private final Location scratch = new Location(null, 0, 0, 0);
    private final Map<Integer, Integer> localToFakeId = new HashMap<>();
    private final Map<Integer, Entity> localToModelEntity = new HashMap<>();
    private final java.util.Set<Integer> mythicFxLocals = new java.util.HashSet<>();

    private int particleIndex;
    private int soundIndex;
    private int blockIndex;
    private int entityIndex;
    private int tick;
    private boolean done;

    public EnvironmentPlayer(CinematicManager plugin, Player viewer, EnvironmentClip clip) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.clip = clip;
        this.origin = clip.getOrigin();
        if (origin.getWorld() == null && viewer.getWorld() != null) {
            origin.setWorld(viewer.getWorld());
        }
    }

    public boolean isDone() {
        return done;
    }

    public void tick() {
        if (done || !viewer.isOnline()) {
            done = true;
            return;
        }
        World world = origin.getWorld();
        if (world == null) {
            done = true;
            return;
        }

        while (particleIndex < clip.getParticleCount()
                && clip.particleTick(particleIndex) <= tick) {
            playParticle(world, particleIndex++);
        }
        while (soundIndex < clip.getSoundCount()
                && clip.soundTick(soundIndex) <= tick) {
            playSound(world, soundIndex++);
        }
        while (blockIndex < clip.getBlockCount()
                && clip.blockTick(blockIndex) <= tick) {
            playBlock(world, blockIndex++);
        }
        while (entityIndex < clip.getEntityEventCount()
                && clip.entityTick(entityIndex) <= tick) {
            playEntity(world, entityIndex++);
        }

        if (tick >= clip.getDurationTicks()
                && particleIndex >= clip.getParticleCount()
                && soundIndex >= clip.getSoundCount()
                && blockIndex >= clip.getBlockCount()
                && entityIndex >= clip.getEntityEventCount()) {
            done = true;
        }
        tick++;
    }

    public void cleanup() {
        World world = origin.getWorld();
        if (world != null && viewer.isOnline()) {
            for (int i = 0; i < clip.getBlockCount(); i++) {
                Location loc = new Location(world, clip.blockX(i), clip.blockY(i), clip.blockZ(i));
                viewer.sendBlockChange(loc, loc.getBlock().getBlockData());
            }
        }
        for (Integer entityId : localToFakeId.values()) {
            PacketHelper.destroyEntity(viewer, entityId);
        }
        localToFakeId.clear();
        for (Entity entity : localToModelEntity.values()) {
            plugin.getNpcManager().remove(entity);
        }
        localToModelEntity.clear();
        mythicFxLocals.clear();
        done = true;
    }

    private void playParticle(World world, int i) {
        try {
            Particle particle = Particle.valueOf(clip.particleName(i));
            scratch.setWorld(world);
            scratch.setX(origin.getX() + clip.particleX(i));
            scratch.setY(origin.getY() + clip.particleY(i));
            scratch.setZ(origin.getZ() + clip.particleZ(i));
            viewer.spawnParticle(particle, scratch,
                    clip.particleAmount(i),
                    clip.particleOx(i), clip.particleOy(i), clip.particleOz(i),
                    clip.particleSpeed(i));
        } catch (Exception ignored) {
        }
    }

    private void playSound(World world, int i) {
        scratch.setWorld(world);
        scratch.setX(origin.getX() + clip.soundX(i));
        scratch.setY(origin.getY() + clip.soundY(i));
        scratch.setZ(origin.getZ() + clip.soundZ(i));
        try {
            viewer.playSound(scratch, clip.soundName(i), SoundCategory.MASTER,
                    clip.soundVolume(i), clip.soundPitch(i));
        } catch (Exception ignored) {
        }
    }

    private void playBlock(World world, int i) {
        try {
            BlockData data = Bukkit.createBlockData(clip.blockData(i));
            Location loc = new Location(world, clip.blockX(i), clip.blockY(i), clip.blockZ(i));
            viewer.sendBlockChange(loc, data);
        } catch (Exception ignored) {
        }
    }

    private void playEntity(World world, int i) {
        byte event = clip.entityEvent(i);
        int localId = clip.entityLocalId(i);
        scratch.setWorld(world);
        scratch.setX(origin.getX() + clip.entityX(i));
        scratch.setY(origin.getY() + clip.entityY(i));
        scratch.setZ(origin.getZ() + clip.entityZ(i));
        scratch.setYaw(clip.entityYaw(i));
        scratch.setPitch(clip.entityPitch(i));

        if (event == EnvironmentClip.EVT_SPAWN) {
            String type = clip.entityType(i);
            if (type == null) return;
            // Legacy bare armor stands without packed gear — skip.
            if (type.equalsIgnoreCase("ARMOR_STAND")) return;

            String lower = type.toLowerCase(Locale.ROOT);
            if (lower.startsWith("mythicmobs:")) {
                String mobKey = type.substring("mythicmobs:".length()).trim();
                if (mobKey.isEmpty()) return;
                // Re-summon Mythic VFX mob so equip{delay=} frame animation plays.
                Entity spawned = plugin.getNpcManager()
                        .spawnMythicMob(viewer, mobKey, scratch.clone(), true);
                if (spawned != null) {
                    localToModelEntity.put(localId, spawned);
                    mythicFxLocals.add(localId);
                }
                return;
            }
            if (lower.startsWith("modelengine:")) {
                String modelId = type.substring("modelengine:".length()).trim();
                if (modelId.isEmpty()) return;
                Entity spawned = plugin.getNpcManager().spawnModelEngine(viewer, modelId, scratch.clone());
                if (spawned != null) localToModelEntity.put(localId, spawned);
                return;
            }

            if (ArmorStandSkillCodec.isEncoded(type)) {
                Entity spawned = spawnEquippedArmorStand(scratch.clone(), type);
                if (spawned != null) localToModelEntity.put(localId, spawned);
                return;
            }

            int entityId = PacketHelper.spawnFakeEntity(viewer, scratch, type);
            if (entityId != 0) localToFakeId.put(localId, entityId);
        } else if (event == EnvironmentClip.EVT_MOVE) {
            // Mythic FX stands drive their own equip/velocity timeline — don't teleport.
            if (mythicFxLocals.contains(localId)) return;
            Entity modeled = localToModelEntity.get(localId);
            if (modeled != null) {
                plugin.getNpcManager().move(viewer, modeled, scratch.clone());
                return;
            }
            Integer entityId = localToFakeId.get(localId);
            if (entityId != null) PacketHelper.teleportFakeEntity(viewer, entityId, scratch);
        } else if (event == EnvironmentClip.EVT_REMOVE) {
            mythicFxLocals.remove(localId);
            Entity modeled = localToModelEntity.remove(localId);
            if (modeled != null) {
                plugin.getNpcManager().remove(modeled);
                return;
            }
            Integer entityId = localToFakeId.remove(localId);
            if (entityId != null) PacketHelper.destroyEntity(viewer, entityId);
        }
    }

    private Entity spawnEquippedArmorStand(Location loc, String encoded) {
        if (loc.getWorld() == null) return null;
        try {
            ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as ->
                    ArmorStandSkillCodec.apply(as, encoded));
            ArmorStandSkillCodec.apply(stand, encoded);
            viewer.showEntity(plugin, stand);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(viewer.getUniqueId())) {
                    online.hideEntity(plugin, stand);
                }
            }
            return stand;
        } catch (Exception ignored) {
            return null;
        }
    }
}
