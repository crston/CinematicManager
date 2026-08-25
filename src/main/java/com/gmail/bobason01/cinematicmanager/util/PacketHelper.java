package com.gmail.bobason01.cinematicmanager.util;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.EulerAngle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Viewer-scoped PacketEvents helpers. Never broadcasts — always personal packets.
 */
public final class PacketHelper {
    private static final AtomicInteger FAKE_ENTITY_IDS = new AtomicInteger(1_900_000_000);

    /** HMC {@code getMask}: invisible + fire (backpack-prevent-darkness default). */
    private static final byte HMC_BACKPACK_FLAGS = 0x21;
    /** HMC armor-stand client flags: marker only. */
    private static final byte HMC_STAND_CLIENT_FLAGS = 0x10;
    /** Invisible + silent + no-gravity armor stand with arms, no baseplate (not marker). */
    private static final byte LIMB_ENTITY_FLAGS = 0x20;
    private static final byte LIMB_STAND_FLAGS = 0x04 | 0x08;
    private static final int RIGHT_ARM_POSE = 19;
    private static final ItemStack AIR = new ItemStack(Material.AIR);
    private static final Vector3d ZERO_DELTA = new Vector3d(0, 0, 0);

    private PacketHelper() {}

    public static void send(Player viewer, PacketWrapper<?> packet) {
        if (viewer == null || packet == null) return;
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    /** Spigot-safe action bar (Adventure Component + custom fonts). */
    public static void sendActionBar(Player viewer, Component component) {
        if (viewer == null) return;
        send(viewer, new WrapperPlayServerActionBar(component == null ? Component.empty() : component));
    }

    public static void sendLightning(Player viewer, Location loc) {
        int entityId = ThreadLocalRandom.current().nextInt(100000, 200000);
        WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.LIGHTNING_BOLT,
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                0f,
                0f,
                0f,
                0,
                Optional.empty()
        );
        send(viewer, packet);
    }

    public static void sendEntityAnimation(Player viewer, Entity entity, int animationId) {
        WrapperPlayServerEntityAnimation.EntityAnimationType type =
                WrapperPlayServerEntityAnimation.EntityAnimationType.getById(animationId);
        if (type == null) {
            type = WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM;
        }
        send(viewer, new WrapperPlayServerEntityAnimation(entity.getEntityId(), type));
    }

