package com.gmail.bobason01.cinematicmanager.fx;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Allocation-conscious environment capture: particles, sounds, private blocks,
 * and fake projectile/entity events relative to an origin.
 */
public final class EnvironmentClip {
    public static final byte EVT_SPAWN = 1;
    public static final byte EVT_MOVE = 2;
    public static final byte EVT_REMOVE = 3;

    private String world;
    private double originX, originY, originZ;
    private float originYaw, originPitch;
    private int durationTicks;

    // Particle packed arrays (relative xyz)
    private int particleCount;
    private int[] particleTick = new int[0];
    private double[] particleX = new double[0];
    private double[] particleY = new double[0];
    private double[] particleZ = new double[0];
    private float[] particleOx = new float[0];
    private float[] particleOy = new float[0];
    private float[] particleOz = new float[0];
    private float[] particleSpeed = new float[0];
    private int[] particleAmount = new int[0];
    private String[] particleName = new String[0];

    // Sound packed arrays
    private int soundCount;
    private int[] soundTick = new int[0];
    private double[] soundX = new double[0];
    private double[] soundY = new double[0];
    private double[] soundZ = new double[0];
    private float[] soundVolume = new float[0];
    private float[] soundPitch = new float[0];
    private String[] soundName = new String[0];

    // Block changes (absolute block coords + block data)
    private int blockCount;
    private int[] blockTick = new int[0];
    private int[] blockX = new int[0];
    private int[] blockY = new int[0];
    private int[] blockZ = new int[0];
    private String[] blockData = new String[0];

    // Fake entity events (relative xyz)
    private int entityEventCount;
    private int[] entityTick = new int[0];
    private int[] entityLocalId = new int[0];
    private byte[] entityEvent = new byte[0];
    private double[] entityX = new double[0];
    private double[] entityY = new double[0];
    private double[] entityZ = new double[0];
    private float[] entityYaw = new float[0];
    private float[] entityPitch = new float[0];
    private String[] entityType = new String[0];

    public void setOrigin(Location origin) {
        if (origin == null) return;
        this.world = origin.getWorld() != null ? origin.getWorld().getName() : "world";
        this.originX = origin.getX();
        this.originY = origin.getY();
        this.originZ = origin.getZ();
        this.originYaw = origin.getYaw();
        this.originPitch = origin.getPitch();
    }

    public Location getOrigin() {
        World w = Bukkit.getWorld(world == null ? "world" : world);
        return new Location(w, originX, originY, originZ, originYaw, originPitch);
    }

    public int getDurationTicks() { return durationTicks; }
    public void setDurationTicks(int durationTicks) { this.durationTicks = Math.max(0, durationTicks); }

    public int getParticleCount() { return particleCount; }
    public int getSoundCount() { return soundCount; }
    public int getBlockCount() { return blockCount; }
    public int getEntityEventCount() { return entityEventCount; }

    public void addParticle(int tick, double x, double y, double z,
                            float ox, float oy, float oz, float speed, int amount, String name) {
        ensureParticle(particleCount + 1);
        int i = particleCount++;
        particleTick[i] = tick;
        particleX[i] = x; particleY[i] = y; particleZ[i] = z;
        particleOx[i] = ox; particleOy[i] = oy; particleOz[i] = oz;
        particleSpeed[i] = speed;
        particleAmount[i] = amount;
        particleName[i] = name;
    }

    public void addSound(int tick, double x, double y, double z, float volume, float pitch, String name) {
        ensureSound(soundCount + 1);
        int i = soundCount++;
        soundTick[i] = tick;
        soundX[i] = x; soundY[i] = y; soundZ[i] = z;
        soundVolume[i] = volume;
        soundPitch[i] = pitch;
        soundName[i] = name;
    }

    public void addBlock(int tick, int x, int y, int z, String data) {
        ensureBlock(blockCount + 1);
        int i = blockCount++;
        blockTick[i] = tick;
        blockX[i] = x; blockY[i] = y; blockZ[i] = z;
        blockData[i] = data;
    }

