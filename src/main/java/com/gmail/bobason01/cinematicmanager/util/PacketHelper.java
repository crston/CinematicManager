package com.gmail.bobason01.cinematicmanager.util;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class PacketHelper {
    private static final AtomicInteger FAKE_ENTITY_IDS = new AtomicInteger(1_900_000_000);

    private PacketHelper() {}

    public static void send(Player viewer, PacketWrapper<?> packet) {
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
