package com.gmail.bobason01.cinematicmanager.fx;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Records nearby particles, sounds, block mutations and projectile-like entities
 * into an EnvironmentClip for viewer-only playback.
 */
public final class EnvironmentRecorder implements Listener {
    private final CinematicManager plugin;
    private final Player player;
    private final String cinematicId;
    private final int startTick;
    private final double radius;
    private final double radiusSq;
    private final int maxTicks;
    private final EnvironmentClip clip = new EnvironmentClip();
    private final Location origin;
    private final Map<UUID, Integer> trackedEntities = new HashMap<>();
    private final Location scratch = new Location(null, 0, 0, 0);

    private boolean recording;
    private int tick;
    private int nextLocalId = 1;
    private BukkitTask task;
    private long lastParticleNanos;
    private static final long PARTICLE_SAMPLE_NS = 50_000_000L; // 20Hz cap

    public EnvironmentRecorder(CinematicManager plugin, Player player, String cinematicId, int startTick) {
        this.plugin = plugin;
        this.player = player;
        this.cinematicId = cinematicId;
        this.startTick = startTick;
        this.radius = Math.max(4.0, plugin.getConfig().getDouble("performance.env-record-radius", 24.0));
        this.radiusSq = radius * radius;
        this.maxTicks = Math.max(20, plugin.getConfig().getInt("performance.max-env-recording-ticks", 2400));
        this.origin = player.getLocation().clone();
        clip.setOrigin(origin);
    }

