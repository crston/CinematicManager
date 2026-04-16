package com.gmail.bobason01.cinematicmanager.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import java.io.Serializable;

public class CinematicAction implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum ActionType {
        SPAWN_NPC,
        MOVE_NPC,
        CAMERA,
        SOUND,
        PARTICLE,
        TITLE,
        MESSAGE,
        COMMAND,
        HIDE_ENTITY,
        SHOW_ENTITY,
        SCALE,
        ANIMATION
    }

    public enum TrackType { ACTION, CAMERA, EFFECT }

    private final ActionType type;
    private final String value;
    private final String extra;
    private final double x, y, z;
    private final float yaw, pitch;
    private final String world;

    public CinematicAction(ActionType type, String value, Location loc, String extra) {
        this.type = type;
        this.value = value;
        this.extra = extra;
        if (loc != null) {
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.yaw = loc.getYaw();
            this.pitch = loc.getPitch();
            this.world = loc.getWorld().getName();
        } else {
            this.x = 0; this.y = 0; this.z = 0;
            this.yaw = 0; this.pitch = 0;
            this.world = "world";
        }
    }

    public ActionType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getExtra() {
        return extra;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public String getWorldName() { return world; }

    public Location getLocation() {
        if (Bukkit.getWorld(world) == null) return null;
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }

    public TrackType getTrackType() {
        if (type == ActionType.CAMERA) {
            return TrackType.CAMERA;
        }
        if (type == ActionType.SOUND || type == ActionType.PARTICLE ||
                type == ActionType.TITLE || type == ActionType.MESSAGE || type == ActionType.COMMAND) {
            return TrackType.EFFECT;
        }
        return TrackType.ACTION;
    }
}