    public void addEntityEvent(int tick, int localId, byte event, double x, double y, double z,
                               float yaw, float pitch, String type) {
        ensureEntity(entityEventCount + 1);
        int i = entityEventCount++;
        entityTick[i] = tick;
        entityLocalId[i] = localId;
        entityEvent[i] = event;
        entityX[i] = x; entityY[i] = y; entityZ[i] = z;
        entityYaw[i] = yaw; entityPitch[i] = pitch;
        entityType[i] = type;
    }

    // --- hot-path getters for playback ---
    public int particleTick(int i) { return particleTick[i]; }
    public double particleX(int i) { return particleX[i]; }
    public double particleY(int i) { return particleY[i]; }
    public double particleZ(int i) { return particleZ[i]; }
    public float particleOx(int i) { return particleOx[i]; }
    public float particleOy(int i) { return particleOy[i]; }
    public float particleOz(int i) { return particleOz[i]; }
    public float particleSpeed(int i) { return particleSpeed[i]; }
    public int particleAmount(int i) { return particleAmount[i]; }
    public String particleName(int i) { return particleName[i]; }

    public int soundTick(int i) { return soundTick[i]; }
    public double soundX(int i) { return soundX[i]; }
    public double soundY(int i) { return soundY[i]; }
    public double soundZ(int i) { return soundZ[i]; }
    public float soundVolume(int i) { return soundVolume[i]; }
    public float soundPitch(int i) { return soundPitch[i]; }
    public String soundName(int i) { return soundName[i]; }

    public int blockTick(int i) { return blockTick[i]; }
    public int blockX(int i) { return blockX[i]; }
    public int blockY(int i) { return blockY[i]; }
    public int blockZ(int i) { return blockZ[i]; }
    public String blockData(int i) { return blockData[i]; }

    public int entityTick(int i) { return entityTick[i]; }
    public int entityLocalId(int i) { return entityLocalId[i]; }
    public byte entityEvent(int i) { return entityEvent[i]; }
    public double entityX(int i) { return entityX[i]; }
    public double entityY(int i) { return entityY[i]; }
    public double entityZ(int i) { return entityZ[i]; }
    public float entityYaw(int i) { return entityYaw[i]; }
    public float entityPitch(int i) { return entityPitch[i]; }
    public String entityType(int i) { return entityType[i]; }

    public void serialize(YamlConfiguration yaml, String path) {
        yaml.set(path + ".world", world);
        yaml.set(path + ".origin.x", originX);
        yaml.set(path + ".origin.y", originY);
        yaml.set(path + ".origin.z", originZ);
        yaml.set(path + ".origin.yaw", originYaw);
        yaml.set(path + ".origin.pitch", originPitch);
        yaml.set(path + ".durationTicks", durationTicks);

        List<String> particles = new ArrayList<>(particleCount);
        for (int i = 0; i < particleCount; i++) {
            particles.add(particleTick[i] + "," + particleX[i] + "," + particleY[i] + "," + particleZ[i]
                    + "," + particleOx[i] + "," + particleOy[i] + "," + particleOz[i]
                    + "," + particleSpeed[i] + "," + particleAmount[i] + "," + particleName[i]);
        }
        yaml.set(path + ".particles", particles);

        List<String> sounds = new ArrayList<>(soundCount);
        for (int i = 0; i < soundCount; i++) {
            sounds.add(soundTick[i] + "," + soundX[i] + "," + soundY[i] + "," + soundZ[i]
                    + "," + soundVolume[i] + "," + soundPitch[i] + "," + soundName[i]);
        }
        yaml.set(path + ".sounds", sounds);

        List<String> blocks = new ArrayList<>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            blocks.add(blockTick[i] + "," + blockX[i] + "," + blockY[i] + "," + blockZ[i] + "," + blockData[i]);
        }
        yaml.set(path + ".blocks", blocks);

