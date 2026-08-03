package com.gmail.bobason01.cinematicmanager.manager;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import com.gmail.bobason01.cinematicmanager.data.NpcCosmetics;
import com.gmail.bobason01.cinematicmanager.data.NpcEquipment;
import com.gmail.bobason01.cinematicmanager.hook.HmcCosmeticsHook;
import com.gmail.bobason01.cinematicmanager.data.NpcPreset;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.Locale;

public class GUIManager {

    public static final String NPC_TARGET_KEY = "npc_target";

    private final CinematicManager plugin;
    private final NamespacedKey npcTargetKey;
    private final NamespacedKey hmcSlotKey;
    private final NamespacedKey hmcIdKey;

    public GUIManager(CinematicManager plugin) {
        this.plugin = plugin;
        this.npcTargetKey = new NamespacedKey(plugin, NPC_TARGET_KEY);
        this.hmcSlotKey = new NamespacedKey(plugin, "hmc_slot");
        this.hmcIdKey = new NamespacedKey(plugin, "hmc_id");
    }

    public NamespacedKey getNpcTargetKey() {
        return npcTargetKey;
    }

    public NamespacedKey getHmcSlotKey() { return hmcSlotKey; }
    public NamespacedKey getHmcIdKey() { return hmcIdKey; }

    public void openMainMenu(Player player) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 27, lang.get(LangKey.MENU_MAIN));
        inv.setItem(11, createItem(Material.WRITABLE_BOOK, lang.get(LangKey.MENU_MAIN_LIST), LangKey.MENU_MAIN_LIST_LORE));
        inv.setItem(13, createItem(Material.ARMOR_STAND, lang.get(LangKey.MENU_MAIN_NPC), LangKey.MENU_MAIN_NPC_LORE));
        inv.setItem(15, createItem(Material.EMERALD, lang.get(LangKey.MENU_MAIN_CREATE), LangKey.MENU_MAIN_CREATE_LORE));
        player.openInventory(inv);
    }

    public void openNpcPresetMenu(Player player, int page) {
        openNpcPresetMenu(player, page, false);
    }

    /** @param pickMode true = insert into open cinematic; false = manage library */
    public void openNpcPresetMenu(Player player, int page, boolean pickMode) {
        LangManager lang = plugin.getLangManager();
        String title = pickMode ? lang.get(LangKey.MENU_NPC_PICK) : lang.get(LangKey.MENU_NPC_PRESET);
        Inventory inv = Bukkit.createInventory(null, 54, title);
        List<NpcPreset> presets = new ArrayList<>(plugin.getNpcPresetManager().all());
        presets.sort(Comparator.comparing(NpcPreset::getId));
        int start = page * 45;
        for (int i = 0; i < 45 && start + i < presets.size(); i++) {
            NpcPreset preset = presets.get(start + i);
            String detail = switch (preset.getProvider().toLowerCase(Locale.ROOT)) {
                case "mythicmobs", "modelengine" -> preset.getMobId();
                default -> preset.getName() + (preset.getSkin() == null || preset.getSkin().isBlank()
                        || preset.getSkin().equals(preset.getName()) ? "" : " / " + preset.getSkin());
            };
            ItemStack item = createItem(Material.PLAYER_HEAD,
                    lang.format(LangKey.MENU_NPC_PRESET_NAME, "{id}", preset.getId()),
                    lang.format(LangKey.MENU_NPC_PRESET_INFO,
                            "{provider}", preset.getProvider(),
                            "{detail}", detail),
                    pickMode ? LangKey.MENU_NPC_PICK_LORE : LangKey.MENU_NPC_MANAGE_LORE,
                    LangKey.MENU_NPC_PRESET_EQUIP_LORE,
                    LangKey.MENU_NPC_PRESET_DELETE_LORE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(npcTargetKey, PersistentDataType.STRING, preset.getId());
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
        inv.setItem(45, createItem(Material.ARROW, lang.get(LangKey.MENU_LIST_PREV)));
        inv.setItem(49, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_LIST_BACK)));
        inv.setItem(51, createItem(Material.EMERALD, lang.get(LangKey.MENU_NPC_PRESET_CREATE), LangKey.MENU_NPC_PRESET_CREATE_LORE));
        inv.setItem(53, createItem(Material.ARROW, lang.get(LangKey.MENU_LIST_NEXT)));
        player.setMetadata("npc_preset_page", new FixedMetadataValue(plugin, page));
        player.setMetadata("npc_pick_mode", new FixedMetadataValue(plugin, pickMode));
        player.openInventory(inv);
    }

    public void openNpcCreateTypeGUI(Player player) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 27, lang.get(LangKey.MENU_NPC_CREATE_TYPE));
        inv.setItem(11, createItem(Material.PLAYER_HEAD, lang.get(LangKey.MENU_NPC_CREATE_PLAYER), LangKey.MENU_NPC_CREATE_PLAYER_LORE));
        inv.setItem(13, createItem(Material.ZOMBIE_SPAWN_EGG, lang.get(LangKey.MENU_NPC_CREATE_MM), LangKey.MENU_NPC_CREATE_MM_LORE));
        inv.setItem(15, createItem(Material.ARMOR_STAND, lang.get(LangKey.MENU_NPC_CREATE_ME), LangKey.MENU_NPC_CREATE_ME_LORE));
        inv.setItem(22, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_LIST_BACK)));
        player.openInventory(inv);
    }

    public void openCutsceneList(Player player, int page) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 54, lang.get(LangKey.MENU_LIST));
        List<String> ids = new ArrayList<>(plugin.getDataManager().getIds());
        int start = page * 45;
        for (int i = 0; i < 45 && (start + i) < ids.size(); i++) {
            String id = ids.get(start + i);
            inv.setItem(i, createItem(Material.FILLED_MAP, lang.format(LangKey.MENU_LIST_ID, "{id}", id), LangKey.MENU_LIST_EDIT_LORE, LangKey.MENU_LIST_DELETE_LORE));
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

        for (int i = 0; i < 9; i++) {
            int tick = (page * 9 + i) * 20;
            inv.setItem(i, createItem(Material.CLOCK, lang.format(LangKey.MENU_STUDIO_TIME, "{tick}", String.valueOf(tick)), lang.get(LangKey.MENU_STUDIO_CURRENT)));

            CinematicAction act = data.getActionByTrack(tick, CinematicAction.TrackType.ACTION);
            if (act == null) {
                inv.setItem(i + 9, createItem(Material.WHITE_STAINED_GLASS_PANE, lang.get(LangKey.MENU_STUDIO_ADD_ACTION), LangKey.MENU_STUDIO_ADD_ACTION_LORE));
            } else {
                Material actIcon = switch (act.getType()) {
                    case EQUIP_NPC -> Material.IRON_CHESTPLATE;
                    case SPAWN_NPC -> Material.PLAYER_HEAD;
                    case MOVE_NPC -> Material.MINECART;
                    case HIDE_ENTITY -> Material.ENDER_PEARL;
                    case SHOW_ENTITY -> Material.ENDER_EYE;
                    default -> Material.ARMOR_STAND;
                };
                inv.setItem(i + 9, createItem(actIcon,
                        lang.format(LangKey.MENU_STUDIO_ACTION_NAME, "{type}", act.getType().name()),
                        lang.format(LangKey.MENU_STUDIO_ACTION_TARGET, "{target}",
                                act.getExtra() != null ? act.getExtra() : act.getValue()),
                        LangKey.MENU_STUDIO_DELETE_LORE));
            }

            CinematicAction cam = data.getActionByTrack(tick, CinematicAction.TrackType.CAMERA);
            if (cam == null) inv.setItem(i + 18, createItem(Material.YELLOW_STAINED_GLASS_PANE, lang.get(LangKey.MENU_STUDIO_ADD_CAMERA), LangKey.MENU_STUDIO_ADD_CAMERA_LORE));
            else inv.setItem(i + 18, createItem(Material.ENDER_EYE, lang.format(LangKey.MENU_STUDIO_CAMERA_NAME, "{type}", cam.getType().name()), LangKey.MENU_STUDIO_DELETE_LORE));

            CinematicAction eff = data.getActionByTrack(tick, CinematicAction.TrackType.EFFECT);
            if (eff == null) inv.setItem(i + 27, createItem(Material.PINK_STAINED_GLASS_PANE, lang.get(LangKey.MENU_STUDIO_ADD_EFFECT), LangKey.MENU_STUDIO_ADD_EFFECT_LORE));
            else {
                Material icon;
                switch (eff.getType()) {
                    case COMMAND -> icon = Material.COMMAND_BLOCK;
                    case LIGHTNING -> icon = Material.LIGHTNING_ROD;
                    case MESSAGE -> icon = Material.PAPER;
                    case TITLE -> icon = Material.NAME_TAG;
                    case DIALOGUE -> icon = Material.BOOK;
                    case WAIT -> icon = Material.CLOCK;
                    default -> icon = Material.FIREWORK_STAR;
                }
                inv.setItem(i + 27, createItem(icon, lang.format(LangKey.MENU_STUDIO_EFFECT_NAME, "{type}", eff.getType().name()), lang.format(LangKey.MENU_STUDIO_EFFECT_VALUE, "{value}", eff.getValue()), LangKey.MENU_STUDIO_DELETE_LORE));
            }
        }
        inv.setItem(45, createItem(Material.ARROW, lang.get(LangKey.MENU_STUDIO_PREV)));
        inv.setItem(46, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_STUDIO_BACK)));
        inv.setItem(47, createItem(Material.SPYGLASS, lang.get(LangKey.MENU_STUDIO_ENV_RECORD), LangKey.MENU_STUDIO_ENV_RECORD_LORE));
        inv.setItem(48, createItem(Material.LIME_STAINED_GLASS, lang.get(LangKey.MENU_STUDIO_PLAY)));
        inv.setItem(49, createItem(Material.BOOK, lang.get(LangKey.MENU_STUDIO_SAVE)));
        inv.setItem(50, createItem(Material.RED_STAINED_GLASS, lang.get(LangKey.MENU_STUDIO_STOP)));
        inv.setItem(52, createItem(Material.ARMOR_STAND, lang.get(LangKey.MENU_STUDIO_NPC_LIBRARY), LangKey.MENU_STUDIO_NPC_LIBRARY_LORE));
        inv.setItem(53, createItem(Material.ARROW, lang.get(LangKey.MENU_STUDIO_NEXT)));

        player.setMetadata("edit_id", new FixedMetadataValue(plugin, id));
        player.setMetadata("edit_page", new FixedMetadataValue(plugin, page));
        player.openInventory(inv);
    }

    public void openActionSelectGUI(Player player, String id, int tick, int page) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 27, lang.get(LangKey.MENU_ACTION));
        player.setMetadata("edit_tick", new FixedMetadataValue(plugin, tick));
        inv.setItem(9, createItem(Material.ZOMBIE_HEAD, lang.get(LangKey.MENU_ACTION_SPAWN), LangKey.MENU_ACTION_SPAWN_LORE));
        inv.setItem(11, createItem(Material.MINECART, lang.get(LangKey.MENU_ACTION_MOVE), LangKey.MENU_ACTION_MOVE_LORE));
        inv.setItem(13, createItem(Material.GOLDEN_SWORD, lang.get(LangKey.MENU_ACTION_ANIMATION), LangKey.MENU_ACTION_ANIMATION_LORE));
        inv.setItem(15, createItem(Material.ENDER_PEARL, lang.get(LangKey.MENU_ACTION_HIDE), LangKey.MENU_ACTION_HIDE_LORE));
        inv.setItem(17, createItem(Material.ENDER_EYE, lang.get(LangKey.MENU_ACTION_SHOW), LangKey.MENU_ACTION_SHOW_LORE));
        inv.setItem(19, createItem(Material.IRON_CHESTPLATE, lang.get(LangKey.MENU_ACTION_EQUIP), LangKey.MENU_ACTION_EQUIP_LORE));
        inv.setItem(22, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_ACTION_BACK)));
        player.openInventory(inv);
    }

    public void openSpawnTypeGUI(Player player, String id, int tick, int page) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 27, lang.get(LangKey.MENU_SPAWN_TYPE));
        inv.setItem(10, createItem(Material.BOOKSHELF, lang.get(LangKey.MENU_SPAWN_TYPE_LIBRARY), LangKey.MENU_SPAWN_TYPE_LIBRARY_LORE));
        inv.setItem(12, createItem(Material.PLAYER_HEAD, lang.get(LangKey.MENU_SPAWN_TYPE_NPC)));
        inv.setItem(14, createItem(Material.ZOMBIE_SPAWN_EGG, lang.get(LangKey.MENU_SPAWN_TYPE_MM)));
        inv.setItem(16, createItem(Material.ARMOR_STAND, lang.get(LangKey.MENU_SPAWN_TYPE_ME)));
        inv.setItem(22, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_ACTION_BACK)));
        player.openInventory(inv);
    }

    public void openNPCTypeGUI(Player player, String id, int tick, int page) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 27, lang.get(LangKey.MENU_NPC_TYPE));
        inv.setItem(10, createItem(Material.PLAYER_HEAD, lang.get(LangKey.MENU_NPC_TYPE_PLAYER)));
        inv.setItem(11, createItem(Material.ZOMBIE_HEAD, lang.get(LangKey.MENU_NPC_TYPE_ZOMBIE)));
        inv.setItem(12, createItem(Material.PIG_SPAWN_EGG, lang.get(LangKey.MENU_NPC_TYPE_PIG)));
        inv.setItem(13, createItem(Material.SKELETON_SKULL, lang.get(LangKey.MENU_NPC_TYPE_SKELETON)));
        inv.setItem(16, createItem(Material.NAME_TAG, lang.get(LangKey.MENU_NPC_TYPE_CUSTOM), LangKey.MENU_NPC_TYPE_CUSTOM_LORE));
        inv.setItem(22, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_ACTION_BACK)));
        player.openInventory(inv);
    }

    public void openAnimationSelectGUI(Player player, String id, int tick, int page) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 36, lang.get(LangKey.MENU_ANIMATION));
        inv.setItem(10, createItem(Material.BLAZE_ROD, lang.get(LangKey.MENU_ANIMATION_SPIN), LangKey.MENU_ANIMATION_SPIN_LORE));
        inv.setItem(11, createItem(Material.LEATHER_BOOTS, lang.get(LangKey.MENU_ANIMATION_SPRINT), LangKey.MENU_ANIMATION_SPRINT_LORE));
        inv.setItem(12, createItem(Material.PRISMARINE_SHARD, lang.get(LangKey.MENU_ANIMATION_SWIM), LangKey.MENU_ANIMATION_SWIM_LORE));
        inv.setItem(13, createItem(Material.SLIME_BALL, lang.get(LangKey.MENU_ANIMATION_SNEAK), LangKey.MENU_ANIMATION_SNEAK_LORE));
        inv.setItem(14, createItem(Material.WHITE_BED, lang.get(LangKey.MENU_ANIMATION_SLEEP), LangKey.MENU_ANIMATION_SLEEP_LORE));
        inv.setItem(20, createItem(Material.COMMAND_BLOCK, lang.get(LangKey.MENU_ANIMATION_CUSTOM), LangKey.MENU_ANIMATION_CUSTOM_LORE));
        inv.setItem(21, createItem(Material.ANVIL, lang.get(LangKey.MENU_ANIMATION_REMAP), LangKey.MENU_ANIMATION_REMAP_LORE));
        inv.setItem(22, createItem(Material.ARMOR_STAND, lang.get(LangKey.MENU_ANIMATION_CHANGEPART), LangKey.MENU_ANIMATION_CHANGEPART_LORE));
        inv.setItem(24, createItem(Material.BARRIER, lang.get(LangKey.MENU_ANIMATION_STOP), LangKey.MENU_ANIMATION_STOP_LORE));
        inv.setItem(31, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_ACTION_BACK)));
        player.openInventory(inv);
    }

    public void openToggleGUI(Player player, String type) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 27, lang.format(LangKey.MENU_TOGGLE_TITLE, "{type}", type));
        inv.setItem(11, createItem(Material.LIME_WOOL, lang.get(LangKey.MENU_TOGGLE_ON_NAME), LangKey.MENU_TOGGLE_ON_LORE));
        inv.setItem(15, createItem(Material.RED_WOOL, lang.get(LangKey.MENU_TOGGLE_OFF_NAME), LangKey.MENU_TOGGLE_OFF_LORE));
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
            String display = clean.contains(":") ? clean.split(":", 2)[1] : clean;
            ItemStack head = createItem(
                    Material.ZOMBIE_HEAD,
                    lang.format(LangKey.MENU_NPC_NAME, "{name}", display),
                    LangKey.MENU_NPC_SELECT_LORE,
                    lang.format(LangKey.MENU_NPC_ID_LORE, "{id}", clean)
            );
            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                // lore 줄 번호에 의존하지 않도록 실제 spawn value를 PDC에 저장
                meta.getPersistentDataContainer().set(npcTargetKey, PersistentDataType.STRING, npc);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }
        inv.setItem(49, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_NPC_BACK)));
        player.setMetadata("edit_mode", new FixedMetadataValue(plugin, mode));
        player.openInventory(inv);
    }

    public void openEffectSelectGUI(Player player, String id, int tick, int page) {
        LangManager lang = plugin.getLangManager();
        Inventory inv = Bukkit.createInventory(null, 36, lang.get(LangKey.MENU_STUDIO_ADD_EFFECT));
        player.setMetadata("edit_tick", new FixedMetadataValue(plugin, tick));
        inv.setItem(10, createItem(Material.JUKEBOX, lang.get(LangKey.MENU_EFFECT_SOUND), LangKey.MENU_EFFECT_SOUND_LORE));
        inv.setItem(11, createItem(Material.BLAZE_POWDER, lang.get(LangKey.MENU_EFFECT_PARTICLE), LangKey.MENU_EFFECT_PARTICLE_LORE));
        inv.setItem(12, createItem(Material.NAME_TAG, lang.get(LangKey.MENU_EFFECT_TITLE), LangKey.MENU_EFFECT_TITLE_LORE));
        inv.setItem(13, createItem(Material.PAPER, lang.get(LangKey.MENU_EFFECT_MESSAGE), LangKey.MENU_EFFECT_MESSAGE_LORE));
        inv.setItem(14, createItem(Material.BOOK, lang.get(LangKey.MENU_EFFECT_DIALOGUE_TITLE), LangKey.MENU_EFFECT_DIALOGUE_TITLE_LORE));
        inv.setItem(15, createItem(Material.ENCHANTED_BOOK, lang.get(LangKey.MENU_EFFECT_DIALOGUE_ACTIONBAR), LangKey.MENU_EFFECT_DIALOGUE_ACTIONBAR_LORE));
        inv.setItem(16, createItem(Material.CLOCK, lang.get(LangKey.MENU_EFFECT_WAIT), LangKey.MENU_EFFECT_WAIT_LORE));
        inv.setItem(19, createItem(Material.COMMAND_BLOCK, lang.get(LangKey.MENU_EFFECT_COMMAND), LangKey.MENU_EFFECT_COMMAND_LORE));
        inv.setItem(20, createItem(Material.LIGHTNING_ROD, lang.get(LangKey.MENU_EFFECT_LIGHTNING), LangKey.MENU_EFFECT_LIGHTNING_LORE));
        inv.setItem(21, createItem(Material.SPYGLASS, lang.get(LangKey.MENU_EFFECT_ENV_RECORD), LangKey.MENU_EFFECT_ENV_RECORD_LORE));
        inv.setItem(31, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_STUDIO_BACK)));
        player.openInventory(inv);
    }


    /** Slot map for equip GUI: HEAD=4 OFF=12 CHEST=13 HAND=14 LEGS=22 FEET=31 DONE=30 CLEAR=32 BACK=35 */
    public static final int[] EQUIP_SLOTS = {4, 13, 22, 31, 14, 12};

    public void openNpcEquipGUI(Player player, String presetId) {
        openNpcEquipGUI(player, presetId, null, null, -1);
    }

    /**
     * @param actionNpcTarget when non-null, Save writes an EQUIP_NPC timeline action instead of preset.
     */
    public void openNpcEquipGUI(Player player, String presetId, String actionNpcTarget,
                                  String cinematicId, int tick) {
        LangManager lang = plugin.getLangManager();
        NpcEquipment equipment;
        String titleId;
        if (actionNpcTarget != null) {
            if (player.hasMetadata("equip_working")) {
                String enc = null;
                for (org.bukkit.metadata.MetadataValue v : player.getMetadata("equip_working")) {
                    if (v.getOwningPlugin() != null && v.getOwningPlugin().equals(plugin)) {
                        enc = v.asString();
                        break;
                    }
                }
                equipment = NpcEquipment.parse(enc);
            } else {
                equipment = new NpcEquipment();
            }
            titleId = "action";
            player.setMetadata("equip_action_target", new FixedMetadataValue(plugin, actionNpcTarget));
            if (cinematicId != null) player.setMetadata("edit_id", new FixedMetadataValue(plugin, cinematicId));
            if (tick >= 0) player.setMetadata("edit_tick", new FixedMetadataValue(plugin, tick));
            player.removeMetadata("equip_preset_id", plugin);
        } else {
            NpcPreset preset = plugin.getNpcPresetManager().getPreset(presetId);
            if (preset == null) return;
            // Prefer in-progress edits over saved preset gear.
            if (player.hasMetadata("equip_working")) {
                String enc = null;
                for (org.bukkit.metadata.MetadataValue v : player.getMetadata("equip_working")) {
                    if (v.getOwningPlugin() != null && v.getOwningPlugin().equals(plugin)) {
                        enc = v.asString();
                        break;
                    }
                }
                equipment = NpcEquipment.parse(enc);
            } else {
                equipment = preset.getEquipment() == null ? new NpcEquipment() : preset.getEquipment();
            }
            titleId = presetId;
            player.setMetadata("equip_preset_id", new FixedMetadataValue(plugin, presetId));
            player.removeMetadata("equip_action_target", plugin);
        }

        Inventory inv = Bukkit.createInventory(null, 36, lang.format(LangKey.MENU_NPC_EQUIP, "{id}", titleId));
        fillEquipPane(inv);
        putEquipSlot(inv, 4, NpcEquipment.Slot.HEAD, equipment, lang);
        putEquipSlot(inv, 13, NpcEquipment.Slot.CHEST, equipment, lang);
        putEquipSlot(inv, 22, NpcEquipment.Slot.LEGS, equipment, lang);
        putEquipSlot(inv, 31, NpcEquipment.Slot.FEET, equipment, lang);
        putEquipSlot(inv, 14, NpcEquipment.Slot.HAND, equipment, lang);
        putEquipSlot(inv, 12, NpcEquipment.Slot.OFF, equipment, lang);
        inv.setItem(30, createItem(Material.LIME_CONCRETE, lang.get(LangKey.MENU_NPC_EQUIP_DONE)));
        inv.setItem(32, createItem(Material.RED_CONCRETE, lang.get(LangKey.MENU_NPC_EQUIP_CLEAR), LangKey.MENU_NPC_EQUIP_HINT));
        HmcCosmeticsHook hmc = plugin.getHmcCosmeticsHook();
        if (hmc != null && hmc.isEnabled()) {
            inv.setItem(33, createItem(Material.AMETHYST_SHARD, lang.get(LangKey.MENU_NPC_EQUIP_COSMETICS), LangKey.MENU_NPC_EQUIP_COSMETICS_LORE));
        }
        inv.setItem(35, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_NPC_BACK)));
        ensureCosmeticsWorking(player, actionNpcTarget != null ? null : titleId);
        player.openInventory(inv);
    }

    /** Seed cosmetics_working once from preset (or empty). Skip if already editing. */
    private void ensureCosmeticsWorking(Player player, String presetId) {
        if (player.hasMetadata("cosmetics_working")) return;
        String enc = "";
        if (presetId != null) {
            NpcPreset preset = plugin.getNpcPresetManager().getPreset(presetId);
            if (preset != null && preset.getCosmetics() != null && !preset.getCosmetics().isEmpty()) {
                String e = preset.getCosmetics().encode();
                if (e != null) enc = e;
            }
        }
        player.setMetadata("cosmetics_working", new FixedMetadataValue(plugin, enc));
    }

    public void openNpcCosmeticsGUI(Player player) {
        HmcCosmeticsHook hmc = plugin.getHmcCosmeticsHook();
        if (hmc == null || !hmc.isEnabled()) return;
        LangManager lang = plugin.getLangManager();
        String titleId = metaString(player, "equip_preset_id");
        ensureCosmeticsWorking(player, titleId);
        if (titleId == null) titleId = "action";
        NpcCosmetics cos = NpcCosmetics.parse(metaString(player, "cosmetics_working"));
        int page = metaInt(player, "cosmetic_slots_page");
        List<String> slots = hmc.slotIds();
        Inventory inv = Bukkit.createInventory(null, 54, lang.format(LangKey.MENU_NPC_COSMETICS, "{id}", titleId));
        ItemStack pane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        int start = page * 45;
        for (int i = 0; i < 45 && start + i < slots.size(); i++) {
            String slotName = slots.get(start + i);
            String id = cos.get(slotName);
            ItemStack icon;
            if (id != null) {
                ItemStack cached = hmc.iconOf(id);
                icon = cached != null ? cached : new ItemStack(Material.AMETHYST_SHARD);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(lang.format(LangKey.MENU_NPC_COSMETIC_FILLED, "{slot}", slotName, "{id}", id));
                    List<String> lore = new ArrayList<>(lang.getList(LangKey.MENU_NPC_COSMETIC_HINT));
                    meta.setLore(lore);
                    try {
                        org.bukkit.enchantments.Enchantment glow =
                                org.bukkit.enchantments.Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
                        if (glow != null) meta.addEnchant(glow, 1, true);
                    } catch (Throwable ignored) {}
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
                    meta.getPersistentDataContainer().set(hmcSlotKey, PersistentDataType.STRING, slotName);
                    icon.setItemMeta(meta);
                }
            } else {
                icon = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, org.bukkit.ChatColor.WHITE + slotName,
                        LangKey.MENU_NPC_COSMETIC_EMPTY, LangKey.MENU_NPC_COSMETIC_HINT);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(hmcSlotKey, PersistentDataType.STRING, slotName);
                    icon.setItemMeta(meta);
                }
            }
            inv.setItem(i, icon);
        }
        if (page > 0) inv.setItem(45, createItem(Material.ARROW, lang.get(LangKey.MENU_LIST_PREV)));
        inv.setItem(49, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_NPC_BACK)));
        if (start + 45 < slots.size()) inv.setItem(53, createItem(Material.ARROW, lang.get(LangKey.MENU_LIST_NEXT)));
        player.openInventory(inv);
    }

    public void openNpcCosmeticPicker(Player player, String slotName, int page) {
        HmcCosmeticsHook hmc = plugin.getHmcCosmeticsHook();
        if (hmc == null || !hmc.isEnabled() || slotName == null) return;
        LangManager lang = plugin.getLangManager();
        player.setMetadata("cosmetic_pick_slot", new FixedMetadataValue(plugin, slotName));
        player.setMetadata("cosmetic_pick_page", new FixedMetadataValue(plugin, page));
        List<String> ids = hmc.cosmeticIdsForSlot(slotName);
        Inventory inv = Bukkit.createInventory(null, 54, lang.format(LangKey.MENU_NPC_COSMETIC_PICK, "{slot}", slotName));
        ItemStack pane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        int start = page * 45;
        if (ids.isEmpty()) {
            inv.setItem(22, createItem(Material.BARRIER, lang.get(LangKey.MENU_NPC_COSMETIC_NONE)));
        } else {
            for (int i = 0; i < 45 && start + i < ids.size(); i++) {
                String id = ids.get(start + i);
                ItemStack icon = hmc.iconOf(id);
                if (icon == null) icon = new ItemStack(Material.AMETHYST_SHARD);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(org.bukkit.ChatColor.WHITE + id);
                    meta.getPersistentDataContainer().set(hmcIdKey, PersistentDataType.STRING, id);
                    meta.getPersistentDataContainer().set(hmcSlotKey, PersistentDataType.STRING, slotName);
                    icon.setItemMeta(meta);
                }
                inv.setItem(i, icon);
            }
        }
        if (page > 0) inv.setItem(45, createItem(Material.ARROW, lang.get(LangKey.MENU_LIST_PREV)));
        inv.setItem(48, createItem(Material.RED_CONCRETE, lang.get(LangKey.MENU_NPC_COSMETIC_CLEAR)));
        inv.setItem(49, createItem(Material.DARK_OAK_DOOR, lang.get(LangKey.MENU_NPC_BACK)));
        if (start + 45 < ids.size()) inv.setItem(53, createItem(Material.ARROW, lang.get(LangKey.MENU_LIST_NEXT)));
        player.openInventory(inv);
    }

    private static String metaString(Player player, String key) {
        if (!player.hasMetadata(key)) return null;
        for (org.bukkit.metadata.MetadataValue v : player.getMetadata(key)) {
            if (v.getOwningPlugin() != null) return v.asString();
        }
        return null;
    }

    private static int metaInt(Player player, String key) {
        if (!player.hasMetadata(key)) return 0;
        for (org.bukkit.metadata.MetadataValue v : player.getMetadata(key)) {
            if (v.getOwningPlugin() != null) return v.asInt();
        }
        return 0;
    }

    private void fillEquipPane(Inventory inv) {
        ItemStack pane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) inv.setItem(i, pane);
    }

    private void putEquipSlot(Inventory inv, int slot, NpcEquipment.Slot eqSlot,
                              NpcEquipment equipment, LangManager lang) {
        ItemStack gear = equipment.getItem(eqSlot);
        String slotName = switch (eqSlot) {
            case HEAD -> lang.get(LangKey.MENU_NPC_EQUIP_SLOT_HEAD);
            case CHEST -> lang.get(LangKey.MENU_NPC_EQUIP_SLOT_CHEST);
            case LEGS -> lang.get(LangKey.MENU_NPC_EQUIP_SLOT_LEGS);
            case FEET -> lang.get(LangKey.MENU_NPC_EQUIP_SLOT_FEET);
            case HAND -> lang.get(LangKey.MENU_NPC_EQUIP_SLOT_HAND);
            case OFF -> lang.get(LangKey.MENU_NPC_EQUIP_SLOT_OFF);
        };
        if (gear == null) {
            // Light gray pane — NOT leather armor (that looked "already equipped").
            inv.setItem(slot, createItem(
                    Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    slotName,
                    LangKey.MENU_NPC_EQUIP_EMPTY,
                    LangKey.MENU_NPC_EQUIP_HINT));
            return;
        }
        ItemStack display = gear.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(lang.get(LangKey.MENU_NPC_EQUIP_FILLED)
                    .replace("{slot}", slotName)
                    .replace("{item}", gear.getType().name()));
            List<String> lore = new ArrayList<>();
            lore.add(lang.format(LangKey.MENU_NPC_EQUIP_FILLED_LORE, "{item}", gear.getType().name()));
            lore.addAll(lang.getList(LangKey.MENU_NPC_EQUIP_HINT));
            meta.setLore(lore);
            try {
                org.bukkit.enchantments.Enchantment glow =
                        org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("unbreaking"));
                if (glow != null) meta.addEnchant(glow, 1, true);
            } catch (Throwable ignored) {
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            display.setItemMeta(meta);
        }
        inv.setItem(slot, display);
    }

    public NpcEquipment.Slot equipSlotAt(int rawSlot) {
        return switch (rawSlot) {
            case 4 -> NpcEquipment.Slot.HEAD;
            case 13 -> NpcEquipment.Slot.CHEST;
            case 22 -> NpcEquipment.Slot.LEGS;
            case 31 -> NpcEquipment.Slot.FEET;
            case 14 -> NpcEquipment.Slot.HAND;
            case 12 -> NpcEquipment.Slot.OFF;
            default -> null;
        };
    }


    private ItemStack createItem(Material material, String name, LangKey... loreKeys) {
        LangManager lang = plugin.getLangManager();
        List<String> lore = new ArrayList<>();
        for (LangKey key : loreKeys) lore.addAll(lang.getList(key));
        return createItem(material, name, lore.toArray());
    }

    private ItemStack createItem(Material material, String name, Object... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> list = new ArrayList<>();
            for (Object o : lore) {
                if (o instanceof LangKey key) {
                    list.addAll(plugin.getLangManager().getList(key));
                } else if (o instanceof List<?> loreList) {
                    for (Object l : loreList) list.add(String.valueOf(l));
                } else if (o != null) {
                    for (String line : String.valueOf(o).split("\n", -1)) list.add(line);
                }
            }
            meta.setLore(list);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_PLACED_ON);
            item.setItemMeta(meta);
        }
        return item;
    }
}