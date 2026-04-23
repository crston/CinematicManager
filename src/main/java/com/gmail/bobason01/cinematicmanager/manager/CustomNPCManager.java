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
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.PlayerWatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomNPCManager {

    private final CinematicManager plugin;
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    public CustomNPCManager(CinematicManager plugin) {
        this.plugin = plugin;
    }

    public Entity spawnNPC(Player viewer, Location loc, String type, String name, String skin) {
        ArmorStand as = createBase(loc);
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
                // 모델 엔진의 부드러운 회전을 위해 베이스 엔티티 설정 최적화
                me.setBaseEntityVisible(false);
                hideFromOthers(viewer, as);
                return as;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void move(Player viewer, Entity entity, Location loc) {
        if (entity == null || !entity.isValid()) return;

        Location lastLoc = lastLocations.get(entity.getUniqueId());
        // 이동 거리 계산 (부자연스러움 해결을 위해 감도 조정)
        double distance = lastLoc != null ? loc.distanceSquared(lastLoc) : 0;
        boolean isMoving = distance > 0.001; // 약 0.03블록 이상의 움직임 감지

        // 1. 물리적 위치 갱신
        entity.teleport(loc);

        // 2. ModelEngine 전용 최적화 처리
        if (Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {
            ModeledEntity me = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
            if (me != null) {
                for (ActiveModel model : me.getModels().values()) {
                    if (isMoving) {
                        // 'walk' 애니메이션이 있다면 강제 재생 (속도 1.0)
                        if (!model.getAnimationHandler().isPlayingAnimation("walk")) {
                            model.getAnimationHandler().playAnimation("walk", 0.2, 0.2, 1.0, true);
                        }
                    } else {
                        // 멈췄을 때 즉시 중단이 아니라 자연스럽게 stop
                        model.getAnimationHandler().stopAnimation("walk");
                    }
                }
            }
        }

        // 3. 패킷 기반 부드러운 이동 전송
        sendMovePackets(viewer, entity, loc);
        lastLocations.put(entity.getUniqueId(), loc.clone());
    }

    public void playAnimation(Player viewer, Entity entity, String anim) {
        if (entity == null || anim == null || !entity.isValid()) return;
        String upper = anim.toUpperCase();

        if (Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {
            ModeledEntity me = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
            if (me != null) {
                for (ActiveModel model : me.getModels().values()) {
                    if (upper.startsWith("STOP:")) {
                        model.getAnimationHandler().stopAnimation(anim.substring(5));
                    } else {
                        // 극강의 성능을 위해 보간 시간(lerpIn/Out)을 0.1로 고정
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

    private void sendMovePackets(Player viewer, Entity entity, Location loc) {
        try {
            // ENTITY_TELEPORT 패킷 사용 (가장 정확한 동기화)
            PacketContainer tp = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
            tp.getIntegers().write(0, entity.getEntityId());
            tp.getDoubles().write(0, loc.getX()).write(1, loc.getY()).write(2, loc.getZ());
            tp.getBytes().write(0, (byte) (loc.getYaw() * 256 / 360)).write(1, (byte) (loc.getPitch() * 256 / 360));
            tp.getBooleans().write(0, false); // OnGround 상태 false로 고정하여 공중 이동 시 어색함 방지

            // 머리 회전 패킷 추가 (ModelEngine의 시선 처리에 필수)
            PacketContainer head = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            head.getIntegers().write(0, entity.getEntityId());
            head.getBytes().write(0, (byte) (loc.getYaw() * 256 / 360));

            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, tp);
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, head);
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