        List<String> entities = new ArrayList<>(entityEventCount);
        for (int i = 0; i < entityEventCount; i++) {
            entities.add(entityTick[i] + "," + entityLocalId[i] + "," + entityEvent[i]
                    + "," + entityX[i] + "," + entityY[i] + "," + entityZ[i]
                    + "," + entityYaw[i] + "," + entityPitch[i] + "," + entityType[i]);
        }
        yaml.set(path + ".entities", entities);
    }

    public static EnvironmentClip deserialize(ConfigurationSection section) {
        EnvironmentClip clip = new EnvironmentClip();
        if (section == null) return clip;
        clip.world = section.getString("world", "world");
        ConfigurationSection origin = section.getConfigurationSection("origin");
        if (origin != null) {
            clip.originX = origin.getDouble("x");
            clip.originY = origin.getDouble("y");
            clip.originZ = origin.getDouble("z");
            clip.originYaw = (float) origin.getDouble("yaw");
            clip.originPitch = (float) origin.getDouble("pitch");
        }
        clip.durationTicks = section.getInt("durationTicks", 0);

        for (String line : section.getStringList("particles")) {
            String[] p = line.split(",", 10);
            if (p.length < 10) continue;
            clip.addParticle(Integer.parseInt(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]), Float.parseFloat(p[6]),
                    Float.parseFloat(p[7]), Integer.parseInt(p[8]), p[9]);
        }
        for (String line : section.getStringList("sounds")) {
            String[] p = line.split(",", 7);
            if (p.length < 7) continue;
            clip.addSound(Integer.parseInt(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]), p[6]);
        }
        for (String line : section.getStringList("blocks")) {
            String[] p = line.split(",", 5);
            if (p.length < 5) continue;
            clip.addBlock(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4]);
        }
        for (String line : section.getStringList("entities")) {
            String[] p = line.split(",", 9);
            if (p.length < 9) continue;
            clip.addEntityEvent(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Byte.parseByte(p[2]),
                    Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5]),
                    Float.parseFloat(p[6]), Float.parseFloat(p[7]), p[8]);
        }
        return clip;
    }

    private void ensureParticle(int need) {
        if (need <= particleTick.length) return;
        int cap = grow(particleTick.length, need);
        particleTick = Arrays.copyOf(particleTick, cap);
        particleX = Arrays.copyOf(particleX, cap);
        particleY = Arrays.copyOf(particleY, cap);
        particleZ = Arrays.copyOf(particleZ, cap);
        particleOx = Arrays.copyOf(particleOx, cap);
        particleOy = Arrays.copyOf(particleOy, cap);
        particleOz = Arrays.copyOf(particleOz, cap);
        particleSpeed = Arrays.copyOf(particleSpeed, cap);
        particleAmount = Arrays.copyOf(particleAmount, cap);
        particleName = Arrays.copyOf(particleName, cap);
    }

    private void ensureSound(int need) {
        if (need <= soundTick.length) return;
        int cap = grow(soundTick.length, need);
        soundTick = Arrays.copyOf(soundTick, cap);
        soundX = Arrays.copyOf(soundX, cap);
        soundY = Arrays.copyOf(soundY, cap);
        soundZ = Arrays.copyOf(soundZ, cap);
        soundVolume = Arrays.copyOf(soundVolume, cap);
        soundPitch = Arrays.copyOf(soundPitch, cap);
        soundName = Arrays.copyOf(soundName, cap);
    }

    private void ensureBlock(int need) {
        if (need <= blockTick.length) return;
        int cap = grow(blockTick.length, need);
        blockTick = Arrays.copyOf(blockTick, cap);
        blockX = Arrays.copyOf(blockX, cap);
        blockY = Arrays.copyOf(blockY, cap);
        blockZ = Arrays.copyOf(blockZ, cap);
        blockData = Arrays.copyOf(blockData, cap);
    }

    private void ensureEntity(int need) {
        if (need <= entityTick.length) return;
        int cap = grow(entityTick.length, need);
        entityTick = Arrays.copyOf(entityTick, cap);
        entityLocalId = Arrays.copyOf(entityLocalId, cap);
        entityEvent = Arrays.copyOf(entityEvent, cap);
        entityX = Arrays.copyOf(entityX, cap);
        entityY = Arrays.copyOf(entityY, cap);
        entityZ = Arrays.copyOf(entityZ, cap);
        entityYaw = Arrays.copyOf(entityYaw, cap);
        entityPitch = Arrays.copyOf(entityPitch, cap);
        entityType = Arrays.copyOf(entityType, cap);
    }

    private static int grow(int current, int need) {
        int cap = current == 0 ? 64 : current + (current >> 1);
        return Math.max(cap, need);
    }
}
