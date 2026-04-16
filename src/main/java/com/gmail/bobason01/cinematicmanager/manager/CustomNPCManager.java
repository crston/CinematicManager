package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.PlayerWatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomNPCManager {

    private final CinematicManager plugin;
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    public CustomNPCManager(CinematicManager plugin) {
        this.plugin = plugin;
    }

    public Entity spawnNPC(Player viewer, Location loc, String name, String skin) {
        ArmorStand as = createBase(loc);
        PlayerDisguise disguise = new PlayerDisguise(skin);
        disguise.setName(name);
        disguise.getWatcher().setInvisible(false);
        disguise.getWatcher().setCustomNameVisible(true);
        DisguiseAPI.disguiseToPlayers(as, disguise, viewer);
        hideFromOthers(viewer, as);
        return as;
    }

    public Entity spawnMythicMob(Player viewer, String mobKey, Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) return null;
        try {
            Entity entity = MythicBukkit.inst().getAPIHelper().spawnMythicMob(mobKey, loc);
            if (entity != null) {
                entity.setPersistent(false);
                hideFromOthers(viewer, entity);
                return entity;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public Entity spawnModelEngine(Player viewer, String modelId, Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) return null;
        try {
            ArmorStand as = createBase(loc);
            ActiveModel model = ModelEngineAPI.createActiveModel(modelId);
            if (model != null) {
                ModeledEntity me = ModelEngineAPI.getOrCreateModeledEntity(as);
                me.addModel(model, true);
                hideFromOthers(viewer, as);
                return as;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void move(Player viewer, Entity entity, Location loc) {
        if (entity == null || !entity.isValid()) return;
        Location lastLoc = lastLocations.get(entity.getUniqueId());
        double distance = lastLoc != null ? loc.distance(lastLoc) : 0;

        entity.teleport(loc);

        if (Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {
            ModeledEntity me = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
            if (me != null) {
                for (ActiveModel model : me.getModels().values()) {
                    if (distance > 0.005) model.getAnimationHandler().playAnimation("walk", 0.1, 0.1, 1, true);
                    else model.getAnimationHandler().stopAnimation("walk");
                }
            }
        }
        sendMovePackets(viewer, entity, loc);
        lastLocations.put(entity.getUniqueId(), loc.clone());
    }

    public void playAnimation(Player viewer, Entity entity, String anim) {
        if (entity == null || anim == null || !entity.isValid()) return;
        String upper = anim.toUpperCase();

        // 1. ModelEngine 애니메이션 처리
        if (Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {
            ModeledEntity me = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
            if (me != null) {
                for (ActiveModel model : me.getModels().values()) {
                    if (upper.startsWith("STOP:")) model.getAnimationHandler().stopAnimation(anim.substring(5));
                    else model.getAnimationHandler().playAnimation(anim, 0.1, 0.1, 1, true);
                }
                return;
            }
        }

        // 2. LibsDisguises 애니메이션 및 상태 처리
        if (DisguiseAPI.isDisguised(entity)) {
            Disguise disguise = DisguiseAPI.getDisguise(entity);
            if (disguise.getWatcher() instanceof PlayerWatcher watcher) {
                try {
                    switch (upper) {
                        case "SWING" -> sendAnimationPacket(viewer, entity, 0);
                        case "SPIN_ON" -> watcher.setSpinning(true);
                        case "SPIN_OFF" -> watcher.setSpinning(false);
                        case "SPRINT_ON" -> watcher.setSprinting(true);
                        case "SPRINT_OFF" -> watcher.setSprinting(false);
                        case "SWIM_ON" -> watcher.setSwimming(true);
                        case "SWIM_OFF" -> watcher.setSwimming(false);
                        case "SNEAK_ON" -> watcher.setSneaking(true);
                        case "SNEAK_OFF" -> watcher.setSneaking(false);
                        case "SLEEP_ON" -> watcher.setSleeping(true);
                        case "SLEEP_OFF" -> watcher.setSleeping(false);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void sendMovePackets(Player viewer, Entity entity, Location loc) {
        try {
            PacketContainer tp = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
            tp.getIntegers().write(0, entity.getEntityId());
            tp.getDoubles().write(0, loc.getX()).write(1, loc.getY()).write(2, loc.getZ());
            tp.getBytes().write(0, (byte) (loc.getYaw() * 256 / 360)).write(1, (byte) (loc.getPitch() * 256 / 360));
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, tp);
        } catch (Exception ignored) {}
    }

    private void sendAnimationPacket(Player viewer, Entity entity, int id) {
        try {
            PacketContainer p = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ANIMATION);
            p.getIntegers().write(0, entity.getEntityId()).write(1, id);
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, p);
        } catch (Exception ignored) {}
    }

    private ArmorStand createBase(Location loc) {
        return loc.getWorld().spawn(loc, ArmorStand.class, entity -> {
            entity.setMarker(true);
            entity.setInvisible(true);
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setBasePlate(false);
            entity.setSmall(true);
        });
    }

    private void hideFromOthers(Player viewer, Entity entity) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(viewer.getUniqueId())) online.hideEntity(plugin, entity);
        }
    }

    public void remove(Entity entity) {
        if (entity != null) {
            lastLocations.remove(entity.getUniqueId());
            entity.remove();
        }
    }
}