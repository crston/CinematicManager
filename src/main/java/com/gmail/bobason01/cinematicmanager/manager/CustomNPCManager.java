package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.util.PacketHelper;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.generator.blueprint.BlueprintBone;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.ModelBone;
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

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
        // Base must stay invisible — if the disguise fails, a visible stand is what players see.
        as.setInvisible(true);
        as.setMarker(true);
        Disguise disguise;

        if (type.equalsIgnoreCase("PLAYER")) {
            String skinName = (skin == null || skin.isBlank()) ? name : skin;
            disguise = new PlayerDisguise(skinName);
            ((PlayerDisguise) disguise).setName(name);
        } else {
            try {
                EntityType entityType = EntityType.valueOf(type.toUpperCase());
                disguise = new MobDisguise(DisguiseType.getType(entityType));
            } catch (Exception e) {
                String skinName = (skin == null || skin.isBlank()) ? name : skin;
                disguise = new PlayerDisguise(skinName);
                ((PlayerDisguise) disguise).setName(name);
            }
        }

        try {
            disguise.setEntity(as);
        } catch (Throwable ignored) {
        }
        disguise.getWatcher().setInvisible(false);
        disguise.getWatcher().setCustomNameVisible(true);
        if (!(disguise instanceof PlayerDisguise)) {
            disguise.getWatcher().setCustomName(name);
        }

        DisguiseAPI.disguiseToPlayers(as, disguise, viewer);
        // Re-assert after disguise attach (some LD builds flip base flags).
        as.setInvisible(true);
        viewer.showEntity(plugin, as);
        hideFromOthers(viewer, as);
        final ArmorStand base = as;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!base.isValid()) return;
            base.setInvisible(true);
            if (!DisguiseAPI.isDisguised(base)) {
                plugin.getLogger().warning("LibsDisguises failed to attach NPC disguise for viewer "
                        + viewer.getName() + " — base armor stand stays hidden.");
            }
        });
        return as;
    }

    public Entity spawnMythicMob(Player viewer, String mobKey, Location loc) {
        return spawnMythicMob(viewer, mobKey, loc, false);
    }

    /**
     * @param visualFx when true, keep Mythic AI/timers/gravity so armor-stand
     *                 frame animations ({@code equip{delay=N}}) play correctly.
     */
    public Entity spawnMythicMob(Player viewer, String mobKey, Location loc, boolean visualFx) {
        if (!mythicEnabled) return null;
        try {
            Entity entity = MythicBukkit.inst().getAPIHelper().spawnMythicMob(mobKey, loc);
            if (entity != null) {
                entity.setPersistent(false);
                if (!visualFx) {
                    entity.setGravity(false);
                    if (entity instanceof LivingEntity living) {
                        living.setAI(false);
                        living.setCollidable(false);
                    }
                }
                if (viewer != null) {
                    viewer.showEntity(plugin, entity);
                    hideFromOthers(viewer, entity);
                }
                return entity;
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("MythicMob spawn failed: " + mobKey + " — " + exception.getMessage());
        }
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
            // ME can reset Bukkit flags on attach — re-hide the hitbox so only the model shows.
            as.setInvisible(true);
            as.setMarker(true);
            as.setBasePlate(false);
            me.setBaseEntityVisible(false);
            viewer.showEntity(plugin, as);
            hideFromOthers(viewer, as);
            // Some ME builds re-sync visibility one tick later; enforce again.
            final ArmorStand base = as;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!base.isValid()) return;
                base.setInvisible(true);
                base.setMarker(true);
                ModeledEntity modeled = ModelEngineAPI.getModeledEntity(base.getUniqueId());
                if (modeled != null) modeled.setBaseEntityVisible(false);
            });
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

        var hmc = plugin.getHmcCosmeticsHook();
        if (hmc != null && hmc.isEnabled()) {
            hmc.syncBackpacks(entity);
        }

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
        String raw = anim.trim();
        if (raw.isEmpty()) return;

        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("remap:")) {
            applyRemapModel(entity, raw.substring(6).trim());
            return;
        }
        if (lower.startsWith("changepart:")) {
            applyChangePart(entity, raw.substring(11).trim());
            return;
        }

        String upper = raw.toUpperCase(Locale.ROOT);
        if (modelEngineEnabled) {
            ModeledEntity me = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
            if (me != null) {
                for (ActiveModel model : me.getModels().values()) {
                    if (upper.startsWith("STOP:")) {
                        model.getAnimationHandler().stopAnimation(raw.substring(5).trim());
                    } else {
                        model.getAnimationHandler().playAnimation(raw, 0.1, 0.1, 1.0, true);
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

    /**
     * Remap ModelEngine bones from another blueprint.
     * Spec: {@code [modelId>]newModelId[|mapId]}
     */
    public void applyRemapModel(Entity entity, String spec) {
        if (!modelEngineEnabled || entity == null || !entity.isValid() || spec == null || spec.isBlank()) {
            return;
        }
        String modelId = null;
        String newModelId;
        String mapId = null;
        String body = spec.trim();
        int pipe = body.indexOf('|');
        if (pipe >= 0) {
            mapId = body.substring(pipe + 1).trim();
            if (mapId.isEmpty()) mapId = null;
            body = body.substring(0, pipe).trim();
        }
        int gt = body.indexOf('>');
        if (gt >= 0) {
            modelId = emptyToNull(body.substring(0, gt).trim());
            newModelId = body.substring(gt + 1).trim();
        } else {
            newModelId = body;
        }
        if (newModelId == null || newModelId.isBlank()) {
            plugin.getLogger().warning("remap_model missing newModel id: '" + spec + "'");
            return;
        }

        ModeledEntity me = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
        if (me == null) {
            plugin.getLogger().warning("remap_model skipped — entity has no ModeledEntity.");
            return;
        }
        ActiveModel active = resolveActiveModel(me, modelId);
        if (active == null) {
            plugin.getLogger().warning("remap_model skipped — ActiveModel not found"
                    + (modelId != null ? " '" + modelId + "'" : "") + ".");
            return;
        }
        ModelBlueprint neu = ModelEngineAPI.getBlueprint(newModelId);
        if (neu == null) {
            plugin.getLogger().warning("remap_model unknown blueprint: '" + newModelId + "'");
            return;
        }
        Map<String, BlueprintBone> neuFlat = neu.getFlatMap();
        Iterable<String> keys;
        if (mapId != null) {
            ModelBlueprint mapBp = ModelEngineAPI.getBlueprint(mapId);
            if (mapBp == null) {
                plugin.getLogger().warning("remap_model unknown map blueprint: '" + mapId + "'");
                return;
            }
            keys = mapBp.getFlatMap().keySet();
        } else {
            java.util.ArrayList<String> rendererKeys = new java.util.ArrayList<>();
            for (Map.Entry<String, BlueprintBone> entry : neuFlat.entrySet()) {
                BlueprintBone bb = entry.getValue();
                if (bb != null && bb.isRenderer()) {
                    rendererKeys.add(entry.getKey());
                }
            }
            keys = rendererKeys;
        }
        int replaced = 0;
        for (String boneId : keys) {
            BlueprintBone bb = neuFlat.get(boneId);
            if (bb == null) continue;
            Optional<ModelBone> bone = active.getBone(boneId);
            if (bone.isEmpty()) continue;
            ModelBone live = bone.get();
            live.setModel(bb);
            live.setModelScale(bb.getScale());
            replaced++;
        }
        if (replaced == 0) {
            plugin.getLogger().warning("remap_model matched 0 bones for '" + newModelId + "' on entity.");
        }
    }

    /**
     * Change a single ModelEngine bone model.
     * Spec: {@code [modelId:]partId>newModelId:newPartId}
     */
    public void applyChangePart(Entity entity, String spec) {
        if (!modelEngineEnabled || entity == null || !entity.isValid() || spec == null || spec.isBlank()) {
            return;
        }
        String body = spec.trim();
        int gt = body.indexOf('>');
        if (gt < 0) {
            plugin.getLogger().warning("change_part format: part>newModel:newPart (got '" + spec + "')");
            return;
        }
        String left = body.substring(0, gt).trim();
        String right = body.substring(gt + 1).trim();
        String modelId = null;
        String partId;
        int colonLeft = left.indexOf(':');
        if (colonLeft >= 0) {
            modelId = emptyToNull(left.substring(0, colonLeft).trim());
            partId = left.substring(colonLeft + 1).trim();
        } else {
            partId = left;
        }
        int colonRight = right.indexOf(':');
        if (colonRight < 0) {
            plugin.getLogger().warning("change_part format: part>newModel:newPart (got '" + spec + "')");
            return;
        }
        String newModelId = right.substring(0, colonRight).trim();
        String newPartId = right.substring(colonRight + 1).trim();
        if (partId.isEmpty() || newModelId.isEmpty() || newPartId.isEmpty()) {
            plugin.getLogger().warning("change_part incomplete: '" + spec + "'");
            return;
        }

        ModeledEntity me = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
        if (me == null) {
            plugin.getLogger().warning("change_part skipped — entity has no ModeledEntity.");
            return;
        }
        ActiveModel active = resolveActiveModel(me, modelId);
        if (active == null) {
            plugin.getLogger().warning("change_part skipped — ActiveModel not found"
                    + (modelId != null ? " '" + modelId + "'" : "") + ".");
            return;
        }
        ModelBlueprint neu = ModelEngineAPI.getBlueprint(newModelId);
        if (neu == null) {
            plugin.getLogger().warning("change_part unknown blueprint: '" + newModelId + "'");
            return;
        }
        BlueprintBone bb = neu.getFlatMap().get(newPartId);
        if (bb == null) {
            plugin.getLogger().warning("change_part unknown part '" + newPartId
                    + "' in model '" + newModelId + "'");
            return;
        }
        Optional<ModelBone> bone = active.getBone(partId);
        if (bone.isEmpty()) {
            plugin.getLogger().warning("change_part target bone missing: '" + partId + "'");
            return;
        }
        ModelBone live = bone.get();
        live.setModel(bb);
        live.setModelScale(bb.getScale());
    }

    private ActiveModel resolveActiveModel(ModeledEntity me, String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            ActiveModel exact = me.getModel(modelId).orElse(null);
            if (exact != null) return exact;
            // Some builds use getModels().get
            ActiveModel byKey = me.getModels().get(modelId);
            if (byKey != null) return byKey;
            return null;
        }
        if (me.getModels().isEmpty()) return null;
        return me.getModels().values().iterator().next();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
        if (entity == null) return;
        lastLocations.remove(entity.getUniqueId());
        var hmc = plugin.getHmcCosmeticsHook();
        if (hmc != null) hmc.clearAll(entity.getUniqueId());
        if (modelEngineEnabled) {
            try {
                ModeledEntity me = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
                if (me != null) ModelEngineAPI.removeModeledEntity(entity.getUniqueId());
            } catch (Throwable ignored) {
            }
        }
        entity.remove();
    }
}