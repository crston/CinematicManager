package com.gmail.bobason01.cinematicmanager.hook;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.util.PacketHelper;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Soft-depend AnimationManager playback for cinematic NPCs.
 * <p>
 * Baked clip arrays are sampled on the cinematic tick and rendered as real, viewer-only
 * limb ArmorStands (same atomic spawn-consumer technique as AnimationManager's own
 * BoneDisplay renderer, so there is no packet-order window where a stand is briefly
 * visible unposed). Every limb stand is hidden from every player but the intended
 * viewer immediately after spawn, so other players never see them.
 */
public final class AnimationManagerHook {

    private static final double POS_EPS_SQ = 1.0E-8D;
    private static final float YAW_EPS = 0.01f;
    private static final float DEG2RAD = (float) (Math.PI / 180.0D);
    /**
     * How long to wait after the LAST resolveLimbItems callback before actually
     * spawning, so a fast Steve-fallback call gets superseded by the real-skin call
     * if the real one lands within this window (see play() below).
     * <p>
     * This was widened to 20 ticks (1s) while chasing a "scatter, then assemble"
     * visual bug, on the theory that resolveLimbItems' documented second ("real
     * skin") callback was arriving after a too-short debounce and its
     * refreshItems() swap was what looked like the limbs self-correcting. That
     * theory was DISPROVEN by direct log evidence: in the actual failing case,
     * resolveLimbItems only ever called back once. The real cause of the
     * scatter/assemble bug was unrelated and has since been fixed (see spawn()'s
     * bx/by/bz snapshot comment) - it was a Location-aliasing bug in spawn()'s
     * per-bone loop that produced wrong cascading spawn coordinates, nothing to do
     * with item/skin resolution timing at all. With that fixed, a full 1s wait here
     * serves no purpose except making every animation visibly late to start, so
     * this is back down to a short safety margin - just enough to coalesce a
     * genuine near-simultaneous double callback. A real second callback that lands
     * later than this still isn't lost: once already spawned, refreshItems() swaps
     * its items into the existing limb stands in place - no respawn, no scatter.
     */
    private static final long SPAWN_DEBOUNCE_TICKS = 2L;

    private enum GearPoseKind { HEAD, BODY, LEGS, ARM }

    private record GearAttach(String bone, EquipmentSlot source, EquipmentSlot wear,
                               GearPoseKind pose, double ox, double oy, double oz) {}

    // Same LimbType.BASE_X/Y/Z constants AnimationManager's own GearDisplay uses to
    // convert a bone's baked hand-origin into a helmet/chestplate/leggings/boots
    // pivot point. CinematicManager's Play renderer already mirrors BoneDisplay's
    // exact bone convention (same clip data, same rig), so these offsets carry over
    // unchanged.
    private static final double GEAR_BASE_X = 0.313D;
    private static final double GEAR_BASE_Y = -1.3520400014901162D;
    private static final double GEAR_BASE_Z = 0.0D;

    /**
     * Mirrors AnimationManager's own GearDisplay.ATTACH table exactly (same bone
     * names, same local offsets, same slot mapping) so the NPC's actually-equipped
     * armor/weapon follows the animated bones during a CinematicManager-driven
     * play the same way it already does during a native AnimationManager play -
     * per the user's request to match "기존 animationmanager" behavior.
     */
    private static final GearAttach[] GEAR_ATTACH = {
            new GearAttach("HEAD", EquipmentSlot.HEAD, EquipmentSlot.HEAD, GearPoseKind.HEAD,
                    -GEAR_BASE_X, -1.4375D - GEAR_BASE_Y, -GEAR_BASE_Z),
            new GearAttach("CHEST", EquipmentSlot.CHEST, EquipmentSlot.CHEST, GearPoseKind.BODY,
                    -GEAR_BASE_X, -1.02D - GEAR_BASE_Y, -GEAR_BASE_Z),
            new GearAttach("HIP", EquipmentSlot.LEGS, EquipmentSlot.LEGS, GearPoseKind.LEGS,
                    -GEAR_BASE_X, -0.72D - GEAR_BASE_Y, -GEAR_BASE_Z),
            new GearAttach("HIP", EquipmentSlot.FEET, EquipmentSlot.FEET, GearPoseKind.LEGS,
                    -GEAR_BASE_X, -0.22D - GEAR_BASE_Y, -GEAR_BASE_Z),
            new GearAttach("RIGHT_ARM", EquipmentSlot.HAND, EquipmentSlot.HAND, GearPoseKind.ARM, 0, 0, 0),
            new GearAttach("LEFT_ARM", EquipmentSlot.OFF_HAND, EquipmentSlot.HAND, GearPoseKind.ARM, 0, 0, 0),
    };

    // Local-space rotation helpers matching AnimationManager's own Offset class
    // (com.gmail.bobason01.math.Offset) exactly - not available to us directly
    // since CinematicManager only soft-depends on AnimationManager via reflection,
    // so these are small self-contained re-implementations.
    private static void rotateYaw(double x, double y, double z, double sin, double cos, double[] out) {
        out[0] = x * cos - z * sin;
        out[1] = y;
        out[2] = x * sin + z * cos;
    }

    private static void relativeOffset(double ox, double oy, double oz, float rx, float ry, float rz, double[] out) {
        double x = ox;
        double y = oy;
        double z = oz;
        double sin = Math.sin(rx);
        double cos = Math.cos(rx);
        double ny = y * cos - z * sin;
        double nz = y * sin + z * cos;
        y = ny;
        z = nz;
        sin = Math.sin(ry);
        cos = Math.cos(ry);
        double nx = x * cos - z * sin;
        nz = x * sin + z * cos;
        x = nx;
        z = nz;
        sin = Math.sin(rz);
        cos = Math.cos(rz);
        nx = x * cos + y * sin;
        ny = -x * sin + y * cos;
        out[0] = nx;
        out[1] = ny;
        out[2] = nz;
    }

