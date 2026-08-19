package com.gmail.bobason01.cinematicmanager.hook;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soft-depend bridge for LuxGestures entity gestures.
 * <p>
 * Modern Lux uses {@code item-model-pack} ({@code luxgestures:arm_left_upper} etc.) via
 * PixelSkinService. Player gestures generate per-limb item_model + color flags from the skin PNG.
 * Entity API only applies that if PixelSkin is cached for the base UUID — otherwise it falls back
 * to PLAYER_HEAD + numeric CMD, which this pack does not override → floating heads.
 * <p>
 * Cinematic NPCs are also small/marker AS + LibsDisguises + HMC, so playback uses a temporary
 * full-size host, injects pixel-skin cache for that host, then restores visuals on stop.
 */
public final class LuxGesturesHook {

    private static final String STEVE_TEXTURE_URL =
            "http://textures.minecraft.net/texture/1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb325c";

    private final CinematicManager plugin;
    private final boolean enabled;

    private Method apiGetInstance;
    private Method apiGetGestureManager;
    private Method gmGetGesture;
    private Method gmGetOrAddEntity;
    private Method gmHasEntity;
    private Method gmRemoveEntity;
    private Method modelDespawnPlayer;
    private Method modelDespawnAll;
    private Method textureFromBase64;
    private Constructor<?> textureCtorUrl;

    /** cinematic NPC uuid → playback state */
    private final ConcurrentHashMap<UUID, GesturePlay> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> textureCache = new ConcurrentHashMap<>();
    /** skinUrl|slim → Map&lt;limb, PixelSkinData&gt; */
    private final ConcurrentHashMap<String, Map<?, ?>> pixelSkinCache = new ConcurrentHashMap<>();

    private Method textureGetUrl;
    private Method textureIsSlim;
    private Method pixelSkinGenerate;
    private java.lang.reflect.Field pixelByEntityField;
    private record SavedDisguise(Disguise disguise, Player viewer) {}

    private record GesturePlay(
            Player viewer,
            Entity host,
            Object model,
            SavedDisguise disguise,
            int watchdogTaskId
    ) {}

