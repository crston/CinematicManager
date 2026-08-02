package com.gmail.bobason01.cinematicmanager.fx;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.EulerAngle;

/**
 * Packs / restores Mythic-style ArmorStand skill visuals (equipment + flags + pose).
 * Stored in EnvironmentClip entityType as: {@code as1|inv=1|HEAD=STONE#c=12|...}
 */
public final class ArmorStandSkillCodec {
    public static final String PREFIX = "as1|";

    private ArmorStandSkillCodec() {}

    public static boolean isEncoded(String type) {
        return type != null && type.startsWith(PREFIX);
    }

    public static boolean hasAnyEquipment(ArmorStand stand) {
        EntityEquipment eq = stand.getEquipment();
        if (eq == null) return false;
        return !isEmpty(eq.getItemInMainHand())
                || !isEmpty(eq.getItemInOffHand())
                || !isEmpty(eq.getHelmet())
                || !isEmpty(eq.getChestplate())
                || !isEmpty(eq.getLeggings())
                || !isEmpty(eq.getBoots());
    }

    public static boolean looksLikeSkillStand(ArmorStand stand) {
        return stand.isInvisible()
                || stand.isMarker()
                || !stand.hasGravity()
                || stand.hasArms()
                || hasAnyEquipment(stand);
    }

    public static String encode(ArmorStand stand) {
        StringBuilder out = new StringBuilder(PREFIX);
        out.append("inv=").append(stand.isInvisible() ? 1 : 0).append('|');
        out.append("small=").append(stand.isSmall() ? 1 : 0).append('|');
        out.append("marker=").append(stand.isMarker() ? 1 : 0).append('|');
        out.append("arms=").append(stand.hasArms() ? 1 : 0).append('|');
        out.append("base=").append(stand.hasBasePlate() ? 1 : 0).append('|');
        out.append("grav=").append(stand.hasGravity() ? 1 : 0).append('|');
        out.append("glow=").append(stand.isGlowing() ? 1 : 0).append('|');
        appendPose(out, "hp", stand.getHeadPose());
        appendPose(out, "bp", stand.getBodyPose());
        appendPose(out, "la", stand.getLeftArmPose());
        appendPose(out, "ra", stand.getRightArmPose());
        appendPose(out, "ll", stand.getLeftLegPose());
        appendPose(out, "rl", stand.getRightLegPose());

        EntityEquipment eq = stand.getEquipment();
        if (eq != null) {
            appendItem(out, "HEAD", eq.getHelmet());
            appendItem(out, "CHEST", eq.getChestplate());
            appendItem(out, "LEGS", eq.getLeggings());
            appendItem(out, "FEET", eq.getBoots());
            appendItem(out, "HAND", eq.getItemInMainHand());
            appendItem(out, "OFF", eq.getItemInOffHand());
        }
        return out.toString();
    }

    public static void apply(ArmorStand stand, String encoded) {
        if (!isEncoded(encoded)) return;
        String[] parts = encoded.split("\\|");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq);
            String val = part.substring(eq + 1);
            switch (key) {
                case "inv" -> stand.setInvisible(val.equals("1"));
                case "small" -> stand.setSmall(val.equals("1"));
                case "marker" -> stand.setMarker(val.equals("1"));
                case "arms" -> stand.setArms(val.equals("1"));
                case "base" -> stand.setBasePlate(val.equals("1"));
                case "grav" -> stand.setGravity(val.equals("1"));
                case "glow" -> stand.setGlowing(val.equals("1"));
                case "hp" -> stand.setHeadPose(parsePose(val));
                case "bp" -> stand.setBodyPose(parsePose(val));
                case "la" -> stand.setLeftArmPose(parsePose(val));
                case "ra" -> stand.setRightArmPose(parsePose(val));
                case "ll" -> stand.setLeftLegPose(parsePose(val));
                case "rl" -> stand.setRightLegPose(parsePose(val));
                case "HEAD" -> setSlot(stand, EquipmentSlot.HEAD, decodeItem(val));
                case "CHEST" -> setSlot(stand, EquipmentSlot.CHEST, decodeItem(val));
                case "LEGS" -> setSlot(stand, EquipmentSlot.LEGS, decodeItem(val));
                case "FEET" -> setSlot(stand, EquipmentSlot.FEET, decodeItem(val));
                case "HAND" -> setSlot(stand, EquipmentSlot.HAND, decodeItem(val));
                case "OFF" -> setSlot(stand, EquipmentSlot.OFF_HAND, decodeItem(val));
            }
        }
        stand.setInvulnerable(true);
        stand.setPersistent(false);
        stand.setCollidable(false);
    }

    private static void setSlot(ArmorStand stand, EquipmentSlot slot, ItemStack item) {
        EntityEquipment eq = stand.getEquipment();
        if (eq == null) return;
        eq.setItem(slot, item);
    }

    private static void appendPose(StringBuilder out, String key, EulerAngle pose) {
        if (pose == null) return;
        out.append(key).append('=')
                .append(fmt(pose.getX())).append(',')
                .append(fmt(pose.getY())).append(',')
                .append(fmt(pose.getZ())).append('|');
    }

    private static EulerAngle parsePose(String val) {
        String[] p = val.split(",");
        if (p.length < 3) return new EulerAngle(0, 0, 0);
        try {
            return new EulerAngle(
                    Double.parseDouble(p[0]),
                    Double.parseDouble(p[1]),
                    Double.parseDouble(p[2]));
        } catch (NumberFormatException e) {
            return new EulerAngle(0, 0, 0);
        }
    }

    private static void appendItem(StringBuilder out, String slot, ItemStack item) {
        if (isEmpty(item)) return;
        String enc = com.gmail.bobason01.cinematicmanager.data.NpcEquipment.encodeItem(item);
        if (enc == null) return;
        out.append(slot).append('=').append(enc).append('|');
    }

    private static ItemStack decodeItem(String val) {
        return com.gmail.bobason01.cinematicmanager.data.NpcEquipment.decodeItem(val);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }
}