    private static void applyGearPose(ArmorStand stand, GearPoseKind pose, float rx, float ry, float rz) {
        EulerAngle angle = new EulerAngle(rx, ry, rz);
        switch (pose) {
            case HEAD -> stand.setHeadPose(angle);
            case BODY -> stand.setBodyPose(angle);
            case LEGS -> {
                stand.setLeftLegPose(angle);
                stand.setRightLegPose(angle);
            }
            case ARM -> stand.setRightArmPose(angle);
        }
    }

    // Cached once per JVM: LibsDisguises' equipment getters vary a little by watcher
    // subtype/version (LivingWatcher exposes getHelmet()/getChestplate()/etc AND a
    // getItemStack(EquipmentSlot) overload depending on build), and CinematicManager
    // does not compile against LibsDisguises directly for this lookup (unlike the
    // hard DisguiseAPI/Disguise imports above, which are only used for the
    // stash/restore round-trip, not for reading equipment) - so this is resolved
    // reflectively, the same defensive style PacketHelper.lockEquipmentSlots() uses
    // for Paper-only ArmorStand methods.
    private static final java.util.Map<EquipmentSlot, String> DISGUISE_GETTER_NAMES = java.util.Map.of(
            EquipmentSlot.HEAD, "getHelmet",
            EquipmentSlot.CHEST, "getChestplate",
            EquipmentSlot.LEGS, "getLeggings",
            EquipmentSlot.FEET, "getBoots",
            EquipmentSlot.HAND, "getItemInMainHand",
            EquipmentSlot.OFF_HAND, "getItemInOffHand"
    );