    public LuxGesturesHook(CinematicManager plugin) {
        this.plugin = plugin;
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("LuxGestures") && resolveApi();
        if (enabled) {
            plugin.getLogger().info("LuxGestures hook enabled (NPC entity gestures).");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean resolveApi() {
        try {
            Class<?> apiClass = Class.forName("com.aselstudios.gestures.api.GesturesAPI");
            apiGetInstance = apiClass.getMethod("getInstance");
            apiGetGestureManager = apiClass.getMethod("getGestureManager");

            Class<?> gmClass = Class.forName("com.aselstudios.gestures.api.gesture.IGestureManager");
            gmGetGesture = gmClass.getMethod("getGesture", String.class);
            gmGetOrAddEntity = gmClass.getMethod("getOrAddEntityGesture",
                    Entity.class, Map.class,
                    Class.forName("com.aselstudios.gestures.api.gesture.Gesture"));
            gmHasEntity = gmClass.getMethod("hasEntityGesture", Entity.class);
            gmRemoveEntity = gmClass.getMethod("removeEntityGesture", Entity.class);

            Class<?> modelClass = Class.forName("com.aselstudios.gestures.api.entity.EntityPlayerModel");
            try {
                modelDespawnPlayer = modelClass.getMethod("despawn", Player.class);
            } catch (NoSuchMethodException ignored) {
                modelDespawnPlayer = null;
            }
            try {
                modelDespawnAll = modelClass.getMethod("despawn");
            } catch (NoSuchMethodException ignored) {
                modelDespawnAll = null;
            }

            Class<?> tw = Class.forName("com.aselstudios.gestures.playeranimator.api.texture.TextureWrapper");
            textureFromBase64 = tw.getMethod("fromBase64", String.class);
            textureCtorUrl = tw.getConstructor(String.class, boolean.class);
            textureGetUrl = tw.getMethod("getUrl");
            textureIsSlim = tw.getMethod("isSlim");

            try {
                Class<?> gen = Class.forName(
                        "com.aselstudios.gestures.playeranimator.api.skin.pixel.PixelSkinGenerator");
                pixelSkinGenerate = gen.getMethod("generate",
                        java.awt.image.BufferedImage.class, boolean.class, String.class);
                Class<?> pss = Class.forName("com.aselstudios.gestures.skin.PixelSkinService");
                pixelByEntityField = pss.getDeclaredField("byEntity");
                pixelByEntityField.setAccessible(true);
            } catch (Throwable t) {
                plugin.getLogger().warning("LuxGestures pixel-skin bridge unavailable: " + t.getMessage());
                pixelSkinGenerate = null;
                pixelByEntityField = null;
            }

            Object api = apiGetInstance.invoke(null);
            return api != null;
        } catch (Throwable t) {
            plugin.getLogger().warning("LuxGestures API resolve failed: " + t.getMessage());
            return false;
        }
    }

    /**
     * @param skinHint Minecraft username for skin fallback (from spawn key), may be null
     */
    public boolean play(Player viewer, Entity entity, String gestureId, String skinHint) {
        if (!enabled || viewer == null || entity == null || gestureId == null || gestureId.isBlank()) {
            return false;
        }
        String id = gestureId.trim();
        if (id.equalsIgnoreCase("STOP") || id.regionMatches(true, 0, "STOP:", 0, 5)) {
            stop(entity, viewer);
            return true;
        }

        // Replace any previous gesture on this NPC.
        if (active.containsKey(entity.getUniqueId())) {
            stop(entity, viewer);
        }

        try {
            Object api = apiGetInstance.invoke(null);
            Object gm = apiGetGestureManager.invoke(api);
            Object gesture = gmGetGesture.invoke(gm, id);
            if (gesture == null) {
                plugin.getLogger().warning("LuxGestures: unknown gesture '" + id + "'");
                return false;
            }

            Map<String, Object> textures = new HashMap<>();
            textures.put("HEAD", resolveHeadTexture(entity, skinHint, viewer));

            SavedDisguise saved = stashDisguise(viewer, entity);
            var hmc = plugin.getHmcCosmeticsHook();
            if (hmc != null && hmc.isEnabled()) {
                hmc.suspendForGesture(entity, viewer);
            }

            // Hide cinematic NPC body from viewer while Lux draws on a clean host.
            viewer.hideEntity(plugin, entity);
            if (hmc != null && hmc.isEnabled()) {
                hmc.suspendForGesture(entity, viewer);
            }

            ArmorStand host = spawnGestureHost(entity.getLocation());
            if (host == null) {
                restoreAfterFailure(viewer, entity, saved);
                return false;
            }
            viewer.showEntity(plugin, host);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                    other.hideEntity(plugin, host);
                }
            }

            // Must run before getOrAddEntityGesture — Lux copies PixelSkin cache onto the model then.
            Object headTw = textures.get("HEAD");
            injectPixelSkinForHost(host.getUniqueId(), headTw);

            Object model = gmGetOrAddEntity.invoke(gm, host, textures, gesture);
            if (model != null) {
                restrictToViewer(model, viewer);
            }

            // Keep host invisible — Lux despawn briefly flips LivingEntity visible.
            host.setInvisible(true);

            UUID npcId = entity.getUniqueId();
            int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                GesturePlay play = active.get(npcId);
                if (play == null) return;
                Entity npc = findEntityByUuid(npcId);
                Entity h = play.host();
                boolean luxGone = h == null || !h.isValid() || !hasEntityGesture(h);
                if (luxGone) {
                    if (npc != null) stop(npc, play.viewer());
                    else cleanupOrphan(npcId);
                    return;
                }
                // Follow cinematic NPC (in case timeline moves mid-gesture).
                if (npc != null && npc.isValid()) {
                    h.teleport(npc.getLocation());
                    if (h instanceof LivingEntity livingHost) livingHost.setInvisible(true);
                    if (npc instanceof LivingEntity living) living.setInvisible(true);
                }
            }, 2L, 2L).getTaskId();

