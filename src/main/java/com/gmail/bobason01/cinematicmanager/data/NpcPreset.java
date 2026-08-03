package com.gmail.bobason01.cinematicmanager.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reusable NPC definition managed outside a single cinematic timeline.
 */
public final class NpcPreset {
    private final String id;
    private String provider; // vanilla | mythicmobs | modelengine
    private String entityType;
    private String name;
    private String skin;
    private String mobId;
    private NpcEquipment equipment = new NpcEquipment();
    private NpcCosmetics cosmetics = new NpcCosmetics();

    public NpcPreset(String id) {
        this.id = id;
        this.provider = "vanilla";
        this.entityType = "PLAYER";
        this.name = id;
        this.skin = id;
        this.mobId = "";
    }

    public String getId() { return id; }
    public String getProvider() { return provider; }
    public String getEntityType() { return entityType; }
    public String getName() { return name; }
    public String getSkin() { return skin; }
    public String getMobId() { return mobId; }
    public NpcEquipment getEquipment() { return equipment; }
    public NpcCosmetics getCosmetics() { return cosmetics; }

    public void setProvider(String provider) { this.provider = provider == null ? "vanilla" : provider; }
    public void setEntityType(String entityType) { this.entityType = entityType == null ? "PLAYER" : entityType; }
    public void setName(String name) { this.name = name == null ? id : name; }
    public void setSkin(String skin) { this.skin = skin == null ? "" : skin; }
    public void setMobId(String mobId) { this.mobId = mobId == null ? "" : mobId; }
    public void setEquipment(NpcEquipment equipment) {
        this.equipment = equipment == null ? new NpcEquipment() : equipment;
    }
    public void setCosmetics(NpcCosmetics cosmetics) {
        this.cosmetics = cosmetics == null ? new NpcCosmetics() : cosmetics;
    }

    /** Runtime spawn value consumed by CinematicSession.handleSpawn. */
    public String asSpawnValue() {
        return switch (provider.toLowerCase()) {
            case "mythicmobs" -> "mythicmobs:" + mobId;
            case "modelengine" -> "modelengine:" + mobId;
            default -> {
                StringBuilder out = new StringBuilder("npc:")
                        .append(entityType == null || entityType.isBlank() ? "PLAYER" : entityType.toUpperCase())
                        .append(':')
                        .append(name == null || name.isBlank() ? id : name);
                if (skin != null && !skin.isBlank()) out.append(':').append(skin);
                yield out.toString();
            }
        };
    }

    /** Equipment + optional HMC cosmetics for SPAWN_NPC action.extra. */
    public String asEquipmentExtra() {
        String eq = equipment == null || equipment.isEmpty() ? null : equipment.encode();
        return NpcCosmetics.mergeExtra(eq, cosmetics);
    }

    public void serialize(YamlConfiguration yaml) {
        yaml.set("id", id);
        yaml.set("provider", provider);
        yaml.set("entityType", entityType);
        yaml.set("name", name);
        yaml.set("skin", skin);
        yaml.set("mobId", mobId);
        yaml.set("equipment", null);
        if (equipment != null && !equipment.isEmpty()) {
            ConfigurationSection section = yaml.createSection("equipment");
            equipment.writeYaml(section);
        }
        yaml.set("cosmetics", null);
        if (cosmetics != null && !cosmetics.isEmpty()) {
            ConfigurationSection section = yaml.createSection("cosmetics");
            cosmetics.writeYaml(section);
        }
    }

    public static NpcPreset deserialize(YamlConfiguration yaml, String fallbackId) {
        String id = yaml.getString("id", fallbackId);
        NpcPreset preset = new NpcPreset(id);
        preset.setProvider(yaml.getString("provider", "vanilla"));
        preset.setEntityType(yaml.getString("entityType", "PLAYER"));
        preset.setName(yaml.getString("name", id));
        preset.setSkin(yaml.getString("skin", ""));
        preset.setMobId(yaml.getString("mobId", ""));
        preset.setEquipment(NpcEquipment.fromYaml(yaml.getConfigurationSection("equipment")));
        preset.setCosmetics(NpcCosmetics.fromYaml(yaml.getConfigurationSection("cosmetics")));
        return preset;
    }
}
