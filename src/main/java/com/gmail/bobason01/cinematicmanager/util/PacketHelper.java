package com.gmail.bobason01.cinematicmanager.util;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class PacketHelper {

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

    public static void sendEntityTeleport(Player viewer, Entity entity, Location loc) {
        WrapperPlayServerEntityTeleport teleport = new WrapperPlayServerEntityTeleport(
                entity.getEntityId(),
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                loc.getYaw(),
                loc.getPitch(),
                false
        );
        WrapperPlayServerEntityHeadLook head = new WrapperPlayServerEntityHeadLook(
                entity.getEntityId(),
                loc.getYaw()
        );
        send(viewer, teleport);
        send(viewer, head);
    }

    public static void sendEntityAnimation(Player viewer, Entity entity, int animationId) {
        WrapperPlayServerEntityAnimation.EntityAnimationType type =
                WrapperPlayServerEntityAnimation.EntityAnimationType.getById(animationId);
        if (type == null) {
            type = WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM;
        }
        send(viewer, new WrapperPlayServerEntityAnimation(entity.getEntityId(), type));
    }
}
