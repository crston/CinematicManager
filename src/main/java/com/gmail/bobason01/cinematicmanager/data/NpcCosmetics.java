package com.gmail.bobason01.cinematicmanager.data;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * HMC cosmetic id bindings by slot name. Wire: {@code HELMET=cool_hat;BACKPACK=wings}.
 */
public final class NpcCosmetics {
    public static final String EXTRA_PREFIX = "hmc:";
    public static final String EXTRA_SEP = "|hmc:";

    private final LinkedHashMap<String, String> bySlot = new LinkedHashMap<>();

    public boolean isEmpty() { return bySlot.isEmpty(); }

    public Map<String, String> view() { return Collections.unmodifiableMap(bySlot); }

    public String get(String slot) {
        return slot == null ? null : bySlot.get(slot.toUpperCase(Locale.ROOT));
    }

    public void set(String slot, String cosmeticId) {
        if (slot == null || slot.isBlank()) return;
        String key = slot.toUpperCase(Locale.ROOT);
        if (cosmeticId == null || cosmeticId.isBlank()) {
            bySlot.remove(key);
        } else {
            bySlot.put(key, cosmeticId.trim());
        }
    }

    public void clear(String slot) {
        if (slot != null) bySlot.remove(slot.toUpperCase(Locale.ROOT));
    }

    public void clearAll() { bySlot.clear(); }

    public String encode() {
        if (bySlot.isEmpty()) return null;
        StringBuilder out = new StringBuilder(bySlot.size() * 24);
        boolean first = true;
        for (Map.Entry<String, String> e : bySlot.entrySet()) {
            if (!first) out.append(';');
            out.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return first ? null : out.toString();
    }

    public static NpcCosmetics parse(String encoded) {
        NpcCosmetics cos = new NpcCosmetics();
        if (encoded == null || encoded.isBlank()) return cos;
        int start = 0;
        int len = encoded.length();
        while (start < len) {
            int sep = encoded.indexOf(';', start);
            if (sep < 0) sep = len;
            int eq = encoded.indexOf('=', start);
            if (eq > start && eq < sep) {
                String slot = encoded.substring(start, eq).trim();
                String id = encoded.substring(eq + 1, sep).trim();
                if (!slot.isEmpty() && !id.isEmpty()) cos.set(slot, id);
            }
            start = sep + 1;
        }
        return cos;
    }

    public void writeYaml(ConfigurationSection section) {
        if (section == null) return;
        for (Map.Entry<String, String> e : bySlot.entrySet()) {
            section.set(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
        }
    }

    public static NpcCosmetics fromYaml(ConfigurationSection section) {
        NpcCosmetics cos = new NpcCosmetics();
        if (section == null) return cos;
        for (String key : section.getKeys(false)) {
            String id = section.getString(key);
            if (id != null && !id.isBlank()) cos.set(key, id);
        }
        return cos;
    }

    /** Merge equipment + cosmetics into SPAWN/EQUIP wire extra. */
    public static String mergeExtra(String equipmentEncoded, NpcCosmetics cosmetics) {
        String cos = cosmetics == null || cosmetics.isEmpty() ? null : cosmetics.encode();
        if (equipmentEncoded == null || equipmentEncoded.isBlank()) {
            return cos == null ? null : EXTRA_PREFIX + cos;
        }
        if (cos == null) return equipmentEncoded;
        return equipmentEncoded + EXTRA_SEP + cos;
    }

    public static String equipmentPart(String extra) {
        if (extra == null || extra.isBlank()) return null;
        if (extra.startsWith(EXTRA_PREFIX)) return null;
        int i = extra.indexOf(EXTRA_SEP);
        return i < 0 ? extra : extra.substring(0, i);
    }

    public static NpcCosmetics cosmeticsPart(String extra) {
        if (extra == null || extra.isBlank()) return new NpcCosmetics();
        if (extra.startsWith(EXTRA_PREFIX)) return parse(extra.substring(EXTRA_PREFIX.length()));
        int i = extra.indexOf(EXTRA_SEP);
        return i < 0 ? new NpcCosmetics() : parse(extra.substring(i + EXTRA_SEP.length()));
    }
}