    /**
     * Reads one equipment slot off a LibsDisguise's watcher. Returns null (never
     * air) if the disguise is null, has no watcher, the watcher doesn't expose
     * equipment (e.g. a non-living disguise type), or nothing is worn there.
     * <p>
     * This is the actual source of "equipped armor" for a disguised NPC:
     * LibsDisguises' watcher equipment is a presentation-layer overlay independent
     * of the underlying entity's real Bukkit equipment (an NPC can visibly wear a
     * diamond helmet through its disguise while entity.getEquipment() reports empty
     * air) - see spawnGear()'s javadoc in Play.
     */
    // Per-watcher-class reflection caches for disguiseEquipmentItem() below. This can
    // be called up to 6 times per Play on every pose-changed tick (once per
    // GEAR_ATTACH entry), i.e. potentially every animation frame for every disguised,
    // animating NPC - re-resolving getMethod() that often would be wasteful, so each
    // Method (or its absence) is looked up once per watcher Class and cached.
    private static final java.util.Map<Class<?>, java.util.Optional<Method>> DISGUISE_GET_ITEM_STACK_METHODS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Class<?>, java.util.Map<EquipmentSlot, java.util.Optional<Method>>> DISGUISE_SLOT_GETTER_METHODS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static ItemStack disguiseEquipmentItem(Disguise disguise, EquipmentSlot slot) {
        if (disguise == null) return null;
        try {
            Object watcher = disguise.getWatcher();
            if (watcher == null) return null;
            Class<?> watcherClass = watcher.getClass();

            Method getItemStack = DISGUISE_GET_ITEM_STACK_METHODS.computeIfAbsent(watcherClass, cls -> {
                try {
                    return java.util.Optional.of(cls.getMethod("getItemStack", EquipmentSlot.class));
                } catch (Throwable t) {
                    return java.util.Optional.empty();
                }
            }).orElse(null);
            if (getItemStack != null) {
                try {
                    Object result = getItemStack.invoke(watcher, slot);
                    if (result instanceof ItemStack item && !item.getType().isAir()) return item;
                } catch (Throwable ignored) {
                    // Not every LivingWatcher build exposes the generic overload - fall
                    // through to the named per-slot getter below.
                }
            }

            String getterName = DISGUISE_GETTER_NAMES.get(slot);
            if (getterName == null) return null;
            Method getter = DISGUISE_SLOT_GETTER_METHODS
                    .computeIfAbsent(watcherClass, cls -> new java.util.concurrent.ConcurrentHashMap<>())
                    .computeIfAbsent(slot, s -> {
                        try {
                            return java.util.Optional.of(watcherClass.getMethod(getterName));
                        } catch (Throwable t) {
                            return java.util.Optional.empty();
                        }
                    }).orElse(null);
            if (getter == null) return null;
            Object result = getter.invoke(watcher);
            return result instanceof ItemStack item && !item.getType().isAir() ? item : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private final CinematicManager plugin;
    private final boolean enabled;

    private Method packedClip;
    private Method resolveLimbItems;
    private Method apiPlay;
    private Method apiStop;
    private Method apiIsPlaying;
    private Field clipTicks;
    private Field clipBones;
    private Field clipLoop;
    private Field clipPos;
    private Field clipRot;
    private Field clipHidden;
    private Field clipLimbKeys;

    private final ConcurrentHashMap<UUID, Play> byNpc = new ConcurrentHashMap<>();
    private Play[] plays = new Play[4];
    private int playCount;

    /**
     * Same fix LuxGesturesHook already uses for the identical problem (see its
     * stashDisguise/restoreDisguise): a LibsDisguise body runs its own independent
     * packet loop that Bukkit's hideEntity/showEntity does not govern, so just hiding
     * the base entity was not enough to keep the disguised body from showing through
     * our own animated packet limbs. Toggling the disguise watcher's invisible flag
     * (the first attempt) was not reliable either. Fully removing the disguise for
     * the duration of the play - like Lux does - and re-attaching the exact same
     * disguise afterward is the version that is proven to work.
     */
    private record SavedDisguise(Disguise disguise, Player viewer) {}

    public AnimationManagerHook(CinematicManager plugin) {
        this.plugin = plugin;
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("AnimationManager") && resolveApi();
        if (enabled) {
            plugin.getLogger().info("AnimationManager hook enabled (viewer-only packet limbs).");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean resolveApi() {
        try {
            Class<?> api = Class.forName("com.gmail.bobason01.api.AnimationAPI");
            apiPlay = api.getMethod("play", Entity.class, String.class);
            apiStop = api.getMethod("stop", Entity.class);
            apiIsPlaying = api.getMethod("isPlaying", Entity.class);
            try {
                Class<?> clip = Class.forName("com.gmail.bobason01.api.PackedClip");
                packedClip = api.getMethod("packedClip", String.class);
                resolveLimbItems = api.getMethod("resolveLimbItems", Entity.class, clip, Consumer.class);
                clipTicks = clip.getField("ticks");
                clipBones = clip.getField("boneCount");
                clipLoop = clip.getField("loop");
                clipPos = clip.getField("pos");
                clipRot = clip.getField("rot");
                clipHidden = clip.getField("hidden");
                clipLimbKeys = clip.getField("limbKeys");
            } catch (Throwable t) {
                plugin.getLogger().warning("AnimationManager packet clip API missing — using entity play fallback.");
                packedClip = null;
            }
            return apiPlay != null;
        } catch (Throwable t) {
            plugin.getLogger().warning("AnimationManager API resolve failed: " + t.getMessage());
            return false;
        }
    }

    public boolean play(Player viewer, Entity entity, String clipRef) {
        if (!enabled || viewer == null || entity == null || clipRef == null || clipRef.isBlank()) {
            return false;
        }
        String id = clipRef.trim();
        if (id.equalsIgnoreCase("STOP") || id.regionMatches(true, 0, "STOP:", 0, 5)) {
            stop(entity, viewer);
            return true;
        }
        if (byNpc.containsKey(entity.getUniqueId())) {
            stop(entity, viewer);
        }
        Object clip = null;
        if (packedClip != null) {
            try {
                clip = packedClip.invoke(null, id);
            } catch (Throwable t) {
                plugin.getLogger().warning("AnimationManager clip resolve failed (" + id + "): " + t.getMessage());
            }
        }
        preparePlayback(viewer, entity);
        if (clip == null) {
            return playViaApi(viewer, entity, id);
        }

        int bones;
        int ticks;
        byte loop;
        float[] pos;
        float[] rot;
        boolean[] hidden;
        try {
            bones = clipBones.getInt(clip);
            ticks = clipTicks.getInt(clip);
            loop = clipLoop.getByte(clip);
            pos = (float[]) clipPos.get(clip);
            rot = (float[]) clipRot.get(clip);
            hidden = (boolean[]) clipHidden.get(clip);
        } catch (Throwable t) {
            plugin.getLogger().warning("AnimationManager clip buffers unreadable: " + t.getMessage());
            return false;
        }
        // Bone-name lookup for gear attachment (see GEAR_ATTACH) - kept in its own
        // lenient try/catch so an older AnimationManager build without this field,
        // or any other reflection hiccup here, only turns off the armor-follows-
        // bones extra instead of breaking the core animation above.
        String[] limbKeys = null;
        try {
            if (clipLimbKeys != null) {
                limbKeys = (String[]) clipLimbKeys.get(clip);
            }
        } catch (Throwable ignored) {
        }
        if (bones <= 0 || ticks <= 0 || pos == null || rot == null || hidden == null) {
            plugin.getLogger().warning("AnimationManager: empty clip '" + id + "'");
            return false;
        }

        UUID npcId = entity.getUniqueId();
        // Disguise is stashed inside the resolveLimbItems callback below (right before
        // the debounced spawn()), not before this call: AnimationManager has its own
        // DisguiseSkinLookup that reads the skin texture straight off a LibsDisguise
        // PlayerDisguise when one is still attached to the entity (see
        // SkinCache#hintOf in AnimationManager). Undisguising before this call meant
        // the entity (a bare marker ArmorStand) had no skin of its own by the time
        // AnimationManager looked for one, so it silently fell back to the generic
        // Steve texture instead of the NPC's real skin.
        Play play = new Play(viewer, entity, npcId, bones, ticks, loop, pos, rot, hidden, null, limbKeys);
        addPlay(play);

        try {
            resolveLimbItems.invoke(null, entity, clip, (Consumer<ItemStack[]>) items -> {
                Play current = byNpc.get(npcId);
                if (current != play || current.dead) return;
                if (!current.spawned) {
                    if (current.pendingSpawn == null) {
                        // First callback for this play - the skin hint has already been
                        // read (and cached) off the still-attached disguise by the
                        // resolveLimbItems call that led to this callback, so it's safe
                        // to strip the disguise body now. A LibsDisguise body runs its
                        // own independent packet loop that viewer.hideEntity() does not
                        // govern, and undisguiseToAll() only removes the disguise from
                        // LibsDisguises' own bookkeeping here - the actual "stop
                        // broadcasting the disguised body" effect follows on
                        // LibsDisguises' own next scheduled pass, not synchronously.
                        current.disguise = stashDisguise(viewer, entity);
                    } else {
                        // A later callback landed before the debounced spawn fired -
                        // cancel it so only the newest items ever get used.
                        current.pendingSpawn.cancel();
                    }
                    // Debounced instead of a flat delay: resolveLimbItems's own contract
                    // is "Steve immediately, real skin when ready - onItems may run
                    // twice". Spawning off the very first (fast, Steve-fallback) call
                    // used to visibly show placeholder-skinned/placeholder-modeled limb
                    // pieces for a few frames before the real-skin callback swapped them
                    // in - looking exactly like the NPC assembling itself out of
                    // fragments right after it appears. Every callback now (re)starts a
                    // short wait instead: if a second call lands before it fires, its
                    // items replace the pending ones and the timer restarts, so we only
                    // ever spawn once - with whichever items turned out to be the LAST
                    // (most resolved) ones actually received - and the very first frame
                    // the viewer sees is already the final pose with the final skin.
                    current.pendingSpawn = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Play still = byNpc.get(npcId);
                        if (still != current || still.dead || still.spawned) {
                            return;
                        }
                        still.pendingSpawn = null;
                        still.spawn(items);
                        if (!still.hasLimbs()) {
                            // The packet-limb spawn produced zero live limb entities -
                            // bail out to the API fallback renderer instead.
                            byNpc.remove(npcId, still);
                            removePlay(still);
                            // The disguise was already stripped above for this
                            // abandoned Play - restore it immediately rather than
                            // leaking it, since playViaApi() below starts an entirely
                            // different renderer.
                            restoreDisguise(entity, still.disguise);
                            still.disguise = null;
                            playViaApi(viewer, entity, id);
                        }
                    }, SPAWN_DEBOUNCE_TICKS);
                } else {
                    current.refreshItems(items);
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("AnimationManager skin resolve failed (" + id + "): " + t.getMessage());
            stop(entity, viewer);
            return false;
        }
        return true;
    }

    private void preparePlayback(Player viewer, Entity entity) {
        var lux = plugin.getLuxGesturesHook();
        if (lux != null && lux.isEnabled()) {
            lux.ensureStopped(entity, viewer);
        }
        var hmc = plugin.getHmcCosmeticsHook();
        if (hmc != null && hmc.isEnabled()) {
            hmc.suspendForGesture(entity, viewer);
        }
        if (viewer != null && viewer.isOnline() && entity.isValid()) {
            viewer.hideEntity(plugin, entity);
        }
        if (hmc != null && hmc.isEnabled()) {
            hmc.suspendForGesture(entity, viewer);
        }
    }

    private boolean playViaApi(Player viewer, Entity entity, String id) {
        if (apiPlay == null) {
            plugin.getLogger().warning("AnimationManager: cannot play '" + id + "'");
            return false;
        }
        try {
            boolean ok = Boolean.TRUE.equals(apiPlay.invoke(null, entity, id));
            if (!ok) {
                plugin.getLogger().warning("AnimationManager: unknown clip '" + id + "'");
                return false;
            }
            restrictLimbsToViewer(viewer, entity);
            Bukkit.getScheduler().runTask(plugin, () -> restrictLimbsToViewer(viewer, entity));
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("AnimationManager play failed (" + id + "): " + t.getMessage());
            return false;
        }
    }

    private void restrictLimbsToViewer(Player viewer, Entity entity) {
        if (viewer == null || entity == null || !entity.isValid()) return;
        org.bukkit.World world = entity.getWorld();
        if (world == null) return;
        if (viewer.isOnline()) {
            viewer.hideEntity(plugin, entity);
        }
        for (Entity nearby : world.getNearbyEntities(entity.getLocation(), 6.0, 6.0, 6.0)) {
            boolean limb = nearby.getScoreboardTags().contains("am_limb");
            boolean name = nearby.getScoreboardTags().contains("am_name");
            if (!limb && !name) continue;
            // Name plates on cinematic ArmorStand bases become "Armor Stand".
            if (name) {
                hideFromAll(nearby);
                continue;
            }
            viewer.showEntity(plugin, nearby);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                    other.hideEntity(plugin, nearby);
                }
            }
        }
    }

    private void hideFromAll(Entity entity) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.hideEntity(plugin, entity);
        }
    }

    public boolean isPlaying(Entity entity) {
        if (entity == null) return false;
        if (byNpc.containsKey(entity.getUniqueId())) return true;
        if (apiIsPlaying == null) return false;
        try {
            return Boolean.TRUE.equals(apiIsPlaying.invoke(null, entity));
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isPlayingFor(Player viewer) {
        if (viewer == null || playCount == 0) return false;
        UUID id = viewer.getUniqueId();
        for (int i = 0; i < playCount; i++) {
            Play play = plays[i];
            if (play.viewer.getUniqueId().equals(id) && !play.dead && play.keepsSession()) return true;
        }
        return false;
    }

    public void ensureStopped(Entity entity, Player viewer) {
        if (entity != null && isPlaying(entity)) {
            stop(entity, viewer);
        }
    }

    public void stop(Entity entity, Player viewer) {
        if (entity == null) return;
        Play play = byNpc.remove(entity.getUniqueId());
        if (play != null) {
            removePlay(play);
            play.destroy();
        }
        if (apiStop != null) {
            try {
                apiStop.invoke(null, entity);
            } catch (Throwable ignored) {
            }
        }
        restoreNpc(entity, play != null && play.viewer != null ? play.viewer : viewer,
                play != null ? play.disguise : null);
    }

    public void tick(Player viewer) {
        if (!enabled || playCount == 0 || viewer == null) return;
        UUID id = viewer.getUniqueId();
        for (int i = 0; i < playCount; ) {
            Play play = plays[i];
            if (play.dead || !play.viewer.getUniqueId().equals(id)) {
                i++;
                continue;
            }
            if (!play.tick()) {
                byNpc.remove(play.npcId, play);
                removeAt(i);
                if (apiStop != null) {
                    try {
                        apiStop.invoke(null, play.npc);
                    } catch (Throwable ignored) {
                    }
                }
                restoreNpc(play.npc, play.viewer, play.disguise);
                continue;
            }
            i++;
        }
    }

    public void stopAll(Player viewer) {
        if (viewer == null) return;
        UUID id = viewer.getUniqueId();
        for (int i = playCount - 1; i >= 0; i--) {
            Play play = plays[i];
            if (play.viewer.getUniqueId().equals(id)) {
                byNpc.remove(play.npcId, play);
                removeAt(i);
                play.destroy();
                restoreNpc(play.npc, play.viewer, play.disguise);
            }
        }
    }

    public void shutdown() {
        for (int i = playCount - 1; i >= 0; i--) {
            Play play = plays[i];
            byNpc.remove(play.npcId, play);
            play.destroy();
            restoreNpc(play.npc, play.viewer, play.disguise);
        }
        playCount = 0;
        byNpc.clear();
    }

    private void restoreNpc(Entity entity, Player viewer, SavedDisguise disguise) {
        plugin.getNpcManager().revealToViewer(viewer, entity);
        // Order matters: re-attach the disguise only after the base entity is shown
        // again, matching LuxGesturesHook's own proven restore order (showEntity,
        // then re-disguise) rather than the reverse.
        restoreDisguise(entity, disguise);
        var hmc = plugin.getHmcCosmeticsHook();
        if (hmc != null && hmc.isEnabled() && entity != null) {
            hmc.resumeAfterGesture(entity, viewer);
        }
    }

    /**
     * Clone and fully strip the NPC's LibsDisguise body (if any) before we start
     * drawing our own animated packet limbs, so there is nothing left for LibsDisguise
     * to keep re-broadcasting underneath them. Returns null when there was no disguise
     * to touch (plain entity, or LibsDisguises not installed).
     */
    private SavedDisguise stashDisguise(Player viewer, Entity entity) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) return null;
        try {
            if (!DisguiseAPI.isDisguised(entity)) return null;
            Disguise disguise = DisguiseAPI.getDisguise(entity);
            if (disguise == null) return null;
            Disguise copy;
            try {
                copy = disguise.clone();
            } catch (Throwable t) {
                copy = disguise;
            }
            DisguiseAPI.undisguiseToAll(entity);
            return new SavedDisguise(copy, viewer);
        } catch (Throwable t) {
            plugin.getLogger().warning("[AnimationManagerHook] could not clear disguise for play: " + t.getMessage());
            return null;
        }
    }

    /** Re-attach the disguise saved by stashDisguise() once our packet limbs are gone. */
    private void restoreDisguise(Entity entity, SavedDisguise saved) {
        if (saved == null || saved.disguise() == null || entity == null || !entity.isValid()) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) return;
        try {
            Player viewer = saved.viewer();
            if (viewer != null && viewer.isOnline()) {
                DisguiseAPI.disguiseToPlayers(entity, saved.disguise(), viewer);
            } else {
                DisguiseAPI.disguiseToAll(entity, saved.disguise());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[AnimationManagerHook] could not restore disguise after play: " + t.getMessage());
        }
    }

    private void addPlay(Play play) {
        if (playCount == plays.length) {
            Play[] next = new Play[plays.length << 1];
            System.arraycopy(plays, 0, next, 0, playCount);
            plays = next;
        }
        plays[playCount++] = play;
        byNpc.put(play.npcId, play);
    }

    private void removePlay(Play play) {
        for (int i = 0; i < playCount; i++) {
            if (plays[i] == play) {
                removeAt(i);
                return;
            }
        }
    }

    private void removeAt(int i) {
        plays[i] = plays[--playCount];
        plays[playCount] = null;
    }

    private final class Play {
        final Player viewer;
        final Entity npc;
        final UUID npcId;
        final int boneCount;
        final int ticks;
        final byte loop;
        final float[] pos;
        final float[] rot;
        final boolean[] hidden;
        final ArmorStand[] stands;
        final boolean[] holding;
        // LimbType.name() per bone, or null if this clip/AnimationManager build
        // didn't provide one - see GEAR_ATTACH. Null entirely disables the
        // armor-follows-bones extra for this Play without touching anything else.
        final String[] limbKeys;
        // One slot per GEAR_ATTACH entry; null where that attach has no matching
        // bone in this clip or the NPC has nothing equipped in that slot.
        final ArmorStand[] gearStands = new ArmorStand[GEAR_ATTACH.length];
        // Precomputed once in the constructor: GEAR_ATTACH[g].bone()'s index into
        // limbKeys, or -1 if this clip has no matching bone. Replaces a per-tick,
        // per-attach O(boneCount) linear scan (boneIndexOf()) with a single array
        // read in the hot spawnGear()/tickGear() loops.
        final int[] gearBoneIndex = new int[GEAR_ATTACH.length];
        // Last item actually applied to gearStands[g] - lets tickGear() skip the
        // clone()+setItem() round-trip on a pose-changed tick where the equipped
        // item hasn't actually changed since the last check.
        final ItemStack[] gearLastItem = new ItemStack[GEAR_ATTACH.length];
        // Scratch buffers reused by placeGearLoc() across every spawnGear()/tickGear()
        // call instead of allocating three new double[3]s per call.
        private final double[] gearBonePos = new double[3];
        private final double[] gearLocal = new double[3];
        private final double[] gearLocalYawed = new double[3];
        final Location loc = new Location(null, 0, 0, 0);
        ItemStack[] items;
        boolean spawned;
        boolean dead;
        int frame;
        double carry;
        double lastX = Double.NaN;
        double lastY;
        double lastZ;
        float lastYaw;
        int lastFrame = Integer.MIN_VALUE;
        // Not final: set right after resolveLimbItems has read the skin off the
        // still-attached disguise (see play() below) - null until then.
        SavedDisguise disguise;
        // The debounced spawn task waiting for resolveLimbItems' items to settle
        // (see play() below) - null once spawn() has actually run, or if nothing
        // is currently pending.
        BukkitTask pendingSpawn;

        Play(Player viewer, Entity npc, UUID npcId, int boneCount, int ticks, byte loop,
             float[] pos, float[] rot, boolean[] hidden, SavedDisguise disguise, String[] limbKeys) {
            this.viewer = viewer;
            this.npc = npc;
            this.npcId = npcId;
            this.boneCount = boneCount;
            this.ticks = ticks;
            this.loop = loop;
            this.pos = pos;
            this.rot = rot;
            this.hidden = hidden;
            this.stands = new ArmorStand[boneCount];
            this.holding = new boolean[boneCount];
            this.disguise = disguise;
            this.limbKeys = limbKeys;
            for (int g = 0; g < GEAR_ATTACH.length; g++) {
                gearBoneIndex[g] = boneIndexOf(GEAR_ATTACH[g].bone());
            }
        }

        private int boneIndexOf(String want) {
            if (limbKeys == null) return -1;
            for (int i = 0; i < limbKeys.length; i++) {
                if (want.equals(limbKeys[i])) return i;
            }
            return -1;
        }

        /**
         * What item this NPC actually has equipped in the given slot, checked in
         * priority order: the LibsDisguise watcher first (see the disguise vs. real
         * equipment note on spawnGear() below), then the real entity's own equipment.
         * Returns null (never air) when nothing is equipped there from either source.
         */
        private ItemStack currentGear(Disguise sourceDisguise, EntityEquipment equipment, EquipmentSlot slot) {
            ItemStack gear = disguiseEquipmentItem(sourceDisguise, slot);
            if ((gear == null || gear.getType().isAir()) && equipment != null) {
                gear = equipment.getItem(slot);
            }
            return gear == null || gear.getType().isAir() ? null : gear;
        }

        /**
         * Fills loc with this attach's current world position, given
         * bx/by/bz/yaw/sin/cos already snapshotted. Uses the gearBonePos/gearLocal/
         * gearLocalYawed scratch fields instead of allocating fresh double[3]s -
         * safe because Play is only ever ticked/spawned on the main thread.
         */
        private void placeGearLoc(double bx, double by, double bz, float yaw, double sin, double cos,
                                   int o, GearAttach attach) {
            rotateYaw(pos[o], pos[o + 1], pos[o + 2], sin, cos, gearBonePos);
            relativeOffset(attach.ox(), attach.oy(), attach.oz(), rot[o], rot[o + 1], rot[o + 2], gearLocal);
            rotateYaw(gearLocal[0], gearLocal[1], gearLocal[2], sin, cos, gearLocalYawed);
            loc.setX(bx + gearBonePos[0] + gearLocalYawed[0]);
            loc.setY(by + gearBonePos[1] + gearLocalYawed[1]);
            loc.setZ(bz + gearBonePos[2] + gearLocalYawed[2]);
            loc.setYaw(yaw);
            loc.setPitch(0f);
        }

        /**
         * AnimationManagerHook's equivalent of AnimationManager's own GearDisplay:
         * one extra, viewer-only ArmorStand per equipped armor/weapon piece, riding
         * the bone it visually belongs to. The NPC's real equipment is only READ
         * here (cloned onto these stands), never modified - same reasoning as
         * AnimationManager's HideController: attribute modifiers baked into the
         * gear (e.g. MMOItems stats) must never be recalculated by touching it.
         * <p>
         * Must be called with the SAME bx/by/bz/yaw/sin/cos/poseBase spawn() already
         * computed as local primitives - never re-derived from npc.getLocation(loc)
         * inside this method's own loop, for the exact aliasing reason spawn()'s own
         * per-bone loop had to be fixed (loc is mutated below on every iteration).
         */
        private void spawnGear(double bx, double by, double bz, float yaw, double sin, double cos, int poseBase) {
            if (limbKeys == null) return;
            // Two possible sources for "what armor is this NPC wearing", tried in this
            // order:
            //  1. The LibsDisguise watcher, if the NPC was disguised (see stashDisguise()
            //     above - disguise here is the CLONE taken right before undisguising, so
            //     its watcher still carries whatever helmet/chestplate/etc the disguise
            //     was set up with). This is very likely THE actual source of "equipped"
            //     armor for a MythicMobs/LibsDisguise NPC: LibsDisguises' equipment is a
            //     presentation-layer overlay independent of the underlying entity's real
            //     Bukkit equipment, so an NPC can visibly wear a diamond helmet through
            //     its disguise while entity.getEquipment() reports empty air - which is
            //     exactly why gear was invisible even though everything else here worked.
            //  2. The underlying entity's real Bukkit equipment, for plain (non-disguised)
            //     NPCs or as a fallback.
            Disguise sourceDisguise = disguise != null ? disguise.disguise() : null;
            EntityEquipment equipment = npc instanceof LivingEntity living ? living.getEquipment() : null;
            if (sourceDisguise == null && equipment == null) return;
            for (int g = 0; g < GEAR_ATTACH.length; g++) {
                GearAttach attach = GEAR_ATTACH[g];
                int bone = gearBoneIndex[g];
                if (bone < 0) continue;
                ItemStack gear = currentGear(sourceDisguise, equipment, attach.source());
                if (gear == null) continue;
                int o = poseBase + bone * 3;
                placeGearLoc(bx, by, bz, yaw, sin, cos, o, attach);
                ItemStack worn = gear.clone();
                gearStands[g] = PacketHelper.spawnGearStandReal(plugin, viewer, loc, attach.wear(), worn,
                        attach.pose().ordinal(), rot[o], rot[o + 1], rot[o + 2]);
                gearLastItem[g] = worn;
            }
        }

        /**
         * Per-tick counterpart to spawnGear() - see its javadoc for the aliasing
         * warning. Unlike the base limb loop (whose held-item is fixed by the clip
         * itself), gear tracks the NPC's LIVE equip state on every pose change: a
         * piece equipped after spawn() gets its stand created here, one removed gets
         * its stand despawned, and one swapped for a different item gets re-worn in
         * place - so "장착한 방어구/무기가 애니메이션 중에도 실제 착용 상태를 따라간다"
         * holds for the whole play, not just whatever was equipped at the instant it
         * started.
         */
        private void tickGear(double bx, double by, double bz, float yaw, double sin, double cos, int poseBase,
                               boolean moved, boolean poseChanged) {
            if (!moved && !poseChanged) return;
            Disguise sourceDisguise = disguise != null ? disguise.disguise() : null;
            EntityEquipment equipment = npc instanceof LivingEntity living ? living.getEquipment() : null;
            for (int g = 0; g < GEAR_ATTACH.length; g++) {
                GearAttach attach = GEAR_ATTACH[g];
                int bone = gearBoneIndex[g];
                if (bone < 0) continue;
                int o = poseBase + bone * 3;
                ArmorStand stand = gearStands[g];
                if (poseChanged) {
                    ItemStack gear = currentGear(sourceDisguise, equipment, attach.source());
                    if (gear == null) {
                        if (stand != null && stand.isValid()) {
                            stand.remove();
                        }
                        gearStands[g] = null;
                        gearLastItem[g] = null;
                        continue;
                    }
                    if (stand == null || !stand.isValid()) {
                        placeGearLoc(bx, by, bz, yaw, sin, cos, o, attach);
                        ItemStack worn = gear.clone();
                        gearStands[g] = PacketHelper.spawnGearStandReal(plugin, viewer, loc, attach.wear(), worn,
                                attach.pose().ordinal(), rot[o], rot[o + 1], rot[o + 2]);
                        gearLastItem[g] = worn;
                        continue; // freshly spawned already at the right pose/position
                    }
                    // Only re-clone + re-set the worn item when it actually changed
                    // since the last check - isSimilar() ignores stack amount (which
                    // a single worn piece never varies) but catches a real swap.
                    if (gearLastItem[g] == null || !gearLastItem[g].isSimilar(gear)) {
                        ItemStack worn = gear.clone();
                        stand.getEquipment().setItem(attach.wear(), worn);
                        gearLastItem[g] = worn;
                    }
                }
                stand = gearStands[g];
                if (stand == null || !stand.isValid()) continue;
                placeGearLoc(bx, by, bz, yaw, sin, cos, o, attach);
                PacketHelper.repositionLimbStand(viewer, stand, loc);
                if (poseChanged) {
                    applyGearPose(stand, attach.pose(), rot[o], rot[o + 1], rot[o + 2]);
                }
            }
        }

        void spawn(ItemStack[] resolved) {
            if (dead || spawned || viewer == null || !viewer.isOnline() || !npc.isValid()) return;
            this.items = resolved;
            Location base = npc.getLocation(loc);
            // CRITICAL: base and loc are THE SAME OBJECT — Entity#getLocation(Location)
            // mutates and returns the passed-in instance. poseInto() below writes each
            // bone's computed world position back into loc (== base). If we read
            // base.getX()/getY()/getZ() *inside* the loop (as arguments evaluated at
            // each iteration), every bone after the first reads the PREVIOUS bone's
            // already-offset position instead of the NPC's stable base location, so
            // each bone's offset compounds onto wherever the last one landed. That is
            // exactly what produced the "scattered pile that assembles a few frames
            // later" bug: bones 1..N spawned at wildly cascading, wrong coordinates,
            // then tick() (which correctly snapshots bx/by/bz into local doubles
            // BEFORE its loop) recomputed the correct positions on the very next
            // update and snapped everything into place. Snapshotting bx/by/bz here too
            // — before the loop, as primitives independent of loc's mutation — fixes
            // it exactly the way tick() already does it.
            double bx = base.getX();
            double by = base.getY();
            double bz = base.getZ();
            float yaw = base.getYaw();
            double sin = Math.sin(yaw * DEG2RAD);
            double cos = Math.cos(yaw * DEG2RAD);
            // Spawn straight into whatever frame playback has already reached, instead
            // of always frame 0. resolveLimbItems() resolves skin/item textures
            // asynchronously and can take several ticks; tick() keeps advancing `frame`
            // the whole time (it only skips sending packets while !spawned). Spawning
            // at a hardcoded frame 0 meant the limbs always first appeared in the
            // clip's very first pose, then immediately teleported/re-posed to the real
            // (already-elapsed) frame on the very next tick — a visible "scatter, then
            // snap into place" pop right after the NPC appears. Sampling the CURRENT
            // frame here instead makes the limbs render already in the correct pose on
            // their very first packet, with no follow-up jump.
            int sampled = wrapFrame();
            int poseBase = sampled * boneCount * 3;
            int hideBase = sampled * boneCount;
            for (int i = 0; i < boneCount; i++) {
                int o = poseBase + i * 3;
                poseInto(o, bx, by, bz, sin, cos, yaw);
                boolean hide = hidden[hideBase + i];
                ItemStack hand = item(i);
                stands[i] = PacketHelper.spawnLimbStandReal(
                        plugin, viewer, loc, hide ? null : hand, rot[o], rot[o + 1], rot[o + 2]);
                holding[i] = !hide && hand != null && !hand.getType().isAir();
            }
            spawnGear(bx, by, bz, yaw, sin, cos, poseBase);
            spawned = true;
            lastX = bx;
            lastY = by;
            lastZ = bz;
            lastYaw = yaw;
            lastFrame = sampled;
            if (hasLimbs()) {
                viewer.hideEntity(plugin, npc);
                // The disguise itself (if any) was already fully stripped in
                // stashDisguise() before this Play was even constructed - see the
                // SavedDisguise javadoc. Nothing further to hide here.
            }
        }

        boolean hasLimbs() {
            for (ArmorStand stand : stands) if (stand != null && stand.isValid()) return true;
            return false;
        }

        boolean keepsSession() {
            return loop == 0 && frame < ticks;
        }

        void refreshItems(ItemStack[] resolved) {
            if (dead || !spawned) {
                this.items = resolved;
                return;
            }
            this.items = resolved;
            for (int i = 0; i < boneCount; i++) {
                ArmorStand stand = stands[i];
                if (stand == null || !stand.isValid()) continue;
                boolean hide = hidden[wrapFrame() * boneCount + i];
                ItemStack hand = item(i);
                if (hide) {
                    if (holding[i]) {
                        stand.getEquipment().setItemInMainHand(null);
                        holding[i] = false;
                    }
                } else {
                    stand.getEquipment().setItemInMainHand(hand);
                    holding[i] = hand != null && !hand.getType().isAir();
                }
            }
        }

        boolean tick() {
            if (dead) return false;
            if (viewer == null || !viewer.isOnline() || npc == null || !npc.isValid()) {
                destroy();
                return false;
            }
            if (!spawned) {
                // Do not advance frame/carry while waiting on the async
                // resolveLimbItems() resolve (skin lookup off the disguise, item
                // texture generation, etc - can easily take 15-20+ ticks). frame used
                // to keep counting the whole time, so by the time spawn() finally ran
                // it would sample whatever mid-clip frame playback had already
                // "reached" - often a busy, limbs-flying-outward moment rather than
                // the clip's authored rest pose, which is exactly why the NPC first
                // appeared as several disconnected pieces that only "assembled" a few
                // ticks later as tick() played out the rest of that motion. Freezing
                // frame here means playback always visibly starts from frame 0 the
                // instant it becomes visible, no matter how long setup took.
                return true;
            }
            carry += 1.0D;
            int step = (int) carry;
            if (step != 0) {
                carry -= step;
                frame += step;
            }
            if (loop == 0 && frame >= ticks) {
                destroy();
                return false;
            }
            int sampled = wrapFrame();
            Location base = npc.getLocation(loc);
            double bx = base.getX();
            double by = base.getY();
            double bz = base.getZ();
            float yaw = base.getYaw();
            boolean moved = moved(bx, by, bz, yaw);
            boolean poseChanged = sampled != lastFrame;
            if (!spawned || (!moved && !poseChanged)) {
                return true;
            }
            double sin = Math.sin(yaw * DEG2RAD);
            double cos = Math.cos(yaw * DEG2RAD);
            int poseBase = sampled * boneCount * 3;
            int hideBase = sampled * boneCount;
            for (int i = 0; i < boneCount; i++) {
                ArmorStand stand = stands[i];
                if (stand == null || !stand.isValid()) continue;
                int o = poseBase + i * 3;
                poseInto(o, bx, by, bz, sin, cos, yaw);
                // "moved" only tracks whether the NPC's own base location/yaw changed -
                // it says nothing about whether this bone's own animated offset (pos[o])
                // changed, which happens on almost every frame of a gesture played while
                // the NPC stands still. Gating the teleport on "moved" alone left each
                // limb's floating stand frozen at wherever it first spawned any time the
                // NPC wasn't walking, while its rotation kept animating around that stale
                // position - looking like the motion was dragging/lagging behind itself.
                // AnimationManager's own BoneDisplay.update() (the reference renderer)
                // re-snaps position on every frame advance for exactly this reason.
                //
                // (Per-bone dead-zone skipping was tried here as a packet-count
                // optimization but made early frames visibly stagger bone-by-bone
                // instead of settling into pose together, so every bone is simply
                // re-sent on every frame advance - correctness over packet count.)
                if (moved || poseChanged) {
                    PacketHelper.repositionLimbStand(viewer, stand, loc);
                }
                if (poseChanged) {
                    stand.setRightArmPose(new EulerAngle(rot[o], rot[o + 1], rot[o + 2]));
                    boolean hide = hidden[hideBase + i];
                    if (hide) {
                        if (holding[i]) {
                            stand.getEquipment().setItemInMainHand(null);
                            holding[i] = false;
                        }
                    } else if (!holding[i]) {
                        ItemStack hand = item(i);
                        if (hand != null && !hand.getType().isAir()) {
                            stand.getEquipment().setItemInMainHand(hand);
                            holding[i] = true;
                        }
                    }
                }
            }
            tickGear(bx, by, bz, yaw, sin, cos, poseBase, moved, poseChanged);
            lastX = bx;
            lastY = by;
            lastZ = bz;
            lastYaw = yaw;
            lastFrame = sampled;
            return true;
        }

        void destroy() {
            if (dead) return;
            dead = true;
            if (pendingSpawn != null) {
                pendingSpawn.cancel();
                pendingSpawn = null;
            }
            if (spawned) {
                for (ArmorStand stand : stands) {
                    if (stand != null && stand.isValid()) {
                        stand.remove();
                    }
                }
                for (ArmorStand stand : gearStands) {
                    if (stand != null && stand.isValid()) {
                        stand.remove();
                    }
                }
            }
            spawned = false;
        }

        private int wrapFrame() {
            if (frame < ticks) return Math.max(frame, 0);
            return loop == 2 ? frame % ticks : ticks - 1;
        }

        private boolean moved(double x, double y, double z, float yaw) {
            if (Double.isNaN(lastX)) return true;
            double dx = x - lastX;
            double dy = y - lastY;
            double dz = z - lastZ;
            if (dx * dx + dy * dy + dz * dz > POS_EPS_SQ) return true;
            float dyaw = yaw - lastYaw;
            if (dyaw < 0f) dyaw = -dyaw;
            return dyaw > YAW_EPS;
        }

        private void poseInto(int o, double bx, double by, double bz, double sin, double cos, float yaw) {
            float x = pos[o];
            float z = pos[o + 2];
            loc.setX(bx + x * cos - z * sin);
            loc.setY(by + pos[o + 1]);
            loc.setZ(bz + x * sin + z * cos);
            loc.setYaw(yaw);
            loc.setPitch(0f);
        }

        private ItemStack item(int bone) {
            return items == null || bone >= items.length ? null : items[bone];
        }
    }
}
