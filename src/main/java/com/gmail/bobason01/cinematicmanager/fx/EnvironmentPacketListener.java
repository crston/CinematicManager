package com.gmail.bobason01.cinematicmanager.fx;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.particle.Particle;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.sound.Sound;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.gmail.bobason01.cinematicmanager.CinematicManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Captures outbound particle/sound packets destined for an active recorder.
 * Uses PacketEvents 2.13 Vector accessors (getPosition / getOffset / getMaxSpeed).
 */
public final class EnvironmentPacketListener extends PacketListenerAbstract {
    private final CinematicManager plugin;

    public EnvironmentPacketListener(CinematicManager plugin) {
        super(PacketListenerPriority.MONITOR);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        try {
            // Hot-reload / disable race: PacketEvents may still hold this listener
            // after the plugin JAR classloader is closed.
            if (plugin == null || !plugin.isEnabled()) return;
            EnvironmentRecordManager manager = plugin.getEnvironmentRecordManager();
            if (manager == null || !manager.hasActive()) return;

            User user = event.getUser();
            if (user == null || user.getUUID() == null) return;
            Player player = Bukkit.getPlayer(user.getUUID());
            if (player == null) return;
            EnvironmentRecorder recorder = manager.get(player);
            if (recorder == null) return;

            if (event.getPacketType() == PacketType.Play.Server.PARTICLE) {
                WrapperPlayServerParticle wrapper = new WrapperPlayServerParticle(event);
                Particle<?> particle = wrapper.getParticle();
                if (particle == null) return;
                ParticleType<?> type = particle.getType();
                if (type == null || type.getName() == null) return;
                String name = type.getName().getKey().toUpperCase();

                Vector3d position = wrapper.getPosition();
                Vector3f offset = wrapper.getOffset();
                if (position == null) return;

                Location loc = new Location(player.getWorld(), position.getX(), position.getY(), position.getZ());
                float ox = offset == null ? 0f : offset.getX();
                float oy = offset == null ? 0f : offset.getY();
                float oz = offset == null ? 0f : offset.getZ();
                float speed = wrapper.getMaxSpeed();
                int count = wrapper.getParticleCount();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (!plugin.isEnabled()) return;
                        recorder.captureParticle(loc,
                                org.bukkit.Particle.valueOf(name),
                                count, ox, oy, oz, speed);
                    } catch (IllegalArgumentException ignored) {
                    } catch (IllegalStateException ignored) {
                    }
                });
            } else if (event.getPacketType() == PacketType.Play.Server.SOUND_EFFECT) {
                WrapperPlayServerSoundEffect wrapper = new WrapperPlayServerSoundEffect(event);
                Sound sound = wrapper.getSound();
                ResourceLocation key = sound == null ? null : sound.getSoundId();
                if (key == null) return;

                Vector3d position = wrapper.getPosition();
                if (position == null) return;
                Location loc = new Location(player.getWorld(), position.getX(), position.getY(), position.getZ());
                float volume = wrapper.getVolume();
                float pitch = wrapper.getPitch();
                String name = key.toString();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled()) return;
                    recorder.captureSound(loc, name, volume, pitch);
                });
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
                WrapperPlayServerEntitySoundEffect wrapper = new WrapperPlayServerEntitySoundEffect(event);
                Sound sound = wrapper.getSound();
                ResourceLocation key = sound == null ? null : sound.getSoundId();
                if (key == null) return;

                String name = key.toString();
                float volume = wrapper.getVolume();
                float pitch = wrapper.getPitch();
                Location loc = player.getLocation();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled()) return;
                    recorder.captureSound(loc, name, volume, pitch);
                });
            }
        } catch (Throwable ignored) {
            // Packet shape variance OR closed plugin classloader after reload.
        }
    }
}
