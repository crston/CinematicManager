package com.gmail.bobason01.cinematicmanager.hook;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

public class HookManager {

    private final CinematicManager plugin;

    public HookManager(CinematicManager plugin) {
        this.plugin = plugin;
    }

    public Entity spawnNPC(Location loc, String type, String name, String skin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) return null;

        ArmorStand as = loc.getWorld().spawn(loc, ArmorStand.class, entity -> {
            entity.setMarker(true);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setBasePlate(false);
            entity.setInvisible(false);
        });

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

        DisguiseAPI.disguiseToAll(as, disguise);

        return as;
    }

    public Entity spawnMythicMob(String mobKey, Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) return null;
        try {
            return MythicBukkit.inst().getAPIHelper().spawnMythicMob(mobKey, loc);
        } catch (Exception e) {
            plugin.getLogger().warning("MythicMob spawn failed: " + mobKey);
        }
        return null;
    }

    public Entity spawnModelEngine(String modelId, Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) return null;
        try {
            ArmorStand as = loc.getWorld().spawn(loc, ArmorStand.class, entity -> {
                entity.setInvisible(true);
                entity.setPersistent(false);
                entity.setInvulnerable(true);
            });
            ActiveModel model = ModelEngineAPI.createActiveModel(modelId);
            if (model != null) {
                ModelEngineAPI.getOrCreateModeledEntity(as).addModel(model, true);
            }
            return as;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}