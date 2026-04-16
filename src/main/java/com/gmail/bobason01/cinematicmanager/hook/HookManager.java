package com.gmail.bobason01.cinematicmanager.hook;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

public class HookManager {

    private final CinematicManager plugin;

    public HookManager(CinematicManager plugin) {
        this.plugin = plugin;
    }

    public Entity spawnNPC(Location loc, String name, String skin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) return null;

        ArmorStand as = loc.getWorld().spawn(loc, ArmorStand.class, entity -> {
            entity.setMarker(true);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setBasePlate(false);
            entity.setInvisible(false); // 처음에는 보이게 설정하여 변장이 씹히는 현상 방지
        });

        PlayerDisguise disguise = new PlayerDisguise(skin);
        disguise.setName(name);

        // 워처 설정을 통해 투명화 방지 및 이름 표시 강제
        disguise.getWatcher().setInvisible(false);
        disguise.getWatcher().setCustomNameVisible(true);

        DisguiseAPI.disguiseToAll(as, disguise);

        return as;
    }

    public Entity spawnMythicMob(String mobKey, Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) return null;
        try {
            // APIHelper를 사용하여 심볼 에러 원천 차단
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