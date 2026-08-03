package com.gmail.bobason01.cinematicmanager.util;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Viewer-scoped PacketEvents helpers. Never broadcasts — always personal packets.
 */
public final class PacketHelper {
    private static final AtomicInteger FAKE_ENTITY_IDS = new AtomicInteger(1_900_000_000);

    /** HMC {@code getMask}: invisible + fire (backpack-prevent-darkness default). */
    private static final byte HMC_BACKPACK_FLAGS = 0x21;
    /** HMC armor-stand client flags: marker only. */
    private static final byte HMC_STAND_CLIENT_FLAGS = 0x10;

    private PacketHelper() {}

    public static void send(Player viewer, PacketWrapper<?> packet) {
        if (viewer == null || packet == null) return;
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    public static void sendLightning(Player viewer, Location loc) {
        int entityId = ThreadLocalRandom.current().nextInt(100000, 200000);
        WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.LIGHTNING_BOLT,
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                0f,
                0f,
                0f,
                0,
                Optional.empty()
        );
        send(viewer, packet);
    }

    public static void sendEntityAnimation(Player viewer, Entity entity, int animationId) {
        WrapperPlayServerEntityAnimation.EntityAnimationType type =
                WrapperPlayServerEntityAnimation.EntityAnimationType.getById(animationId);
        if (type == null) {
            type = WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM;
        }
        send(viewer, new WrapperPlayServerEntityAnimation(entity.getEntityId(), type));
    }

    public static int spawnFakeEntity(Player viewer, Location loc, String bukkitTypeName) {
        if (viewer == null || loc == null) return 0;
        EntityType type = resolveEntityType(bukkitTypeName);
        if (type == null) type = EntityTypes.ARMOR_STAND;
        int entityId = FAKE_ENTITY_IDS.getAndIncrement();
        WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(UUID.randomUUID()),
                type,
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                loc.getPitch(),
                loc.getYaw(),
                loc.getYaw(),
                0,
                Optional.empty()
        );
        send(viewer, packet);
        return entityId;
    }

    /**
     * HMCCosmeticsRemapped backpack host: packet ArmorStand + helmet, then ride the NPC.
     */
    public static int spawnHmcBackpackStand(Player viewer, Location entityFeet, ItemStack helmet) {
        if (viewer == null || entityFeet == null || helmet == null || helmet.getType().isAir()) return 0;
        int entityId = FAKE_ENTITY_IDS.getAndIncrement();
        try {
            send(viewer, new WrapperPlayServerSpawnEntity(
                    entityId,
                    Optional.of(UUID.randomUUID()),
                    EntityTypes.ARMOR_STAND,
                    new Vector3d(entityFeet.getX(), entityFeet.getY(), entityFeet.getZ()),
                    entityFeet.getPitch(),
                    entityFeet.getYaw(),
                    entityFeet.getYaw(),
                    0,
                    Optional.empty()
            ));
            sendHmcBackpackMeta(viewer, entityId);
            setEquipment(viewer, entityId, EquipmentSlot.HELMET, helmet);
            sendHmcBackpackMeta(viewer, entityId);
            return entityId;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void sendHmcBackpackMeta(Player viewer, int entityId) {
        if (viewer == null || entityId == 0) return;
        try {
            List<EntityData<?>> meta = new ArrayList<>(4);
            meta.add(new EntityData<>(0, EntityDataTypes.BYTE, HMC_BACKPACK_FLAGS));
            meta.add(new EntityData<>(4, EntityDataTypes.BOOLEAN, true));
            meta.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true));
            meta.add(new EntityData<>(15, EntityDataTypes.BYTE, HMC_STAND_CLIENT_FLAGS));
            send(viewer, new WrapperPlayServerEntityMetadata(entityId, meta));
        } catch (Throwable ignored) {
        }
    }

    public static void forceInvisibleArmorStand(Player viewer, int entityId) {
        if (viewer == null || entityId == 0) return;
        try {
            List<EntityData<?>> meta = new ArrayList<>(4);
            meta.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20));
            meta.add(new EntityData<>(4, EntityDataTypes.BOOLEAN, true));
            meta.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true));
            meta.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) (0x08 | 0x10)));
            send(viewer, new WrapperPlayServerEntityMetadata(entityId, meta));
        } catch (Throwable ignored) {
        }
    }

    public static void setEquipment(Player viewer, int entityId, EquipmentSlot slot, ItemStack bukkitItem) {
        if (viewer == null || entityId == 0 || slot == null || bukkitItem == null) return;
        try {
            var peItem = SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
            send(viewer, new WrapperPlayServerEntityEquipment(
                    entityId,
                    List.of(new Equipment(slot, peItem))
            ));
        } catch (Throwable ignored) {
        }
    }

    public static void setPassengers(Player viewer, int vehicleEntityId, int... passengerIds) {
        if (viewer == null || vehicleEntityId == 0) return;
        try {
            send(viewer, new WrapperPlayServerSetPassengers(
                    vehicleEntityId,
                    passengerIds == null ? new int[0] : passengerIds
            ));
        } catch (Throwable ignored) {
        }
    }

    public static void teleportFakeEntity(Player viewer, int entityId, Location loc) {
        if (viewer == null || loc == null || entityId == 0) return;
        try {
            send(viewer, new WrapperPlayServerEntityTeleport(
                    entityId,
                    new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                    loc.getYaw(),
                    loc.getPitch(),
                    false
            ));
            send(viewer, new WrapperPlayServerEntityHeadLook(entityId, loc.getYaw()));
        } catch (Throwable ignored) {
        }
    }

    public static void destroyEntity(Player viewer, int entityId) {
        if (viewer == null || entityId == 0) return;
        try {
            send(viewer, new WrapperPlayServerDestroyEntities(entityId));
        } catch (Throwable ignored) {
        }
    }

    private static EntityType resolveEntityType(String name) {
        if (name == null || name.isBlank()) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        try {
            EntityType typed = EntityTypes.getByName(key);
            if (typed != null) return typed;
            return EntityTypes.getByName("minecraft:" + key);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
