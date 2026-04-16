package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

public class GUIManager {

    private final CinematicManager plugin;

    public GUIManager(CinematicManager plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 27, lang.get(LangKey.MENU_MAIN));
        inv.setItem(11, createItem(Material.WRITABLE_BOOK, lang.get(LangKey.MENU_MAIN_LIST), lang.get(LangKey.MENU_MAIN_LIST_LORE)));
        inv.setItem(15, createItem(Material.EMERALD, lang.get(LangKey.MENU_MAIN_CREATE), lang.get(LangKey.MENU_MAIN_CREATE_LORE)));
        player.openInventory(inv);
    }

    public void openCutsceneList(Player player, int page) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 54, lang.get(LangKey.MENU_LIST));
        List<String> ids = new ArrayList<>(plugin.getDataManager().getIds());
        int start = page * 45;
        for (int i = 0; i < 45 && (start + i) < ids.size(); i++) {
            String id = ids.get(start + i);
            inv.setItem(i, createItem(Material.FILLED_MAP, lang.format(LangKey.MENU_LIST_ID, "{id}", id), lang.get(LangKey.MENU_LIST_EDIT_LORE), lang.get(LangKey.MENU_LIST_DELETE_LORE)));
        }
        inv.setItem(45, createItem(Material.ARROW, lang.get(LangKey.MENU_LIST_PREV)));
        inv.setItem(49, createItem(Material.BARRIER, lang.get(LangKey.MENU_LIST_BACK)));
        inv.setItem(53, createItem(Material.ARROW, lang.get(LangKey.MENU_LIST_NEXT)));
        player.setMetadata("gui_page", new FixedMetadataValue(plugin, page));
        player.openInventory(inv);
    }

    public void openStudioGUI(Player player, String id, int page) {
        LangManager lang = plugin.getLangManager();
        CinematicData data = plugin.getDataManager().getCinematic(id);
        if (data == null) return;
        Inventory inv = Bukkit.createInventory(null, 54, lang.format(LangKey.MENU_STUDIO, "{id}", id));
        int startTick = page * 9 * 20;
        for (int i = 0; i < 9; i++) {
            int tick = startTick + (i * 20);
            inv.setItem(i, createItem(Material.CLOCK, lang.format(LangKey.MENU_STUDIO_TIME, "{tick}", String.valueOf(tick)), lang.get(LangKey.MENU_STUDIO_CURRENT)));
            CinematicAction act = data.getActionByTrack(tick, CinematicAction.TrackType.ACTION);
            if (act == null) inv.setItem(i + 9, createItem(Material.WHITE_STAINED_GLASS_PANE, lang.get(LangKey.MENU_STUDIO_ADD_ACTION), lang.get(LangKey.MENU_STUDIO_ADD_ACTION_LORE)));
            else inv.setItem(i + 9, createItem(Material.ARMOR_STAND, lang.format(LangKey.MENU_STUDIO_ACTION_NAME, "{type}", act.getType().name()), lang.format(LangKey.MENU_STUDIO_ACTION_TARGET, "{target}", act.getValue()), lang.get(LangKey.MENU_STUDIO_DELETE_LORE)));

            CinematicAction cam = data.getActionByTrack(tick, CinematicAction.TrackType.CAMERA);
            if (cam == null) inv.setItem(i + 18, createItem(Material.YELLOW_STAINED_GLASS_PANE, lang.get(LangKey.MENU_STUDIO_ADD_CAMERA), lang.get(LangKey.MENU_STUDIO_ADD_CAMERA_LORE)));
            else inv.setItem(i + 18, createItem(Material.ENDER_EYE, lang.format(LangKey.MENU_STUDIO_CAMERA_NAME, "{type}", cam.getType().name()), lang.get(LangKey.MENU_STUDIO_DELETE_LORE)));

            CinematicAction eff = data.getActionByTrack(tick, CinematicAction.TrackType.EFFECT);
            if (eff == null) inv.setItem(i + 27, createItem(Material.PINK_STAINED_GLASS_PANE, lang.get(LangKey.MENU_STUDIO_ADD_EFFECT), lang.get(LangKey.MENU_STUDIO_ADD_EFFECT_LORE)));
            else inv.setItem(i + 27, createItem(Material.FIREWORK_STAR, lang.format(LangKey.MENU_STUDIO_EFFECT_NAME, "{type}", eff.getType().name()), lang.format(LangKey.MENU_STUDIO_EFFECT_VALUE, "{value}", eff.getValue()), lang.get(LangKey.MENU_STUDIO_DELETE_LORE)));
        }
        inv.setItem(45, createItem(Material.ARROW, lang.get(LangKey.MENU_STUDIO_PREV)));
        inv.setItem(46, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_STUDIO_BACK)));
        inv.setItem(48, createItem(Material.LIME_STAINED_GLASS, lang.get(LangKey.MENU_STUDIO_PLAY)));
        inv.setItem(49, createItem(Material.BOOK, lang.get(LangKey.MENU_STUDIO_SAVE)));
        inv.setItem(50, createItem(Material.RED_STAINED_GLASS, lang.get(LangKey.MENU_STUDIO_STOP)));
        inv.setItem(53, createItem(Material.ARROW, lang.get(LangKey.MENU_STUDIO_NEXT)));
        player.setMetadata("edit_id", new FixedMetadataValue(plugin, id));
        player.setMetadata("edit_page", new FixedMetadataValue(plugin, page));
        player.openInventory(inv);
    }

    public void openActionSelectGUI(Player player, String id, int tick, int page) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 27, lang.get(LangKey.MENU_ACTION));
        inv.setItem(9, createItem(Material.ZOMBIE_HEAD, lang.get(LangKey.MENU_ACTION_SPAWN), lang.get(LangKey.MENU_ACTION_SPAWN_LORE)));
        inv.setItem(11, createItem(Material.MINECART, lang.get(LangKey.MENU_ACTION_MOVE), lang.get(LangKey.MENU_ACTION_MOVE_LORE)));
        inv.setItem(13, createItem(Material.GOLDEN_SWORD, lang.get(LangKey.MENU_ACTION_ANIMATION), lang.get(LangKey.MENU_ACTION_ANIMATION_LORE)));
        inv.setItem(15, createItem(Material.ENDER_PEARL, lang.get(LangKey.MENU_ACTION_HIDE), lang.get(LangKey.MENU_ACTION_HIDE_LORE)));
        inv.setItem(17, createItem(Material.ENDER_EYE, lang.get(LangKey.MENU_ACTION_SHOW), lang.get(LangKey.MENU_ACTION_SHOW_LORE)));
        inv.setItem(22, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_ACTION_BACK)));
        player.setMetadata("edit_tick", new FixedMetadataValue(plugin, tick));
        player.openInventory(inv);
    }

    public void openAnimationSelectGUI(Player player, String id, int tick, int page) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 36, lang.get(LangKey.MENU_ANIMATION));
        inv.setItem(10, createItem(Material.BLAZE_ROD, lang.get(LangKey.MENU_ANIMATION_SPIN), lang.get(LangKey.MENU_ANIMATION_SPIN_LORE)));
        inv.setItem(11, createItem(Material.LEATHER_BOOTS, lang.get(LangKey.MENU_ANIMATION_SPRINT), lang.get(LangKey.MENU_ANIMATION_SPRINT_LORE)));
        inv.setItem(12, createItem(Material.PRISMARINE_SHARD, lang.get(LangKey.MENU_ANIMATION_SWIM), lang.get(LangKey.MENU_ANIMATION_SWIM_LORE)));
        inv.setItem(13, createItem(Material.SLIME_BALL, lang.get(LangKey.MENU_ANIMATION_SNEAK), lang.get(LangKey.MENU_ANIMATION_SNEAK_LORE)));
        inv.setItem(14, createItem(Material.WHITE_BED, lang.get(LangKey.MENU_ANIMATION_SLEEP), lang.get(LangKey.MENU_ANIMATION_SLEEP_LORE)));
        // Scale 아이템(slot 16) 제거됨
        inv.setItem(20, createItem(Material.COMMAND_BLOCK, lang.get(LangKey.MENU_ANIMATION_CUSTOM), lang.get(LangKey.MENU_ANIMATION_CUSTOM_LORE)));
        inv.setItem(24, createItem(Material.BARRIER, lang.get(LangKey.MENU_ANIMATION_STOP), lang.get(LangKey.MENU_ANIMATION_STOP_LORE)));
        inv.setItem(31, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_ACTION_BACK)));
        player.openInventory(inv);
    }

    public void openToggleGUI(Player player, String type) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 27, lang.format(LangKey.MENU_TOGGLE_TITLE, "{type}", type));

        inv.setItem(11, createItem(Material.LIME_WOOL, lang.get(LangKey.MENU_TOGGLE_ON_NAME), lang.get(LangKey.MENU_TOGGLE_ON_LORE)));
        inv.setItem(15, createItem(Material.RED_WOOL, lang.get(LangKey.MENU_TOGGLE_OFF_NAME), lang.get(LangKey.MENU_TOGGLE_OFF_LORE)));
        inv.setItem(22, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_ACTION_BACK)));

        player.setMetadata("pending_toggle", new FixedMetadataValue(plugin, type));
        player.openInventory(inv);
    }

    public void openNPCListGUI(Player player, String id, int tick, int page, String mode) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 54, lang.get(LangKey.MENU_NPC));
        CinematicData data = plugin.getDataManager().getCinematic(id);
        if (data == null) return;
        Set<String> npcSet = new LinkedHashSet<>();
        for (List<CinematicAction> actions : data.getTimeline().values()) {
            for (CinematicAction action : actions) if (action.getType() == CinematicAction.ActionType.SPAWN_NPC) npcSet.add(action.getValue());
        }
        int slot = 0;
        for (String npc : npcSet) {
            if (slot >= 45) break;
            String clean = lang.sanitize(npc);
            inv.setItem(slot++, createItem(Material.ZOMBIE_HEAD, lang.format(LangKey.MENU_NPC_NAME, "{name}", clean.contains(":") ? clean.split(":")[1] : clean), lang.get(LangKey.MENU_NPC_SELECT_LORE), lang.format(LangKey.MENU_NPC_ID_LORE, "{id}", clean)));
        }
        inv.setItem(49, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_NPC_BACK)));
        player.setMetadata("edit_mode", new FixedMetadataValue(plugin, mode));
        player.openInventory(inv);
    }

    public void openEffectSelectGUI(Player player, String id, int tick, int page) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 27, lang.get(LangKey.MENU_EFFECT));
        inv.setItem(10, createItem(Material.JUKEBOX, lang.get(LangKey.MENU_EFFECT_SOUND), lang.get(LangKey.MENU_EFFECT_SOUND_LORE)));
        inv.setItem(12, createItem(Material.BLAZE_POWDER, lang.get(LangKey.MENU_EFFECT_PARTICLE), lang.get(LangKey.MENU_EFFECT_PARTICLE_LORE)));
        inv.setItem(14, createItem(Material.NAME_TAG, lang.get(LangKey.MENU_EFFECT_TITLE), lang.get(LangKey.MENU_EFFECT_TITLE_LORE)));
        inv.setItem(16, createItem(Material.PAPER, lang.get(LangKey.MENU_EFFECT_MESSAGE), lang.get(LangKey.MENU_EFFECT_MESSAGE_LORE)));
        inv.setItem(22, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_EFFECT_BACK)));
        player.openInventory(inv);
    }

    private ItemStack createItem(Material material, String name, Object... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> list = new ArrayList<>();
            for (Object o : lore) {
                if (o instanceof List) for (Object l : (List<?>) o) list.add(String.valueOf(l));
                else if (o != null) list.add(String.valueOf(o));
            }
            meta.setLore(list);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_PLACED_ON);
            item.setItemMeta(meta);
        }
        return item;
    }
}