    public static int spawnFakeEntity(Player viewer, Location loc, String bukkitTypeName) {
        if (viewer == null || loc == null) return 0;
        EntityType type = resolveEntityType(bukkitTypeName);
        if (type == null) type = EntityTypes.ARMOR_STAND;
        int entityId = FAKE_ENTITY_IDS.getAndIncrement();
        WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(UUID.randomUUID()),
                type,
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                loc.getPitch(),
                loc.getYaw(),
                loc.getYaw(),
                0,
                Optional.empty()
        );
        send(viewer, packet);
        return entityId;
    }

    /**
     * HMC backpack host: invisible packet ArmorStand at the NPC, no helmet.
     * Helmet must wait until the client has finished passenger interpolation.
     */
    public static int spawnHmcBackpackStand(Player viewer, Location entityFeet) {
        if (viewer == null || entityFeet == null) return 0;
        int entityId = FAKE_ENTITY_IDS.getAndIncrement();
        try {
            send(viewer, new WrapperPlayServerSpawnEntity(
                    entityId,
                    Optional.of(UUID.randomUUID()),
                    EntityTypes.ARMOR_STAND,
                    new Vector3d(entityFeet.getX(), entityFeet.getY(), entityFeet.getZ()),
                    entityFeet.getPitch(),
                    entityFeet.getYaw(),
                    entityFeet.getYaw(),
                    0,
                    Optional.empty()
            ));
            sendHmcBackpackMeta(viewer, entityId);
            return entityId;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Yaw only — never teleport a riding backpack or it lerps up from the vehicle feet. */
    public static void lookFakeEntity(Player viewer, int entityId, float yaw) {
        if (viewer == null || entityId == 0) return;
        try {
            send(viewer, new WrapperPlayServerEntityHeadLook(entityId, yaw));
        } catch (Throwable ignored) {
        }
    }

    public static void sendHmcBackpackMeta(Player viewer, int entityId) {
        if (viewer == null || entityId == 0) return;
        try {
            List<EntityData<?>> meta = new ArrayList<>(4);
            meta.add(new EntityData<>(0, EntityDataTypes.BYTE, HMC_BACKPACK_FLAGS));
            meta.add(new EntityData<>(4, EntityDataTypes.BOOLEAN, true));
            meta.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true));
            meta.add(new EntityData<>(15, EntityDataTypes.BYTE, HMC_STAND_CLIENT_FLAGS));
            send(viewer, new WrapperPlayServerEntityMetadata(entityId, meta));
        } catch (Throwable ignored) {
        }
    }

    public static void forceInvisibleArmorStand(Player viewer, int entityId) {
        if (viewer == null || entityId == 0) return;
        try {
            List<EntityData<?>> meta = new ArrayList<>(4);
            meta.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20));
            meta.add(new EntityData<>(4, EntityDataTypes.BOOLEAN, true));
            meta.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true));
            meta.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) (0x08 | 0x10)));
            send(viewer, new WrapperPlayServerEntityMetadata(entityId, meta));
        } catch (Throwable ignored) {
        }
    }

    public static void setEquipment(Player viewer, int entityId, EquipmentSlot slot, ItemStack bukkitItem) {
        if (viewer == null || entityId == 0 || slot == null) return;
        try {
            ItemStack stack = bukkitItem == null ? AIR : bukkitItem;
            var peItem = SpigotConversionUtil.fromBukkitItemStack(stack);
            send(viewer, new WrapperPlayServerEntityEquipment(
                    entityId,
                    List.of(new Equipment(slot, peItem))
            ));
        } catch (Throwable ignored) {
        }
    }

    public static void clearEquipment(Player viewer, int entityId, EquipmentSlot slot) {
        setEquipment(viewer, entityId, slot, AIR);
    }

    public static void setPassengers(Player viewer, int vehicleEntityId, int... passengerIds) {
        if (viewer == null || vehicleEntityId == 0) return;
        try {
            send(viewer, new WrapperPlayServerSetPassengers(
                    vehicleEntityId,
                    passengerIds == null ? new int[0] : passengerIds
            ));
        } catch (Throwable ignored) {
        }
    }

    public static void teleportFakeEntity(Player viewer, int entityId, Location loc) {
        if (viewer == null || loc == null || entityId == 0) return;
        try {
            send(viewer, new WrapperPlayServerEntityTeleport(
                    entityId,
                    new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                    loc.getYaw(),
                    loc.getPitch(),
                    false
            ));
            send(viewer, new WrapperPlayServerEntityHeadLook(entityId, loc.getYaw()));
        } catch (Throwable ignored) {
        }
    }

    /** 1.21.2+ position-sync so packet limbs do not interpolate between poses. */
    public static void teleportFakeEntitySnapped(Player viewer, int entityId, Location loc) {
        if (viewer == null || loc == null || entityId == 0) return;
        try {
            send(viewer, new WrapperPlayServerEntityPositionSync(
                    entityId,
                    new EntityPositionData(
                            new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                            ZERO_DELTA,
                            loc.getYaw(),
                            loc.getPitch()
                    ),
                    false
            ));
            send(viewer, new WrapperPlayServerEntityHeadLook(entityId, loc.getYaw()));
        } catch (Throwable t) {
            teleportFakeEntity(viewer, entityId, loc);
        }
    }

    public static void destroyEntity(Player viewer, int entityId) {
        if (viewer == null || entityId == 0) return;
        try {
            send(viewer, new WrapperPlayServerDestroyEntities(entityId));
        } catch (Throwable ignored) {
        }
    }

    public static void destroyEntities(Player viewer, int[] entityIds) {
        if (viewer == null || entityIds == null || entityIds.length == 0) return;
        int live = 0;
        for (int id : entityIds) if (id != 0) live++;
        if (live == 0) return;
        try {
            if (live == entityIds.length) {
                send(viewer, new WrapperPlayServerDestroyEntities(entityIds));
                return;
            }
            int[] packed = new int[live];
            int n = 0;
            for (int id : entityIds) if (id != 0) packed[n++] = id;
            send(viewer, new WrapperPlayServerDestroyEntities(packed));
        } catch (Throwable ignored) {
        }
    }

    /** Small enough that even a client-side lerp over this distance is imperceptible
     *  in a single tick, unlike the earlier 256-block staging spot that visibly
     *  climbed into place over multiple frames. */
    private static final double LIMB_STAGING_Y_OFFSET = 4.0;

    /**
     * Viewer-only PixelSkin limb: invisible armor stand, item in main hand, right-arm pose.
     * Never broadcast — other players never receive these packets.
     * <p>
     * The vanilla Spawn Entity packet cannot carry metadata (invisible flag, limb
     * rotation) - that only ever arrives in a follow-up Entity Metadata packet, so the
     * entity necessarily exists for a moment in a visible, unposed state between the
     * two. Staging the spawn 256 blocks below and position-syncing up used to "fix"
     * this, but that distance was far enough that the client visibly interpolated the
     * climb instead of snapping - limbs appeared to rise out of the ground and
     * assemble over several frames, which was worse than the flash it replaced. This
     * uses a much smaller staging offset instead: still spawned somewhere other than
     * the final pose (so a stray visible-default frame isn't sitting right on top of
     * the model), but short enough that even an unwanted lerp resolves within a single
     * tick and is not noticeable.
     */
    private static volatile Method armorStandGetHandle;
    private static volatile Method armorStandMoveTo;
    private static volatile Method armorStandSetOld;
    private static volatile boolean armorStandReflectionResolved;

    /**
     * Same NMS moveTo/setOldPosAndRot reflection AnimationManager's own BoneDisplay
     * uses to snap an ArmorStand with no client-side interpolation. Falls back to a
     * plain Bukkit teleport if the reflective lookup ever fails (version drift).
     */
    private static void resolveArmorStandReflection(ArmorStand stand) {
        if (armorStandReflectionResolved) return;
        synchronized (PacketHelper.class) {
            if (armorStandReflectionResolved) return;
            try {
                Method handle = stand.getClass().getMethod("getHandle");
                Object nms = handle.invoke(stand);
                Method move = findMoveMethod(nms.getClass());
                Method old = findNoArgMethod(nms.getClass(), "setOldPosAndRot", "setOldPosRot", "applyAndSetOldPosAndRot");
                if (move != null) {
                    armorStandGetHandle = handle;
                    armorStandMoveTo = move;
                    armorStandSetOld = old;
                }
            } catch (Throwable ignored) {
            }
            armorStandReflectionResolved = true;
        }
    }

    private static Method findMoveMethod(Class<?> type) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            for (String name : new String[]{"moveTo", "absMoveTo", "snapTo"}) {
                try {
                    return cursor.getMethod(name, double.class, double.class, double.class, float.class, float.class);
                } catch (NoSuchMethodException ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> type, String... names) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            for (String name : names) {
                try {
                    return cursor.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    // Diagnostics only - see describeArmorStandSnapMode(). Not used for any control flow.
    private static volatile boolean armorStandFastPathEverSucceeded;
    private static volatile boolean armorStandFastPathEverFailed;

    /**
     * Which position-sync path snapArmorStand() has actually taken so far this run:
     * "nms-reflect" (no client interpolation - the good path, same one BoneDisplay
     * uses), "teleport-fallback" (plain Bukkit teleport, which the client CAN
     * interpolate/lerp over a few frames - a likely source of visible "growing
     * together" pops if this shows up), or "unresolved" (snapArmorStand hasn't run
     * yet). Logged once from AnimationManagerHook so a report from the user's console
     * tells us definitively which path is live, instead of guessing from video alone.
     */
    public static String describeArmorStandSnapMode() {
        if (!armorStandReflectionResolved) return "unresolved";
        if (armorStandFastPathEverSucceeded && !armorStandFastPathEverFailed) return "nms-reflect";
        if (armorStandFastPathEverSucceeded) return "nms-reflect (intermittent fallback seen)";
        if (armorStandGetHandle == null || armorStandMoveTo == null) return "teleport-fallback (methods not found)";
        return "teleport-fallback (invoke failed)";
    }

    /** Teleport a real limb ArmorStand with no client interpolation (mirrors BoneDisplay.snap). */
    public static void snapArmorStand(ArmorStand stand, double x, double y, double z, float yaw) {
        if (stand == null || !stand.isValid()) return;
        resolveArmorStandReflection(stand);
        Method handle = armorStandGetHandle;
        Method move = armorStandMoveTo;
        if (handle != null && move != null) {
            try {
                Object nms = handle.invoke(stand);
                move.invoke(nms, x, y, z, yaw, 0f);
                if (armorStandSetOld != null) {
                    armorStandSetOld.invoke(nms);
                }
                stand.setRotation(yaw, 0f);
                armorStandFastPathEverSucceeded = true;
                return;
            } catch (Throwable ignored) {
                armorStandFastPathEverFailed = true;
            }
        }
        Location dest = stand.getLocation();
        dest.setX(x);
        dest.setY(y);
        dest.setZ(z);
        dest.setYaw(yaw);
        dest.setPitch(0f);
        stand.teleport(dest);
        stand.setRotation(yaw, 0f);
    }

    /**
     * Reposition a real limb ArmorStand for one viewer with a protocol-level,
     * guaranteed no-interpolation position sync - used instead of relying on
     * snapArmorStand()'s NMS moveTo/setOldPosAndRot reflection, which targets a
     * pre-1.20.2 "movement rework" mechanism that may no longer reliably suppress
     * client-side interpolation on newer server builds (that reflective path either
     * silently fails to resolve, or resolves but no longer has the intended
     * no-interpolation effect against a modern client - either way the visible
     * symptom is the same: limbs visibly drifting/lerping toward their new pose for
     * a few frames instead of snapping, looking like the NPC's body is
     * assembling itself out of scattered pieces).
     * <p>
     * {@code stand.teleport(loc)} still runs first so the entity's own server-side
     * bookkeeping (hitbox/location queries) stays correct - Bukkit's own broadcast
     * from that call may itself be interpolated by the client, but it is
     * immediately followed, in the same tick's packet flush, by an explicit
     * {@code WrapperPlayServerEntityPositionSync} (the same 1.21.2+ packet the old
     * packet-only limb renderer used - see teleportFakeEntitySnapped) aimed at this
     * real entity's own network id. The client processes both packets together and
     * only ever renders the final, corrected position - this packet is the
     * documented, version-correct "this IS where the entity is, do not
     * interpolate" signal, so it does not depend on any reflective NMS method
     * lookup succeeding.
     */
    public static void repositionLimbStand(Player viewer, ArmorStand stand, Location loc) {
        if (viewer == null || stand == null || !stand.isValid() || loc == null) return;
        try {
            stand.teleport(loc);
        } catch (Throwable ignored) {
        }
        teleportFakeEntitySnapped(viewer, stand.getEntityId(), loc);
    }

    /**
     * Best-effort armor-stand equipment lock so the viewer can't right-click a limb
     * stand and pull/swap its held item. {@code ArmorStand#setDisabledSlots} /
     * {@code #addEquipmentLock} are Paper-only additions - this project compiles
     * against plain spigot-api, which doesn't declare them, so they are called purely
     * via reflection here (no compile-time reference to the classes/methods at all)
     * and silently no-op on a server that doesn't have them.
     * <p>
     * The reflective lookups (Class.forName / getMethod / Enum.valueOf) are resolved
     * once and cached in static fields, instead of being redone on every single
     * ArmorStand spawn - this can be called up to ~18 times per animation trigger
     * (limb stands + gear stands), so re-resolving on every call would be pure waste.
     */
    private static volatile boolean disabledSlotsAttempted;
    private static Object disabledSlotsValues;
    private static Method disabledSlotsMethod;

    private static volatile boolean equipmentLockAttempted;
    private static Object[] equipmentLockSlots;
    private static Object equipmentLockAdding;
    private static Object equipmentLockRemoving;
    private static Method equipmentLockMethod;

    private static void resolveDisabledSlotsReflection() {
        if (disabledSlotsAttempted) return;
        disabledSlotsAttempted = true;
        try {
            Class<?> slotClass = Class.forName("org.bukkit.inventory.EquipmentSlot");
            Object slots = slotClass.getMethod("values").invoke(null);
            disabledSlotsMethod = ArmorStand.class.getMethod("setDisabledSlots", slots.getClass());
            disabledSlotsValues = slots;
        } catch (Throwable ignored) {
        }
    }

    private static void resolveEquipmentLockReflection() {
        if (equipmentLockAttempted) return;
        equipmentLockAttempted = true;
        try {
            Class<?> slotClass = Class.forName("org.bukkit.inventory.EquipmentSlot");
            Class<?> lockTypeClass = Class.forName("org.bukkit.entity.ArmorStand$LockType");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object adding = Enum.valueOf((Class<Enum>) (Class) lockTypeClass, "ADDING_OR_CHANGING");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object removing = Enum.valueOf((Class<Enum>) (Class) lockTypeClass, "REMOVING_OR_CHANGING");
            equipmentLockMethod = ArmorStand.class.getMethod("addEquipmentLock", slotClass, lockTypeClass);
            equipmentLockSlots = (Object[]) slotClass.getMethod("values").invoke(null);
            equipmentLockAdding = adding;
            equipmentLockRemoving = removing;
        } catch (Throwable ignored) {
        }
    }

    private static void lockEquipmentSlots(ArmorStand stand) {
        resolveDisabledSlotsReflection();
        if (disabledSlotsMethod != null) {
            try {
                disabledSlotsMethod.invoke(stand, disabledSlotsValues);
            } catch (Throwable ignored) {
            }
        }
        resolveEquipmentLockReflection();
        if (equipmentLockMethod != null) {
            try {
                for (Object slot : equipmentLockSlots) {
                    equipmentLockMethod.invoke(stand, slot, equipmentLockAdding);
                    equipmentLockMethod.invoke(stand, slot, equipmentLockRemoving);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Real, Bukkit-native limb ArmorStand instead of a manual two-packet spawn. All
     * invisibility/pose/equipment is set INSIDE the spawn consumer, exactly like
     * AnimationManager's own BoneDisplay renderer - Bukkit bundles that state with the
     * entity before it is ever added to the world or broadcast to anyone, so there is
     * no window where a client can see a default-visible, unposed stand. This is what
     * finally closes the gap the packet-only spawnLimbStand() could never fully close:
     * the vanilla Spawn Entity packet cannot carry metadata, so any spawn-then-packet
     * approach necessarily has at least one frame where the entity exists unposed
     * before its Entity Metadata packet lands, no matter how small the staging offset.
     * <p>
     * "Viewer-only" is preserved by immediately hiding the freshly spawned stand from
     * every other online player in the same synchronous call, before the server's
     * entity tracker gets a chance to broadcast its spawn - the same
     * hide-from-everyone-but-viewer technique this class's caller already uses for the
     * am_limb/am_name stands in the playViaApi fallback path.
     */
    public static ArmorStand spawnLimbStandReal(Plugin plugin, Player viewer, Location loc, ItemStack hand, float rx, float ry, float rz) {
        if (plugin == null || viewer == null || loc == null || loc.getWorld() == null) return null;
        try {
            ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, (ArmorStand spawned) -> {
                spawned.setPersistent(false);
                spawned.setInvisible(true);
                spawned.setCustomNameVisible(false);
                spawned.setCustomName(null);
                spawned.setMarker(false);
                spawned.setGravity(false);
                spawned.setSilent(true);
                spawned.setBasePlate(false);
                spawned.setArms(true);
                spawned.setSmall(false);
                spawned.setInvulnerable(true);
                spawned.setCollidable(false);
                spawned.setCanPickupItems(false);
                spawned.setRemoveWhenFarAway(false);
                spawned.addScoreboardTag("am_limb");
                lockEquipmentSlots(spawned);
                if (hand != null && !hand.getType().isAir()) {
                    spawned.getEquipment().setItemInMainHand(hand);
                }
                spawned.setRightArmPose(new EulerAngle(rx, ry, rz));
                // No extra position-snap needed here: world.spawn(loc, ...) already
                // places the entity at loc/yaw before this consumer ever runs, and a
                // brand new spawn has no "previous" position for a client to
                // interpolate from - the interpolation risk only applies to
                // REPOSITIONING an already-visible stand later (see
                // repositionLimbStand(), used by every subsequent tick).
            });
            if (stand != null) {
                UUID viewerId = viewer.getUniqueId();
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.getUniqueId().equals(viewerId)) {
                        other.hideEntity(plugin, stand);
                    }
                }
            }
            return stand;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Same atomic real-ArmorStand technique as spawnLimbStandReal(), but for a
     * SEPARATE stand that wears one piece of the NPC's actually-equipped gear
     * (helmet, chestplate, leggings, boots, held weapon/offhand item) in the
     * matching Bukkit equipment slot, instead of holding a PixelSkin block in the
     * main hand. This is CinematicManager's equivalent of AnimationManager's own
     * GearDisplay - see AnimationManagerHook's GEAR_ATTACH table. The NPC's real
     * equipment is never modified, only cloned onto this stand, so no attribute
     * modifier baked into the gear (e.g. MMOItems stats) is ever recalculated.
     *
     * @param poseKind 0 = head pose, 1 = body pose, 2 = both leg poses, 3 = right arm pose
     */
    public static ArmorStand spawnGearStandReal(Plugin plugin, Player viewer, Location loc,
                                                 org.bukkit.inventory.EquipmentSlot wearSlot, ItemStack item,
                                                 int poseKind, float rx, float ry, float rz) {
        if (plugin == null || viewer == null || loc == null || loc.getWorld() == null
                || wearSlot == null || item == null || item.getType().isAir()) {
            return null;
        }
        try {
            ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, (ArmorStand spawned) -> {
                spawned.setPersistent(false);
                spawned.setInvisible(true);
                spawned.setCustomNameVisible(false);
                spawned.setCustomName(null);
                spawned.setMarker(false);
                spawned.setGravity(false);
                spawned.setSilent(true);
                spawned.setBasePlate(false);
                spawned.setArms(true);
                spawned.setSmall(false);
                spawned.setInvulnerable(true);
                spawned.setCollidable(false);
                spawned.setCanPickupItems(false);
                spawned.setRemoveWhenFarAway(false);
                spawned.addScoreboardTag("am_gear");
                lockEquipmentSlots(spawned);
                spawned.getEquipment().setItem(wearSlot, item);
                EulerAngle angle = new EulerAngle(rx, ry, rz);
                switch (poseKind) {
                    case 0 -> spawned.setHeadPose(angle);
                    case 1 -> spawned.setBodyPose(angle);
                    case 2 -> {
                        spawned.setLeftLegPose(angle);
                        spawned.setRightLegPose(angle);
                    }
                    default -> spawned.setRightArmPose(angle);
                }
            });
            if (stand != null) {
                UUID viewerId = viewer.getUniqueId();
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.getUniqueId().equals(viewerId)) {
                        other.hideEntity(plugin, stand);
                    }
                }
            }
            return stand;
        } catch (Throwable t) {
            return null;
        }
    }

    public static int spawnLimbStand(Player viewer, Location loc, ItemStack hand, float rx, float ry, float rz) {
        if (viewer == null || loc == null) return 0;
        int entityId = FAKE_ENTITY_IDS.getAndIncrement();
        try {
            Location staging = loc.clone();
            staging.setY(staging.getY() - LIMB_STAGING_Y_OFFSET);
            send(viewer, new WrapperPlayServerSpawnEntity(
                    entityId,
                    Optional.of(UUID.randomUUID()),
                    EntityTypes.ARMOR_STAND,
                    new Vector3d(staging.getX(), staging.getY(), staging.getZ()),
                    0f,
                    loc.getYaw(),
                    loc.getYaw(),
                    0,
                    Optional.empty()
            ));
            sendLimbStandMeta(viewer, entityId, rx, ry, rz);
            if (hand != null && !hand.getType().isAir()) {
                setEquipment(viewer, entityId, EquipmentSlot.MAIN_HAND, hand);
            }
            teleportFakeEntitySnapped(viewer, entityId, loc);
            return entityId;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void sendLimbStandMeta(Player viewer, int entityId, float rx, float ry, float rz) {
        if (viewer == null || entityId == 0) return;
        try {
            List<EntityData<?>> meta = new ArrayList<>(5);
            meta.add(new EntityData<>(0, EntityDataTypes.BYTE, LIMB_ENTITY_FLAGS));
            meta.add(new EntityData<>(4, EntityDataTypes.BOOLEAN, true));
            meta.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true));
            meta.add(new EntityData<>(15, EntityDataTypes.BYTE, LIMB_STAND_FLAGS));
            meta.add(new EntityData<>(RIGHT_ARM_POSE, EntityDataTypes.ROTATION, toDegreesVector(rx, ry, rz)));
            send(viewer, new WrapperPlayServerEntityMetadata(entityId, meta));
        } catch (Throwable ignored) {
        }
    }

    public static void setLimbArmPose(Player viewer, int entityId, float rx, float ry, float rz) {
        if (viewer == null || entityId == 0) return;
        try {
            List<EntityData<?>> meta = List.of(
                    new EntityData<>(RIGHT_ARM_POSE, EntityDataTypes.ROTATION, toDegreesVector(rx, ry, rz))
            );
            send(viewer, new WrapperPlayServerEntityMetadata(entityId, meta));
        } catch (Throwable ignored) {
        }
    }

    /**
     * AnimationManager's baked clip.rot values are RADIANS (they feed straight into
     * Bukkit's org.bukkit.util.EulerAngle on AnimationManager's own side, which is a
     * radians API). But the raw ArmorStand pose entity-data field this class writes
     * directly to the wire (EntityDataTypes.ROTATION) is the vanilla protocol's
     * "Rotations" type, which vanilla clients read as DEGREES - CraftBukkit itself
     * converts EulerAngle's radians to degrees via Math.toDegrees() before handing a
     * pose to NMS. Skipping that conversion here made every limb pose ~57x smaller
     * than intended (radians used where degrees were expected), so animated poses
     * collapsed to nearly straight/neutral instead of the real baked bend - looking
     * like limbs were detached from / floating away from their proper pose.
     */
    private static Vector3f toDegreesVector(float rx, float ry, float rz) {
        return new Vector3f(
                (float) Math.toDegrees(rx),
                (float) Math.toDegrees(ry),
                (float) Math.toDegrees(rz)
        );
    }

    private static EntityType resolveEntityType(String name) {
        if (name == null || name.isBlank()) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        try {
            EntityType typed = EntityTypes.getByName(key);
            if (typed != null) return typed;
            return EntityTypes.getByName("minecraft:" + key);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