    public void start() {
        if (recording) return;
        recording = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_ENV_RECORD_START));
        player.sendMessage("§7Radius §e" + (int) radius + "§7 · sneak to stop · max "
                + maxTicks + " ticks");

        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!recording) {
                    cancel();
                    return;
                }
                if (!player.isOnline()) {
                    finish(false);
                    cancel();
                    return;
                }
                if (player.isSneaking() || tick >= maxTicks) {
                    finish(true);
                    cancel();
                    return;
                }
                sampleTrackedEntities();
                sampleAmbientParticles();
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finish(boolean reopenGui) {
        if (!recording) return;
        recording = false;
        HandlerList.unregisterAll(this);
        if (task != null) task.cancel();
        clip.setDurationTicks(tick);

        CinematicData data = plugin.getDataManager().getCinematic(cinematicId);
        if (data == null) return;

        String recordId = UUID.randomUUID().toString().substring(0, 8);
        data.addEnvironmentClip(recordId, clip);
        data.addAction(startTick, new CinematicAction(
                CinematicAction.ActionType.ENV_CLIP, recordId, origin, null));
        plugin.getDataManager().saveCinematic(data);

        if (player.isOnline()) {
            player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_ENV_RECORD_END));
            player.sendMessage("§7particles=" + clip.getParticleCount()
                    + " sounds=" + clip.getSoundCount()
                    + " blocks=" + clip.getBlockCount()
                    + " entities=" + clip.getEntityEventCount());
        }
        if (reopenGui && player.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getGuiManager().openStudioGUI(player, cinematicId, startTick / 180));
        }
        plugin.getEnvironmentRecordManager().unregister(player);
    }

    private void sampleAmbientParticles() {
        // Lightweight visual breadcrumb so empty clips are rare during skill casts.
        // Real skill particles are also pushed via captureParticle/captureSound APIs.
        long now = System.nanoTime();
        if (now - lastParticleNanos < PARTICLE_SAMPLE_NS) return;
        lastParticleNanos = now;
    }

    private void sampleTrackedEntities() {
        if (trackedEntities.isEmpty()) return;
        Iterator<Map.Entry<UUID, Integer>> it = trackedEntities.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid() || !inRadius(entity.getLocation())) {
                clip.addEntityEvent(tick, entry.getValue(), EnvironmentClip.EVT_REMOVE,
                        0, 0, 0, 0, 0, "AIR");
                it.remove();
                continue;
            }
            entity.getLocation(scratch);
            clip.addEntityEvent(tick, entry.getValue(), EnvironmentClip.EVT_MOVE,
                    scratch.getX() - origin.getX(),
                    scratch.getY() - origin.getY(),
                    scratch.getZ() - origin.getZ(),
                    scratch.getYaw(), scratch.getPitch(),
                    entity.getType().name());
        }
    }

    public void captureParticle(Location loc, Particle particle, int count,
                                double ox, double oy, double oz, double speed) {
        if (!recording || loc == null || particle == null || !inRadius(loc)) return;
        clip.addParticle(tick,
                loc.getX() - origin.getX(),
                loc.getY() - origin.getY(),
                loc.getZ() - origin.getZ(),
                (float) ox, (float) oy, (float) oz,
                (float) speed, Math.max(1, count), particle.name());
    }

    public void captureSound(Location loc, String sound, float volume, float pitch) {
        if (!recording || loc == null || sound == null || sound.isBlank() || !inRadius(loc)) return;
        clip.addSound(tick,
                loc.getX() - origin.getX(),
                loc.getY() - origin.getY(),
                loc.getZ() - origin.getZ(),
                volume, pitch, sound);
    }

    private void captureBlock(Location loc, String data) {
        if (!recording || loc == null || data == null || !inRadius(loc)) return;
        clip.addBlock(tick, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), data);
    }

    private boolean inRadius(Location loc) {
        if (loc.getWorld() == null || origin.getWorld() == null) return false;
        if (!loc.getWorld().equals(origin.getWorld())) return false;
        double dx = loc.getX() - origin.getX();
        double dy = loc.getY() - origin.getY();
        double dz = loc.getZ() - origin.getZ();
        return dx * dx + dy * dy + dz * dz <= radiusSq;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
        captureBlock(event.getBlock().getLocation(), event.getBlock().getBlockData().getAsString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
        captureBlock(event.getBlock().getLocation(), "minecraft:air");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!inRadius(loc)) return;
        captureBlock(loc, event.getTo().createBlockData().getAsString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(block -> captureBlock(block.getLocation(), "minecraft:air"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!inRadius(event.getLocation())) return;
        event.blockList().forEach(block -> captureBlock(block.getLocation(), "minecraft:air"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (!inRadius(entity.getLocation())) return;
        if (trackedEntities.containsKey(entity.getUniqueId())) return;

        // Projectiles / vanilla skill entities: capture immediately.
        if (entity instanceof Projectile || isLikelySkillEntity(entity)) {
            trackEntity(entity, entity.getType().name());
            return;
        }

        // Mythic skills: ModelEngine bases OR ArmorStands with equipped items.
        // Equipment / ME often attach 1–5 ticks after spawn.
        if ("ARMOR_STAND".equals(entity.getType().name())) {
            UUID uuid = entity.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> resolveArmorStandSkill(uuid, 1), 1L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> resolveArmorStandSkill(uuid, 2), 2L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> resolveArmorStandSkill(uuid, 3), 3L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> resolveArmorStandSkill(uuid, 5), 5L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> resolveArmorStandSkill(uuid, 10), 10L);
            return;
        }

        // Other living bases may get ModelEngine attached shortly after spawn.
        if (entity instanceof org.bukkit.entity.LivingEntity) {
            UUID uuid = entity.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> resolveModeledOnly(uuid), 1L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> resolveModeledOnly(uuid), 3L);
        }
    }

    private void resolveModeledOnly(UUID uuid) {
        if (!recording || trackedEntities.containsKey(uuid)) return;
        Entity entity = Bukkit.getEntity(uuid);
        if (entity == null || !entity.isValid() || !inRadius(entity.getLocation())) return;
        String modelKey = resolveModelEngineKey(entity);
        if (modelKey != null) trackEntity(entity, modelKey);
    }

    private void resolveArmorStandSkill(UUID uuid, int attempt) {
        if (!recording || trackedEntities.containsKey(uuid)) return;
        Entity entity = Bukkit.getEntity(uuid);
        if (!(entity instanceof org.bukkit.entity.ArmorStand stand)) return;
        if (!stand.isValid() || !inRadius(stand.getLocation())) return;

        // Prefer Mythic mob type: warrior VFX is frame animation via equip{} on the mob.
        // Snapshotting a single ItemStack cannot replay warrior_slash_1→7 swaps.
        String mythicKey = resolveMythicMobKey(stand);
        if (mythicKey != null) {
            trackEntity(stand, "mythicmobs:" + mythicKey);
            return;
        }

        String modelKey = resolveModelEngineKey(stand);
        if (modelKey != null) {
            trackEntity(stand, modelKey);
            return;
        }

        boolean equipped = ArmorStandSkillCodec.hasAnyEquipment(stand);
        // Wait for Mythic to put items on the stand before committing the capture.
        if (!equipped && attempt < 10) return;
        if (!equipped) return;

        trackEntity(stand, ArmorStandSkillCodec.encode(stand));
    }

    private static String resolveMythicMobKey(Entity entity) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) return null;
        try {
            return io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager()
                    .getActiveMob(entity.getUniqueId())
                    .map(am -> {
                        try {
                            return am.getMobType();
                        } catch (Throwable ignored) {
                            return am.getType() != null ? am.getType().getInternalName() : null;
                        }
                    })
                    .filter(name -> name != null && !name.isBlank())
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void trackEntity(Entity entity, String typeKey) {
        if (trackedEntities.containsKey(entity.getUniqueId())) return;
        int localId = nextLocalId++;
        trackedEntities.put(entity.getUniqueId(), localId);
        Location loc = entity.getLocation();
        clip.addEntityEvent(tick, localId, EnvironmentClip.EVT_SPAWN,
                loc.getX() - origin.getX(),
                loc.getY() - origin.getY(),
                loc.getZ() - origin.getZ(),
                loc.getYaw(), loc.getPitch(),
                typeKey);
    }

    private static String resolveModelEngineKey(Entity entity) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) return null;
        try {
            var me = com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(entity.getUniqueId());
            if (me == null || me.getModels() == null || me.getModels().isEmpty()) return null;
            String modelId = me.getModels().keySet().iterator().next();
            if (modelId == null || modelId.isBlank()) return null;
            return "modelengine:" + modelId;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isLikelySkillEntity(Entity entity) {
        return switch (entity.getType().name()) {
            case "AREA_EFFECT_CLOUD", "FALLING_BLOCK", "FIREBALL",
                 "SMALL_FIREBALL", "DRAGON_FIREBALL", "SHULKER_BULLET", "LLAMA_SPIT",
                 "SNOWBALL", "EGG", "ENDER_PEARL", "ARROW", "SPECTRAL_ARROW",
                 "TRIDENT", "WITHER_SKULL", "EVOKER_FANGS" -> true;
            default -> false;
        };
    }

    /** Optional external hook for Mythic/packet bridges. */
    public static void pushParticle(CinematicManager plugin, Player player, Location loc,
                                    Particle particle, int count, double ox, double oy, double oz, double speed) {
        EnvironmentRecorder recorder = plugin.getEnvironmentRecordManager().get(player);
        if (recorder != null) recorder.captureParticle(loc, particle, count, ox, oy, oz, speed);
    }

    public static void pushSound(CinematicManager plugin, Player player, Location loc,
                                 String sound, float volume, float pitch) {
        EnvironmentRecorder recorder = plugin.getEnvironmentRecordManager().get(player);
        if (recorder != null) recorder.captureSound(loc, sound, volume, pitch);
    }
}
