package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.util.PacketHelper;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.PlayerWatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomNPCManager {

    private final CinematicManager plugin;
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final boolean modelEngineEnabled;
    private final boolean mythicEnabled;

    public CustomNPCManager(CinematicManager plugin) {
        this.plugin = plugin;
        this.modelEngineEnabled = Bukkit.getPluginManager().isPluginEnabled("ModelEngine");
        this.mythicEnabled = Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    public Entity spawnNPC(Player viewer, Location loc, String type, String name, String skin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) {
            plugin.getLogger().warning("Cannot spawn cinematic NPC: LibsDisguises is not enabled.");
            return null;
        }
        ArmorStand as = createBase(loc);
        // LibsDisguises owns the visible metadata for vanilla cinematic NPCs.
        as.setInvisible(false);
        Disguise disguise;

        if (type.equalsIgnoreCase("PLAYER")) {
            disguise = new PlayerDisguise(skin);
            ((PlayerDisguise) disguise).setName(name);
        } else {
            try {
                EntityType entityType = EntityType.valueOf(type.toUpperCase());
                disguise = new MobDisguise(DisguiseType.getType(entityType));
            } catch (Exception e) {
                disguise = new PlayerDisguise(skin);
                ((PlayerDisguise) disguise).setName(name);
            }
        }

        disguise.getWatcher().setInvisible(false);
        disguise.getWatcher().setCustomNameVisible(true);
        if (!(disguise instanceof PlayerDisguise)) {
            disguise.getWatcher().setCustomName(name);
        }

        DisguiseAPI.disguiseToPlayers(as, disguise, viewer);
        viewer.showEntity(plugin, as);
        hideFromOthers(viewer, as);
        return as;
    }

    public Entity spawnMythicMob(Player viewer, String mobKey, Location loc) {
        if (!mythicEnabled) return null;
        try {
            Entity entity = MythicBukkit.inst().getAPIHelper().spawnMythicMob(mobKey, loc);
            if (entity != null) {
                entity.setPersistent(false);
                entity.setGravity(false);
                if (entity instanceof LivingEntity living) {
                    living.setAI(false);
                    living.setCollidable(false);
                }
                hideFromOthers(viewer, entity);
                return entity;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public Entity spawnModelEngine(Player viewer, String modelId, Location loc) {
        if (!modelEngineEnabled) return null;
        ArmorStand as = null;
        try {
            as = createBase(loc);
            ActiveModel model = ModelEngineAPI.createActiveModel(modelId);
            if (model == null) {
                plugin.getLogger().warning("ModelEngine model not found: " + modelId);
                as.remove();
                return null;
            }
            ModeledEntity me = ModelEngineAPI.getOrCreateModeledEntity(as);
            me.addModel(model, true);
            // 모델 엔진의 부드러운 회전을 위해 베이스 엔티티 설정 최적화
            me.setBaseEntityVisible(false);
            hideFromOthers(viewer, as);
            return as;
        } catch (Exception exception) {
            if (as != null) as.remove();
            plugin.getLogger().warning("ModelEngine spawn failed for '" + modelId
                    + "': " + exception.getMessage());
        }
        return null;
    }

    public void move(Player viewer, Entity entity, Location loc) {
        if (entity == null || !entity.isValid()) return;

        UUID id = entity.getUniqueId();
        Location lastLoc = lastLocations.get(id);
        double dx = 0, dy = 0, dz = 0;
        boolean rotChanged = true;
        if (lastLoc != null) {
            dx = loc.getX() - lastLoc.getX();
            dy = loc.getY() - lastLoc.getY();
            dz = loc.getZ() - lastLoc.getZ();
            rotChanged = loc.getYaw() != lastLoc.getYaw() || loc.getPitch() != lastLoc.getPitch();
            if (dx * dx + dy * dy + dz * dz < 1.0E-6 && !rotChanged) {
                return; // 완전 정지 → 패킷/teleport 스킵
            }
        }
        boolean isMoving = (dx * dx + dy * dy + dz * dz) > 0.001;

        entity.teleport(loc);

        if (modelEngineEnabled) {
            ModeledEntity me = ModelEngineAPI.getModeledEntity(id);
            if (me != null) {
                for (ActiveModel model : me.getModels().values()) {
                    if (isMoving) {
                        if (!model.getAnimationHandler().isPlayingAnimation("walk")) {
                            model.getAnimationHandler().playAnimation("walk", 0.2, 0.2, 1.0, true);
                        }
                    } else {
                        model.getAnimationHandler().stopAnimation("walk");
                    }
                }
            }
        }

        Location stored = lastLocations.get(id);
        if (stored == null) {
            lastLocations.put(id, loc.clone());
        } else {
            stored.setWorld(loc.getWorld());
            stored.setX(loc.getX());
            stored.setY(loc.getY());
            stored.setZ(loc.getZ());
            stored.setYaw(loc.getYaw());
            stored.setPitch(loc.getPitch());
        }
    }

    public void playAnimation(Player viewer, Entity entity, String anim) {
        if (entity == null || anim == null || !entity.isValid()) return;
        String upper = anim.toUpperCase();

        if (modelEngineEnabled) {
            ModeledEntity me = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
            if (me != null) {
                for (ActiveModel model : me.getModels().values()) {
                    if (upper.startsWith("STOP:")) {
                        model.getAnimationHandler().stopAnimation(anim.substring(5));
                    } else {
                        model.getAnimationHandler().playAnimation(anim, 0.1, 0.1, 1.0, true);
                    }
                }
                return;
            }
        }

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

    private void sendAnimationPacket(Player viewer, Entity entity, int id) {
        try {
            PacketHelper.sendEntityAnimation(viewer, entity, id);
        } catch (Exception ignored) {}
    }

    private ArmorStand createBase(Location loc) {
        return loc.getWorld().spawn(loc, ArmorStand.class, entity -> {
            entity.setMarker(true);
            // ModelEngine/Mythic bases must stay hidden. spawnNPC explicitly
            // enables this only for a LibsDisguises-backed vanilla NPC.
            entity.setInvisible(true);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setBasePlate(false);
            entity.setSmall(true);
            // 아머스탠드의 기본 AI 회전 방지
            entity.setRotation(loc.getYaw(), loc.getPitch());
        });
    }

    private void hideFromOthers(Player viewer, Entity entity) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(viewer.getUniqueId())) {
                online.hideEntity(plugin, entity);
            }
        }
    }

    public void remove(Entity entity) {
        if (entity != null) {
            lastLocations.remove(entity.getUniqueId());
            entity.remove();
        }
    }
}