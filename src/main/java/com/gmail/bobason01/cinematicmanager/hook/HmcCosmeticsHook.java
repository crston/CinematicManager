package com.gmail.bobason01.cinematicmanager.hook;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.NpcCosmetics;
import com.gmail.bobason01.cinematicmanager.data.NpcEquipment;
import com.gmail.bobason01.cinematicmanager.util.PacketHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
    private final ConcurrentHashMap<UUID, List<ItemStack>> suspendedBackpacks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> suppressGen = new ConcurrentHashMap<>();

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
        boolean animPlaying = animationPlaying(entity);
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
            if (animPlaying) {
                stashSuspended(entity.getUniqueId(), item);
            } else {
                spawnHmcBackpack(entity, viewer, item);
            }
        }

        if (!overlay.isEmpty()) {
            overlay.apply(entity, false);
            overlaySlots.put(entity.getUniqueId(), painted);
            if (!animPlaying && viewer != null && viewer.isOnline()) {
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
     * HMC {@code backpack-force-riding-packet}: remount + match host yaw so wings/bags turn with the NPC.
     * Never teleport the packet stand to the vehicle feet — the client interpolates that as a rise from below.
     */
    public void syncBackpacks(Entity base) {
        if (!enabled || base == null || !base.isValid()) return;
        if (animationPlaying(base)) return;
        List<BackpackRide> list = backpacks.get(base.getUniqueId());
        if (list == null || list.isEmpty()) return;
        float yaw = base.getLocation().getYaw();
        for (BackpackRide bp : list) {
            Player viewer = Bukkit.getPlayer(bp.viewerId());
            if (viewer == null || !viewer.isOnline()) continue;
            PacketHelper.lookFakeEntity(viewer, bp.standEntityId(), yaw);
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
     * Invisible AS → ride NPC → helmet only after passenger interpolation (3 ticks).
     * Applying the helmet at feet, or teleporting a rider to feet, makes the bag lerp up from the ground.
     */
    private void spawnHmcBackpack(Entity base, Player viewer, ItemStack item) {
        if (viewer == null || !viewer.isOnline() || item == null) return;
        final ItemStack helmet = item.clone();
        UUID npcId = base.getUniqueId();
        if (isSuppressed(npcId)) {
            stashSuspended(npcId, helmet);
            return;
        }
        Location feet = base.getLocation();
        int standId = PacketHelper.spawnHmcBackpackStand(viewer, feet);
        if (standId == 0) return;

        PacketHelper.setPassengers(viewer, base.getEntityId(), standId);

        backpacks.computeIfAbsent(npcId, u -> new ArrayList<>(1))
                .add(new BackpackRide(viewer.getUniqueId(), base.getEntityId(), standId, helmet));

        final int gen = suppressGen.getOrDefault(npcId, 0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!viewer.isOnline() || isSuppressed(npcId) || gen != suppressGen.getOrDefault(npcId, 0)) {
                dumpAndDestroy(viewer, base.getEntityId(), standId);
                return;
            }
            List<BackpackRide> list = backpacks.get(npcId);
            boolean stillMounted = false;
            if (list != null) {
                for (BackpackRide bp : list) {
                    if (bp.standEntityId() == standId) {
                        stillMounted = true;
                        break;
                    }
                }
            }
            if (!stillMounted || !base.isValid()) {
                dumpAndDestroy(viewer, base.getEntityId(), standId);
                return;
            }
            PacketHelper.setPassengers(viewer, base.getEntityId(), standId);
            attachBackpackVisual(viewer, base, standId, helmet);
        }, 3L);
    }

    private static void attachBackpackVisual(Player viewer, Entity base, int standId, ItemStack helmet) {
        PacketHelper.sendHmcBackpackMeta(viewer, standId);
        PacketHelper.setEquipment(viewer, standId,
                com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET, helmet);
        PacketHelper.sendHmcBackpackMeta(viewer, standId);
        PacketHelper.setPassengers(viewer, base.getEntityId(), standId);
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
            dumpAndDestroy(viewer, bp.vehicleEntityId(), bp.standEntityId());
        }
    }

    private static void dumpAndDestroy(Player viewer, int vehicleId, int standId) {
        PacketHelper.clearEquipment(viewer, standId,
                com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET);
        PacketHelper.setPassengers(viewer, vehicleId, new int[0]);
        Location dump = viewer.getLocation();
        if (dump != null) {
            dump = dump.clone();
            dump.setY(-128);
            PacketHelper.teleportFakeEntitySnapped(viewer, standId, dump);
        }
        PacketHelper.destroyEntity(viewer, standId);
    }

    public void clearAll(UUID baseId) {
        clearBackpacks(baseId);
        overlaySlots.remove(baseId);
        suspendedBackpacks.remove(baseId);
    }

    /**
     * Hide HMC backpack/wing packet stands for LuxGestures playback; {@link #resumeAfterGesture} restores them.
     */
    private boolean animationPlaying(Entity entity) {
        var am = plugin.getAnimationManagerHook();
        if (am != null && am.isEnabled() && am.isPlaying(entity)) return true;
        var lux = plugin.getLuxGesturesHook();
        return lux != null && lux.isEnabled() && lux.isPlaying(entity);
    }

    private void stashSuspended(UUID id, ItemStack item) {
        if (item == null) return;
        suspendedBackpacks.compute(id, (k, cur) -> {
            List<ItemStack> next = cur == null ? new ArrayList<>(1) : cur;
            next.add(item.clone());
            return next;
        });
    }

    public void suspendForGesture(Entity base) {
        suspendForGesture(base, null);
    }

    public void suspendForGesture(Entity base, Player viewer) {
        if (!enabled || base == null) return;
        UUID id = base.getUniqueId();
        suppressGen.merge(id, 1, Integer::sum);
        List<BackpackRide> list = backpacks.get(id);
        if (list != null && !list.isEmpty()) {
            List<ItemStack> saved = new ArrayList<>(list.size());
            for (BackpackRide bp : list) {
                if (bp.helmet() != null) saved.add(bp.helmet().clone());
            }
            if (!saved.isEmpty()) {
                suspendedBackpacks.merge(id, saved, (a, b) -> {
                    List<ItemStack> next = new ArrayList<>(a.size() + b.size());
                    next.addAll(a);
                    next.addAll(b);
                    return next;
                });
            }
        }
        List<BackpackRide> rides = backpacks.remove(id);
        destroyRides(rides, viewer);
        clearOverlayArmor(base);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isSuppressed(id)) {
                destroyRides(rides, viewer);
            }
        });
    }

    private void destroyRides(List<BackpackRide> rides, Player viewer) {
        if (rides == null || rides.isEmpty()) return;
        for (BackpackRide bp : rides) {
            Player v = viewer != null && viewer.isOnline() ? viewer : Bukkit.getPlayer(bp.viewerId());
            if (v == null || !v.isOnline()) continue;
            dumpAndDestroy(v, bp.vehicleEntityId(), bp.standEntityId());
        }
    }

    public void resumeAfterGesture(Entity base, Player viewer) {
        if (!enabled || base == null || !base.isValid()) return;
        UUID id = base.getUniqueId();
        suppressGen.remove(id);
        List<ItemStack> saved = suspendedBackpacks.remove(id);
        if (saved == null || saved.isEmpty() || viewer == null || !viewer.isOnline()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!base.isValid() || !viewer.isOnline() || isSuppressed(id)) return;
            for (ItemStack helmet : saved) {
                spawnHmcBackpack(base, viewer, helmet);
            }
        }, 1L);
    }

    private boolean isSuppressed(UUID id) {
        return id != null && suppressGen.containsKey(id);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(backpacks.keySet())) clearBackpacks(id);
        overlaySlots.clear();
        suspendedBackpacks.clear();
        suppressGen.clear();
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
