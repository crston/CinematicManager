package com.gmail.bobason01.cinematicmanager.data;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.FlagWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.LivingWatcher;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Compact NPC gear for cinematic visuals.
 * Encoded as {@code HAND=DIAMOND_SWORD;HEAD=LEATHER_HELMET#d=16711680#c=12#m=pack:hero_helm}.
 * <ul>
 *   <li>{@code #c=} legacy CustomModelData (int)</li>
 *   <li>{@code #m=} Item Model NamespacedKey (1.21.4+)</li>
 *   <li>{@code #d=} leather dye RGB (0xRRGGBB)</li>
 * </ul>
 */
public final class NpcEquipment {

    public enum Slot {
        HEAD(EquipmentSlot.HEAD, "head", Material.LEATHER_HELMET),
        CHEST(EquipmentSlot.CHEST, "chest", Material.LEATHER_CHESTPLATE),
        LEGS(EquipmentSlot.LEGS, "legs", Material.LEATHER_LEGGINGS),
        FEET(EquipmentSlot.FEET, "feet", Material.LEATHER_BOOTS),
        HAND(EquipmentSlot.HAND, "hand", Material.IRON_SWORD),
        OFF(EquipmentSlot.OFF_HAND, "offhand", Material.SHIELD);

        final EquipmentSlot bukkit;
        final String yamlKey;
        final Material icon;

        Slot(EquipmentSlot bukkit, String yamlKey, Material icon) {
            this.bukkit = bukkit;
            this.yamlKey = yamlKey;
            this.icon = icon;
        }

        public Material getIcon() { return icon; }
        public String getYamlKey() { return yamlKey; }

        public static Slot fromYamlKey(String key) {
            if (key == null) return null;
            String k = key.toLowerCase(Locale.ROOT);
            for (Slot slot : values()) {
                if (slot.yamlKey.equals(k) || slot.name().equalsIgnoreCase(k)) return slot;
            }
            if ("mainhand".equals(k) || "main".equals(k)) return HAND;
            if ("off".equals(k)) return OFF;
            return null;
        }
    }

    /** slot → MATERIAL[#c=cmd] — null/absent means empty */
    private final EnumMap<Slot, String> items = new EnumMap<>(Slot.class);

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public String getEncoded(Slot slot) {
        return items.get(slot);
    }

    public ItemStack getItem(Slot slot) {
        return decodeItem(items.get(slot));
    }

    public void setItem(Slot slot, ItemStack stack) {
        if (slot == null) return;
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            items.remove(slot);
            return;
        }
        items.put(slot, encodeItem(stack));
    }

    public void clear(Slot slot) {
        if (slot != null) items.remove(slot);
    }

    public void clearAll() {
        items.clear();
    }

    /** Wire format for action.extra / preset. Empty → null. */
    public String encode() {
        if (items.isEmpty()) return null;
        StringBuilder out = new StringBuilder(items.size() * 24);
        boolean first = true;
        for (Slot slot : Slot.values()) {
            String enc = items.get(slot);
            if (enc == null) continue;
            if (!first) out.append(';');
            out.append(slot.name()).append('=').append(enc);
            first = false;
        }
        return first ? null : out.toString();
    }

    public static NpcEquipment parse(String encoded) {
        NpcEquipment eq = new NpcEquipment();
        if (encoded == null || encoded.isBlank()) return eq;
        int start = 0;
        int len = encoded.length();
        while (start < len) {
            int sep = encoded.indexOf(';', start);
            if (sep < 0) sep = len;
            int eqAt = encoded.indexOf('=', start);
            if (eqAt > start && eqAt < sep) {
                Slot slot = Slot.fromYamlKey(encoded.substring(start, eqAt).trim());
                if (slot != null) {
                    String val = encoded.substring(eqAt + 1, sep).trim();
                    if (!val.isEmpty() && !"-".equals(val)) eq.items.put(slot, val);
                }
            }
            start = sep + 1;
        }
        return eq;
    }

    public void writeYaml(ConfigurationSection section) {
        if (section == null) return;
        for (Slot slot : Slot.values()) {
            String enc = items.get(slot);
            if (enc != null) section.set(slot.yamlKey, enc);
        }
    }

    public static NpcEquipment fromYaml(ConfigurationSection section) {
        NpcEquipment eq = new NpcEquipment();
        if (section == null) return eq;
        for (Slot slot : Slot.values()) {
            String enc = section.getString(slot.yamlKey);
            if (enc != null && !enc.isBlank()) eq.items.put(slot, enc.trim());
        }
        return eq;
    }

    public static NpcEquipment fromMap(Object raw) {
        NpcEquipment eq = new NpcEquipment();
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return eq;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Slot slot = Slot.fromYamlKey(String.valueOf(entry.getKey()));
            if (slot == null || entry.getValue() == null) continue;
            String val = String.valueOf(entry.getValue()).trim();
            if (!val.isEmpty() && !"-".equals(val)) eq.items.put(slot, val);
        }
        return eq;
    }

    public Map<String, String> toMap() {
        Map<String, String> out = new java.util.LinkedHashMap<>(items.size());
        for (Slot slot : Slot.values()) {
            String enc = items.get(slot);
            if (enc != null) out.put(slot.yamlKey, enc);
        }
        return out;
    }

    /** Apply full outfit. Prefers LibsDisguises watcher, else LivingEntity equipment. */
    public void apply(Entity entity) {
        apply(entity, true);
    }

    /** @param clearMissing when true, unset slots are cleared (full outfit replace). */
    public void apply(Entity entity, boolean clearMissing) {
        if (entity == null || !entity.isValid()) return;
        if (items.isEmpty() && !clearMissing) return;

        ItemStack[] resolved = new ItemStack[Slot.values().length];
        Slot[] slots = Slot.values();
        boolean any = false;
        for (int i = 0; i < slots.length; i++) {
            if (items.containsKey(slots[i])) {
                resolved[i] = decodeItem(items.get(slots[i]));
                any = true;
            } else if (clearMissing) {
                resolved[i] = null;
                any = true;
            } else {
                resolved[i] = null; // skip marker — handled below
            }
        }
        if (!any && !clearMissing) return;

        // Cinematic vanilla NPCs are disguised ArmorStands — never write gear onto the
        // stand itself (floating armor / visible stand artifacts). Prefer the disguise watcher.
        if (tryApplyDisguise(entity, slots, resolved, clearMissing)) {
            return;
        }

        // Mythic / living mobs without disguise.
        if (entity instanceof ArmorStand) {
            return; // hidden base — gear belongs on disguise only
        }
        if (entity instanceof LivingEntity living) {
            EntityEquipment equipment = living.getEquipment();
            if (equipment == null) return;
            for (int i = 0; i < slots.length; i++) {
                if (!clearMissing && !items.containsKey(slots[i])) continue;
                equipment.setItem(slots[i].bukkit, resolved[i]);
            }
        }
    }

    private boolean tryApplyDisguise(Entity entity, Slot[] slots, ItemStack[] resolved,
                                     boolean clearMissing) {
        try {
            if (!DisguiseAPI.isDisguised(entity)) return false;
            Disguise disguise = DisguiseAPI.getDisguise(entity);
            if (disguise == null) return false;
            FlagWatcher watcher = disguise.getWatcher();
            if (!(watcher instanceof LivingWatcher living)) return false;
            applyLivingWatcher(living, slots, resolved, clearMissing, items);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void applyLivingWatcher(LivingWatcher watcher, Slot[] slots, ItemStack[] resolved,
                                            boolean clearMissing, EnumMap<Slot, String> items) {
        try {
            for (int i = 0; i < slots.length; i++) {
                if (!clearMissing && !items.containsKey(slots[i])) continue;
                ItemStack stack = resolved[i];
                switch (slots[i]) {
                    case HAND -> watcher.setItemInMainHand(stack);
                    case OFF -> watcher.setItemInOffHand(stack);
                    case HEAD -> watcher.setHelmet(stack);
                    case CHEST -> watcher.setChestplate(stack);
                    case LEGS -> watcher.setLeggings(stack);
                    case FEET -> watcher.setBoots(stack);
                }
            }
        } catch (Throwable ignored) {
            // Older/newer LibsDisguises API variants — fail soft.
        }
    }

    public static String encodeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        StringBuilder out = new StringBuilder(item.getType().name());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return out.toString();

        if (meta instanceof LeatherArmorMeta leather) {
            out.append("#d=").append(leather.getColor().asRGB());
        }
        try {
            if (meta.hasCustomModelData()) {
                out.append("#c=").append(meta.getCustomModelData());
            }
        } catch (Throwable ignored) {
            // 1.21.5+ may prefer component API; float CMD still maps via legacy getter on many builds.
        }
        try {
            if (meta.hasItemModel()) {
                NamespacedKey model = meta.getItemModel();
                if (model != null) out.append("#m=").append(model);
            }
        } catch (Throwable ignored) {
            // Pre-1.21.4 servers: no item model API.
        }
        return out.toString();
    }

    public static ItemStack decodeItem(String val) {
        if (val == null || val.isBlank() || "-".equals(val)) return null;

        String matName = val;
        Integer cmd = null;
        Integer dyeRgb = null;
        String itemModel = null;

        int firstHash = val.indexOf('#');
        if (firstHash >= 0) {
            matName = val.substring(0, firstHash);
            String[] tags = val.substring(firstHash + 1).split("#");
            for (String tag : tags) {
                if (tag.isEmpty()) continue;
                int eq = tag.indexOf('=');
                if (eq <= 0) continue;
                String key = tag.substring(0, eq);
                String body = tag.substring(eq + 1);
                switch (key) {
                    case "c" -> {
                        try { cmd = Integer.parseInt(body); } catch (NumberFormatException ignored) {}
                    }
                    case "d" -> {
                        try { dyeRgb = Integer.parseInt(body); } catch (NumberFormatException ignored) {}
                    }
                    case "m" -> {
                        if (!body.isBlank()) itemModel = body.trim();
                    }
                }
            }
        }

        Material mat;
        try {
            mat = Material.valueOf(matName);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (mat.isAir()) return null;

        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        if (cmd != null) {
            try {
                meta.setCustomModelData(cmd);
            } catch (Throwable ignored) {
            }
        }
        if (itemModel != null) {
            try {
                NamespacedKey key = NamespacedKey.fromString(itemModel);
                if (key != null) meta.setItemModel(key);
            } catch (Throwable ignored) {
            }
        }
        if (dyeRgb != null && meta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.fromRGB(dyeRgb & 0xFFFFFF));
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