            active.put(npcId, new GesturePlay(viewer, host, model, saved, taskId));
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("LuxGestures play failed (" + id + "): " + t.getMessage());
            stop(entity, viewer);
            return false;
        }
    }

    public boolean isPlaying(Entity entity) {
        return entity != null && active.containsKey(entity.getUniqueId());
    }

    /** End gesture and restore LibsDisguise + HMC before move/equip/etc. */
    public void ensureStopped(Entity entity, Player viewer) {
        if (entity != null && isPlaying(entity)) {
            stop(entity, viewer);
        }
    }

    public void stop(Entity entity, Player viewer) {
        if (entity == null) return;
        GesturePlay play = active.remove(entity.getUniqueId());
        if (play != null) {
            cancelWatchdog(play);
        }
        Player v = viewer;
        if (play != null && play.viewer() != null) {
            v = play.viewer();
        }

        Entity host = play == null ? null : play.host();
        Object model = play == null ? null : play.model();

        try {
            if (model != null && modelDespawnAll != null) {
                modelDespawnAll.invoke(model);
            } else if (host != null) {
                forceRemoveEntityGesture(host);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("LuxGestures stop failed: " + t.getMessage());
        }

        if (host != null && host.isValid()) {
            host.remove();
        }

        if (entity.isValid()) {
            if (v != null && v.isOnline()) {
                v.showEntity(plugin, entity);
            }
            if (entity instanceof LivingEntity living) {
                living.setInvisible(true);
            }
            if (play != null && play.disguise() != null) {
                restoreDisguise(entity, play.disguise());
            } else if (play == null) {
                // no-op
            }
            var hmc = plugin.getHmcCosmeticsHook();
            if (hmc != null && hmc.isEnabled()) {
                hmc.resumeAfterGesture(entity, v);
            }
        }
    }

    private void cleanupOrphan(UUID npcId) {
        GesturePlay play = active.remove(npcId);
        if (play == null) return;
        cancelWatchdog(play);
        try {
            if (play.model() != null && modelDespawnAll != null) {
                modelDespawnAll.invoke(play.model());
            } else if (play.host() != null) {
                forceRemoveEntityGesture(play.host());
            }
        } catch (Throwable ignored) {
        }
        if (play.host() != null && play.host().isValid()) {
            play.host().remove();
        }
    }

    private static void cancelWatchdog(GesturePlay play) {
        if (play.watchdogTaskId() > 0) {
            Bukkit.getScheduler().cancelTask(play.watchdogTaskId());
        }
    }

    private boolean hasEntityGesture(Entity host) {
        try {
            Object api = apiGetInstance.invoke(null);
            Object gm = apiGetGestureManager.invoke(api);
            return Boolean.TRUE.equals(gmHasEntity.invoke(gm, host));
        } catch (Throwable t) {
            return false;
        }
    }

    private void forceRemoveEntityGesture(Entity host) {
        try {
            Object api = apiGetInstance.invoke(null);
            Object gm = apiGetGestureManager.invoke(api);
            if (Boolean.TRUE.equals(gmHasEntity.invoke(gm, host))) {
                gmRemoveEntity.invoke(gm, host);
            }
        } catch (Throwable ignored) {
        }
    }

    public void stopAll() {
        for (UUID id : new java.util.ArrayList<>(active.keySet())) {
            GesturePlay play = active.get(id);
            Entity entity = findEntityByUuid(id);
            if (entity != null) {
                stop(entity, play == null ? null : play.viewer());
            } else {
                cleanupOrphan(id);
            }
        }
    }

    public void shutdown() {
        stopAll();
    }

    private void restoreAfterFailure(Player viewer, Entity entity, SavedDisguise saved) {
        if (viewer != null && entity != null && entity.isValid()) {
            viewer.showEntity(plugin, entity);
        }
        if (saved != null && entity != null) {
            restoreDisguise(entity, saved);
        }
        var hmc = plugin.getHmcCosmeticsHook();
        if (hmc != null && hmc.isEnabled() && entity != null) {
            hmc.resumeAfterGesture(entity, viewer);
        }
    }

    /**
     * Seed Lux {@code PixelSkinService.byEntity} so limb renderers use
     * {@code luxgestures:arm_*} item models from item-model-pack (same path as player gestures).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectPixelSkinForHost(UUID hostId, Object headTexture) {
        if (pixelSkinGenerate == null || pixelByEntityField == null || headTexture == null) return;
        try {
            Object api = apiGetInstance.invoke(null);
            Method getPss = api.getClass().getMethod("getPixelSkinService");
            Object pss = getPss.invoke(api);
            if (pss == null) return;

            Method isActive = pss.getClass().getMethod("isActive");
            if (!Boolean.TRUE.equals(isActive.invoke(pss))) {
                plugin.getLogger().warning("LuxGestures item-model-skin inactive — NPC limbs may show as heads.");
                return;
            }

            String url = String.valueOf(textureGetUrl.invoke(headTexture));
            boolean slim = Boolean.TRUE.equals(textureIsSlim.invoke(headTexture));
            String cacheKey = url + "|" + slim;

            Map<?, ?> pixelData = pixelSkinCache.get(cacheKey);
            if (pixelData == null) {
                java.awt.image.BufferedImage image = downloadSkinImage(url);
                if (image == null) {
                    plugin.getLogger().warning("LuxGestures: could not download skin for pixel limbs: " + url);
                    return;
                }
                String namespace = "luxgestures";
                try {
                    Method settings = pss.getClass().getMethod("settings");
                    Object cfg = settings.invoke(pss);
                    Method getNs = cfg.getClass().getMethod("getNamespace");
                    Object ns = getNs.invoke(cfg);
                    if (ns != null && !String.valueOf(ns).isBlank()) namespace = String.valueOf(ns);
                } catch (Throwable ignored) {
                }
                pixelData = (Map<?, ?>) pixelSkinGenerate.invoke(null, image, slim, namespace);
                if (pixelData != null) {
                    pixelSkinCache.put(cacheKey, pixelData);
                }
            }
            if (pixelData == null || pixelData.isEmpty()) return;

            Map byEntity = (Map) pixelByEntityField.get(pss);
            byEntity.put(hostId, pixelData);
        } catch (Throwable t) {
            plugin.getLogger().warning("LuxGestures pixel-skin inject failed: " + t.getMessage());
        }
    }

    private static java.awt.image.BufferedImage downloadSkinImage(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URI(url).toURL().openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.setRequestProperty("User-Agent", "CinematicManager-LuxGesturesHook");
            try (java.io.InputStream in = conn.getInputStream()) {
                return javax.imageio.ImageIO.read(in);
            } finally {
                conn.disconnect();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ArmorStand spawnGestureHost(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().spawn(loc, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(false);
            stand.setSmall(false);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.setCollidable(false);
            stand.setRotation(loc.getYaw(), loc.getPitch());
        });
    }

    private void restrictToViewer(Object model, Player viewer) {
        if (modelDespawnPlayer == null || viewer == null) return;
        try {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                    modelDespawnPlayer.invoke(model, other);
                }
            }
        } catch (Throwable ignored) {
        }
    }

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
            plugin.getLogger().warning("Could not clear disguise for gesture: " + t.getMessage());
            return null;
        }
    }

    private void restoreDisguise(Entity entity, SavedDisguise saved) {
        if (saved == null || saved.disguise() == null) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) return;
        if (!entity.isValid()) return;
        try {
            Player viewer = saved.viewer();
            if (viewer != null && viewer.isOnline()) {
                DisguiseAPI.disguiseToPlayers(entity, saved.disguise(), viewer);
                viewer.showEntity(plugin, entity);
            } else {
                DisguiseAPI.disguiseToAll(entity, saved.disguise());
            }
            if (entity instanceof LivingEntity living) {
                living.setInvisible(true);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not restore disguise after gesture: " + t.getMessage());
        }
    }

    private Object resolveHeadTexture(Entity entity, String skinHint, Player viewer) throws Exception {
        String base64 = extractDisguiseTextureBase64(entity);
        if (base64 != null) {
            return textureFromBase64.invoke(null, base64);
        }

        String name = skinHint;
        if (name == null || name.isBlank()) {
            name = extractDisguiseSkinName(entity);
        }
        if (name == null || name.isBlank()) {
            name = viewer != null ? viewer.getName() : "Steve";
        }

        String key = name.toLowerCase(java.util.Locale.ROOT);
        Object cached = textureCache.get(key);
        if (cached != null) return cached;

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            String raw = extractOnlinePlayerTexture(online);
            if (raw != null) {
                Object tw = textureFromBase64.invoke(null, raw);
                textureCache.put(key, tw);
                return tw;
            }
        }

        Object fallback = textureCtorUrl.newInstance(STEVE_TEXTURE_URL, false);
        textureCache.put(key, fallback);
        return fallback;
    }

    private static String extractDisguiseTextureBase64(Entity entity) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) return null;
        try {
            if (!DisguiseAPI.isDisguised(entity)) return null;
            Disguise disguise = DisguiseAPI.getDisguise(entity);
            if (!(disguise instanceof PlayerDisguise pd)) return null;

            try {
                Method getProfile = pd.getClass().getMethod("getGameProfile");
                Object profile = getProfile.invoke(pd);
                if (profile != null) {
                    Method getProperties = profile.getClass().getMethod("getProperties");
                    Object props = getProperties.invoke(profile);
                    Method get = props.getClass().getMethod("get", Object.class);
                    Object collection = get.invoke(props, "textures");
                    if (collection instanceof Iterable<?> it) {
                        for (Object prop : it) {
                            Method getValue = prop.getClass().getMethod("getValue");
                            Object value = getValue.invoke(prop);
                            if (value instanceof String s && !s.isBlank()) return s;
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String extractDisguiseSkinName(Entity entity) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) return null;
        try {
            if (!DisguiseAPI.isDisguised(entity)) return null;
            Disguise disguise = DisguiseAPI.getDisguise(entity);
            if (!(disguise instanceof PlayerDisguise pd)) return null;
            try {
                Method getSkin = pd.getClass().getMethod("getSkin");
                Object skin = getSkin.invoke(pd);
                if (skin instanceof String s && !s.isBlank()) return s;
            } catch (ReflectiveOperationException ignored) {
            }
            return pd.getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String extractOnlinePlayerTexture(Player player) {
        try {
            Method getProfile = player.getClass().getMethod("getPlayerProfile");
            Object profile = getProfile.invoke(player);
            if (profile == null) return null;
            Method getTextures = profile.getClass().getMethod("getTextures");
            Object textures = getTextures.invoke(profile);
            if (textures == null) return null;
            Method getSkin = textures.getClass().getMethod("getSkin");
            Object url = getSkin.invoke(textures);
            if (url == null) return null;
            String urlStr = String.valueOf(url);
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + urlStr + "\"}}}";
            return java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Entity findEntityByUuid(UUID id) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getUniqueId().equals(id)) return e;
            }
        }
        return null;
    }

    /** Parse skin username from spawn keys like {@code npc:PLAYER:Name:Skin}. */
    public static String skinFromSpawnKey(String spawnKey) {
        if (spawnKey == null || spawnKey.isBlank()) return null;
        String raw = org.bukkit.ChatColor.stripColor(spawnKey).trim();
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("npc:")) {
            String[] split = raw.substring(4).split(":");
            if (split.length >= 3) return split[2];
            if (split.length >= 2) return split[1];
            if (split.length >= 1) return split[0];
        }
        return null;
    }
}
