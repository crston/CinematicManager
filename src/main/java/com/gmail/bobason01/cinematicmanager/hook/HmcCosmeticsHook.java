package com.gmail.bobason01.cinematicmanager.hook;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.NpcCosmetics;
import com.gmail.bobason01.cinematicmanager.data.NpcEquipment;
import com.gmail.bobason01.cinematicmanager.util.PacketHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soft-depend bridge. HMC catalog only — no CosmeticUser ticks.
 * <p>
 * Backpack matches HMCCosmeticsRemapped: viewer-only packet ArmorStand + helmet, ridden on the NPC.
 * Armor slots → LibsDisguises + personal equipment packets.
 * <p>
 * Note: invisible ArmorStands ghost in {@link org.bukkit.GameMode#SPECTATOR} (vanilla). Cinematic
 * playback uses Adventure+flight so the stand body stays fully hidden.
 */
public final class HmcCosmeticsHook {

    private final CinematicManager plugin;
    private final boolean enabled;

    private Method apiGetCosmetic;
    private Method apiGetAllCosmetics;
    private Method apiGetAllSlots;
    private Method cosmeticGetId;
    private Method cosmeticGetItem;
    private Method cosmeticGetSlot;
    private Method slotNameMethod;

    private final ConcurrentHashMap<String, List<String>> idsBySlot = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ItemStack> itemCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> slotOf = new ConcurrentHashMap<>();
    private volatile List<String> slotIds = List.of();

    private final ConcurrentHashMap<UUID, List<BackpackRide>> backpacks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, java.util.EnumSet<NpcEquipment.Slot>> overlaySlots =
            new ConcurrentHashMap<>();

    private record BackpackRide(UUID viewerId, int vehicleEntityId, int standEntityId, ItemStack helmet) {}

    public HmcCosmeticsHook(CinematicManager plugin) {
        this.plugin = plugin;
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("HMCCosmetics") && resolveApi();
        if (enabled) {
            refreshCatalog();
            plugin.getLogger().info("HMCCosmetics hook enabled (" + slotIds.size() + " slots, HMC AS backpack).");
        }
    }

    public boolean isEnabled() { return enabled; }
    public List<String> slotIds() { return slotIds; }

    public List<String> cosmeticIdsForSlot(String slot) {
        if (!enabled || slot == null) return List.of();
        return idsBySlot.getOrDefault(slot.toUpperCase(Locale.ROOT), List.of());
    }

    public ItemStack iconOf(String cosmeticId) {
        if (!enabled || cosmeticId == null) return null;
        ItemStack cached = itemCache.get(cosmeticId);
        return cached == null ? null : cached.clone();
    }

    public String slotOfCosmetic(String cosmeticId) {
        return cosmeticId == null ? null : slotOf.get(cosmeticId);
    }

    public void refreshCatalog() {
        if (!enabled) return;
        idsBySlot.clear();
        itemCache.clear();
        slotOf.clear();
        try {
            @SuppressWarnings("unchecked")
            Map<String, ?> slots = (Map<String, ?>) apiGetAllSlots.invoke(null);
            List<String> names = new ArrayList<>(slots.size());
            for (String key : slots.keySet()) names.add(key.toUpperCase(Locale.ROOT));
            Collections.sort(names);
            slotIds = List.copyOf(names);

            @SuppressWarnings("unchecked")
            List<?> all = (List<?>) apiGetAllCosmetics.invoke(null);
            Map<String, List<String>> building = new LinkedHashMap<>();
            for (Object cosmetic : all) {
                String id = String.valueOf(cosmeticGetId.invoke(cosmetic));
                Object slotObj = cosmeticGetSlot.invoke(cosmetic);
                String slot = slotObj == null ? null : String.valueOf(slotNameMethod.invoke(slotObj)).toUpperCase(Locale.ROOT);
                if (slot == null || slot.isBlank()) continue;
                slotOf.put(id, slot);
                building.computeIfAbsent(slot, s -> new ArrayList<>()).add(id);
                ItemStack item = (ItemStack) cosmeticGetItem.invoke(cosmetic);
                if (item != null && !item.getType().isAir()) itemCache.put(id, item.clone());
            }
            for (Map.Entry<String, List<String>> e : building.entrySet()) {
                Collections.sort(e.getValue());
                idsBySlot.put(e.getKey(), List.copyOf(e.getValue()));
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("HMCCosmetics catalog refresh failed: " + t.getMessage());
        }
    }

    public void apply(Entity entity, Player viewer, NpcCosmetics cosmetics) {
        apply(entity, viewer, cosmetics, false);
    }

    public void apply(Entity entity, Player viewer, NpcCosmetics cosmetics, boolean gearAlreadyReplaced) {
        if (!enabled || entity == null || !entity.isValid()) return;
        clearBackpacks(entity.getUniqueId());

        if (cosmetics == null || cosmetics.isEmpty()) {
            if (gearAlreadyReplaced) overlaySlots.remove(entity.getUniqueId());
            else clearOverlayArmor(entity);
            return;
        }

        if (!gearAlreadyReplaced) clearOverlayArmor(entity);
        else overlaySlots.remove(entity.getUniqueId());

        NpcEquipment overlay = new NpcEquipment();
        java.util.EnumSet<NpcEquipment.Slot> painted = java.util.EnumSet.noneOf(NpcEquipment.Slot.class);

        for (Map.Entry<String, String> e : cosmetics.view().entrySet()) {
            String slot = e.getKey();
            ItemStack item = resolveItem(e.getValue());
            if (item == null) continue;

            EquipmentSlot eq = mapArmorSlot(slot);
            if (eq != null) {
                NpcEquipment.Slot ns = toNpcSlot(eq);
                overlay.setItem(ns, item);
                painted.add(ns);
                continue;
            }
            if (isBalloonLike(slot)) continue;
            // BACKPACK / WING / etc. — HMC ride stand (not disguise helmet, not free-float display)
            spawnHmcBackpack(entity, viewer, item);
        }

        if (!overlay.isEmpty()) {
            overlay.apply(entity, false);
            overlaySlots.put(entity.getUniqueId(), painted);
            if (viewer != null && viewer.isOnline()) {
                for (NpcEquipment.Slot s : painted) {
                    ItemStack stack = overlay.getItem(s);
                    if (stack == null) continue;
                    var peSlot = toPacketSlot(s);
                    if (peSlot != null) {
                        PacketHelper.setEquipment(viewer, entity.getEntityId(), peSlot, stack);
                    }
                }
            }
        }

        if (entity instanceof org.bukkit.entity.ArmorStand as && as.isValid()) {
            as.setInvisible(true);
            as.setMarker(true);
            as.setBasePlate(false);
            as.setArms(false);
        }
    }

    /**
     * HMC {@code backpack-force-riding-packet}: re-send mount so the stand stays on the NPC after moves.
     */
    public void syncBackpacks(Entity base) {
        if (!enabled || base == null || !base.isValid()) return;
        List<BackpackRide> list = backpacks.get(base.getUniqueId());
        if (list == null || list.isEmpty()) return;
        for (BackpackRide bp : list) {
            Player viewer = Bukkit.getPlayer(bp.viewerId());
            if (viewer == null || !viewer.isOnline()) continue;
            PacketHelper.setPassengers(viewer, base.getEntityId(), bp.standEntityId());
        }
    }

    private void clearOverlayArmor(Entity entity) {
        java.util.EnumSet<NpcEquipment.Slot> painted = overlaySlots.remove(entity.getUniqueId());
        if (painted == null || painted.isEmpty()) return;
        NpcEquipment.wipeSlots(entity, painted);
    }

    private ItemStack resolveItem(String id) {
        ItemStack cached = itemCache.get(id);
        if (cached != null) return cached.clone();
        try {
            Object cosmetic = apiGetCosmetic.invoke(null, id);
            if (cosmetic == null) return null;
            ItemStack item = (ItemStack) cosmeticGetItem.invoke(cosmetic);
            if (item == null || item.getType().isAir()) return null;
            itemCache.put(id, item.clone());
            return item.clone();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Mirrors {@code UserBackpackManager#spawn}: invisible AS at feet → helmet → ride NPC.
     */
    private void spawnHmcBackpack(Entity base, Player viewer, ItemStack item) {
        if (viewer == null || !viewer.isOnline() || item == null) return;
        final ItemStack helmet = item.clone();
        int standId = PacketHelper.spawnHmcBackpackStand(viewer, base.getLocation(), helmet);
        if (standId == 0) return;

        PacketHelper.setPassengers(viewer, base.getEntityId(), standId);
        backpacks.computeIfAbsent(base.getUniqueId(), u -> new ArrayList<>(1))
                .add(new BackpackRide(viewer.getUniqueId(), base.getEntityId(), standId, helmet));

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline() || !base.isValid()) {
                PacketHelper.destroyEntity(viewer, standId);
                return;
            }
            PacketHelper.sendHmcBackpackMeta(viewer, standId);
            PacketHelper.setEquipment(viewer, standId,
                    com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET, helmet);
            PacketHelper.sendHmcBackpackMeta(viewer, standId);
            PacketHelper.setPassengers(viewer, base.getEntityId(), standId);
        });
    }

    private static com.github.retrooper.packetevents.protocol.player.EquipmentSlot toPacketSlot(
            NpcEquipment.Slot slot) {
        return switch (slot) {
            case HEAD -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET;
            case CHEST -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.CHEST_PLATE;
            case LEGS -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.LEGGINGS;
            case FEET -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BOOTS;
            case HAND -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.MAIN_HAND;
            case OFF -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.OFF_HAND;
        };
    }

    public void clearPassengers(UUID baseId) { clearBackpacks(baseId); }
    public void clearPacketBackpacks(UUID baseId) { clearBackpacks(baseId); }

    public void clearBackpacks(UUID baseId) {
        List<BackpackRide> list = backpacks.remove(baseId);
        if (list == null || list.isEmpty()) return;
        for (BackpackRide bp : list) {
            Player viewer = Bukkit.getPlayer(bp.viewerId());
            if (viewer == null || !viewer.isOnline()) continue;
            PacketHelper.setPassengers(viewer, bp.vehicleEntityId(), new int[0]);
            PacketHelper.destroyEntity(viewer, bp.standEntityId());
        }
    }

    public void clearAll(UUID baseId) {
        clearBackpacks(baseId);
        overlaySlots.remove(baseId);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(backpacks.keySet())) clearBackpacks(id);
        overlaySlots.clear();
    }

    private static boolean isBalloonLike(String slot) {
        String s = slot.toUpperCase(Locale.ROOT);
        return s.contains("BALLOON") || s.contains("LEASH");
    }

    private static EquipmentSlot mapArmorSlot(String slot) {
        return switch (slot.toUpperCase(Locale.ROOT)) {
            case "HELMET", "HAT", "HEAD" -> EquipmentSlot.HEAD;
            case "CHESTPLATE", "CHEST", "BODY" -> EquipmentSlot.CHEST;
            case "LEGGINGS", "LEGS", "PANTS" -> EquipmentSlot.LEGS;
            case "BOOTS", "FEET", "SHOES" -> EquipmentSlot.FEET;
            case "OFFHAND", "OFF_HAND", "SHIELD" -> EquipmentSlot.OFF_HAND;
            case "MAINHAND", "MAIN_HAND", "HAND", "WEAPON" -> EquipmentSlot.HAND;
            default -> null;
        };
    }

    private static NpcEquipment.Slot toNpcSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> NpcEquipment.Slot.HEAD;
            case CHEST -> NpcEquipment.Slot.CHEST;
            case LEGS -> NpcEquipment.Slot.LEGS;
            case FEET -> NpcEquipment.Slot.FEET;
            case OFF_HAND -> NpcEquipment.Slot.OFF;
            default -> NpcEquipment.Slot.HAND;
        };
    }

    private boolean resolveApi() {
        try {
            Class<?> api = Class.forName("com.hibiscusmc.hmccosmetics.api.HMCCosmeticsAPI");
            Class<?> cosmetic = Class.forName("com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic");
            apiGetCosmetic = api.getMethod("getCosmetic", String.class);
            apiGetAllCosmetics = api.getMethod("getAllCosmetics");
            apiGetAllSlots = api.getMethod("getAllCosmeticSlots");
            cosmeticGetId = cosmetic.getMethod("getId");
            cosmeticGetItem = cosmetic.getMethod("getItem");
            cosmeticGetSlot = cosmetic.getMethod("getSlot");
            Class<?> slotCl = cosmeticGetSlot.getReturnType();
            try {
                slotNameMethod = slotCl.getMethod("name");
            } catch (NoSuchMethodException e) {
                slotNameMethod = slotCl.getMethod("toString");
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("HMCCosmetics present but API resolve failed: " + t.getMessage());
            return false;
        }
    }
}
