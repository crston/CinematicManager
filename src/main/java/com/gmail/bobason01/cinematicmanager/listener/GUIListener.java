package com.gmail.bobason01.cinematicmanager.listener;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import com.gmail.bobason01.cinematicmanager.data.NpcCosmetics;
import com.gmail.bobason01.cinematicmanager.data.NpcEquipment;
import com.gmail.bobason01.cinematicmanager.data.NpcPreset;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import com.gmail.bobason01.cinematicmanager.manager.LangManager;
import com.gmail.bobason01.cinematicmanager.manager.NpcPresetManager;
import com.gmail.bobason01.cinematicmanager.session.RecordSession;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class GUIListener implements Listener {

    private final CinematicManager plugin;

    public GUIListener(CinematicManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        LangManager lang = plugin.getLangManager();
        if (event.getView() == null) return;
        String title = lang.sanitize(event.getView().getTitle());

        // Equip GUI: top inventory only cancelled; bottom stays free for picking items.
        String equipPrefix = lang.sanitize(lang.get(LangKey.MENU_NPC_EQUIP).split("\\{")[0]);
        if (title.startsWith(equipPrefix)) {
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                event.setCancelled(true);
                handleNpcEquip(player, event);
            }
            return;
        }

        String cosmeticsPrefix = lang.sanitize(lang.get(LangKey.MENU_NPC_COSMETICS).split("\\{")[0]);
        if (title.startsWith(cosmeticsPrefix)) {
            event.setCancelled(true);
            handleNpcCosmetics(player, event);
            return;
        }

        String cosmeticPickPrefix = lang.sanitize(lang.get(LangKey.MENU_NPC_COSMETIC_PICK).split("\\{")[0]);
        if (title.startsWith(cosmeticPickPrefix)) {
            event.setCancelled(true);
            handleNpcCosmeticPick(player, event);
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        if (title.equals(lang.sanitize(lang.get(LangKey.MENU_MAIN)))) {
            event.setCancelled(true); handleMain(player, event.getSlot());
        } else if (title.startsWith(lang.sanitize(lang.get(LangKey.MENU_LIST)))) {
            event.setCancelled(true); handleList(player, event.getSlot(), item, event.getClick().isRightClick());
        } else if (title.equals(lang.sanitize(lang.get(LangKey.MENU_NPC_PRESET)))
                || title.equals(lang.sanitize(lang.get(LangKey.MENU_NPC_PICK)))) {
            event.setCancelled(true); handleNpcPreset(player, event.getSlot(), item,
                    event.getClick().isRightClick(), event.getClick().isShiftClick());
        } else if (title.equals(lang.sanitize(lang.get(LangKey.MENU_NPC_CREATE_TYPE)))) {
            event.setCancelled(true); handleNpcCreateType(player, event.getSlot());
        } else if (title.startsWith(lang.sanitize(lang.get(LangKey.MENU_STUDIO).split("\\{")[0]))) {
            event.setCancelled(true); handleStudio(player, event.getSlot(), item, event.getClick().isRightClick());
        } else if (title.equals(lang.sanitize(lang.get(LangKey.MENU_ACTION)))) {
            event.setCancelled(true); handleAction(player, event.getSlot());
        } else if (title.equals(lang.sanitize(lang.get(LangKey.MENU_SPAWN_TYPE)))) {
            event.setCancelled(true); handleSpawnType(player, event.getSlot());
        } else if (title.equals(lang.sanitize(lang.get(LangKey.MENU_NPC_TYPE)))) {
            event.setCancelled(true); handleNPCType(player, event.getSlot());
        } else if (title.equals(lang.sanitize(lang.get(LangKey.MENU_ANIMATION)))) {
            event.setCancelled(true); handleAnimation(player, event.getSlot());
        } else if (title.startsWith(lang.sanitize(lang.get(LangKey.MENU_TOGGLE_TITLE).split("\\{")[0]))) {
            event.setCancelled(true); handleToggle(player, event.getSlot());
        } else if (title.equals(lang.sanitize(lang.get(LangKey.MENU_NPC)))) {
            event.setCancelled(true); handleNPC(player, item);
        } else if (title.equals(lang.sanitize(lang.get(LangKey.MENU_STUDIO_ADD_EFFECT)))) {
            event.setCancelled(true); handleEffect(player, event.getSlot());
        }
    }

    private void handleMain(Player player, int slot) {
        if (slot == 11) plugin.getGuiManager().openCutsceneList(player, 0);
        else if (slot == 13) plugin.getGuiManager().openNpcPresetMenu(player, 0, false);
        else if (slot == 15) {
            player.closeInventory();
            plugin.getChatInputListener().startCreationInput(player);
        }
    }

    private void handleNpcPreset(Player player, int slot, ItemStack item, boolean right, boolean shift) {
        int page = getMetaInt(player, "npc_preset_page");
        boolean pickMode = "true".equalsIgnoreCase(getMeta(player, "npc_pick_mode"));
        NpcPresetManager presets = plugin.getNpcPresetManager();
        if (presets == null) return;

        if (slot < 45 && item.getType() == Material.PLAYER_HEAD) {
            String presetId = item.getItemMeta() == null ? null
                    : item.getItemMeta().getPersistentDataContainer()
                    .get(plugin.getGuiManager().getNpcTargetKey(), PersistentDataType.STRING);
            if (presetId == null) return;
            if (right) {
                presets.deletePreset(presetId);
                plugin.getGuiManager().openNpcPresetMenu(player, page, pickMode);
                return;
            }
            NpcPreset preset = presets.getPreset(presetId);
            if (preset == null) return;

            if (pickMode) {
                String cinematicId = getMeta(player, "edit_id");
                int insertTick = getMetaInt(player, "edit_tick");
                if (cinematicId == null || cinematicId.isBlank()) {
                    plugin.getGuiManager().openNpcPresetMenu(player, page, false);
                    return;
                }
                CinematicData data = plugin.getDataManager().getCinematic(cinematicId);
                if (data == null) return;
                data.addAction(insertTick, new CinematicAction(
                        CinematicAction.ActionType.SPAWN_NPC,
                        preset.asSpawnValue(),
                        player.getLocation(),
                        preset.asEquipmentExtra()));
                plugin.getDataManager().saveCinematic(data);
                player.removeMetadata("npc_pick_mode", plugin);
                player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_NPC_PRESET_INSERTED));
                plugin.getGuiManager().openStudioGUI(player, cinematicId, getMetaInt(player, "edit_page"));
                return;
            }

            // Manage mode: shift+left = equip, left = edit type/name/skin (or mob id)
            if (shift) {
                player.removeMetadata("equip_working", plugin);
                player.removeMetadata("cosmetics_working", plugin);
                player.removeMetadata("equip_action_target", plugin);
                plugin.getGuiManager().openNpcEquipGUI(player, presetId);
                return;
            }
            player.closeInventory();
            plugin.getChatInputListener().startNpcPresetEdit(player, presetId);
        } else if (slot == 45 && page > 0) {
            plugin.getGuiManager().openNpcPresetMenu(player, page - 1, pickMode);
        } else if (slot == 53) {
            plugin.getGuiManager().openNpcPresetMenu(player, page + 1, pickMode);
        } else if (slot == 49) {
            if (pickMode) {
                String cinematicId = getMeta(player, "edit_id");
                if (cinematicId != null && !cinematicId.isBlank()) {
                    plugin.getGuiManager().openStudioGUI(player, cinematicId, getMetaInt(player, "edit_page"));
                    return;
                }
            }
            player.removeMetadata("npc_pick_mode", plugin);
            plugin.getGuiManager().openMainMenu(player);
        } else if (slot == 51) {
            plugin.getGuiManager().openNpcCreateTypeGUI(player);
        }
    }

    private void handleNpcCreateType(Player player, int slot) {
        boolean pickMode = "true".equalsIgnoreCase(getMeta(player, "npc_pick_mode"));
        if (slot == 22) {
            plugin.getGuiManager().openNpcPresetMenu(player, getMetaInt(player, "npc_preset_page"), pickMode);
            return;
        }
        String type = switch (slot) {
            case 11 -> "NPC_CREATE_PLAYER";
            case 13 -> "NPC_CREATE_MM";
            case 15 -> "NPC_CREATE_ME";
            default -> null;
        };
        if (type == null) return;
        player.closeInventory();
        plugin.getChatInputListener().startTrackInput(player, "_preset_", type, 0);
    }

    private void handleList(Player player, int slot, ItemStack item, boolean right) {
        int page = getMetaInt(player, "gui_page");
        if (slot < 45 && item.getType() == Material.FILLED_MAP) {
            String id = plugin.getLangManager().sanitize(item.getItemMeta().getDisplayName()).replace(plugin.getLangManager().sanitize(plugin.getLangManager().get(LangKey.MENU_LIST_ID).split("\\{")[0]), "").trim();
            if (right) { plugin.getDataManager().deleteCinematic(id); plugin.getGuiManager().openCutsceneList(player, page); }
            else plugin.getGuiManager().openStudioGUI(player, id, 0);
        } else if (slot == 45 && page > 0) plugin.getGuiManager().openCutsceneList(player, page - 1);
        else if (slot == 53) plugin.getGuiManager().openCutsceneList(player, page + 1);
        else if (slot == 49) plugin.getGuiManager().openMainMenu(player);
    }

    private void handleStudio(Player player, int slot, ItemStack item, boolean right) {
        String id = getMeta(player, "edit_id");
        int page = getMetaInt(player, "edit_page");
        int tick = (page * 9 + (slot % 9)) * 20;
        CinematicData data = plugin.getDataManager().getCinematic(id);
        if (data == null) return;

        if (slot >= 9 && slot <= 17) {
            if (right) { data.removeActionsByTrack(tick, CinematicAction.TrackType.ACTION); plugin.getGuiManager().openStudioGUI(player, id, page); }
            else plugin.getGuiManager().openActionSelectGUI(player, id, tick, page);
        } else if (slot >= 18 && slot <= 26) {
            if (right) { data.removeActionsByTrack(tick, CinematicAction.TrackType.CAMERA); plugin.getGuiManager().openStudioGUI(player, id, page); }
            else { player.closeInventory(); sendCamBtns(player, id, tick); }
        } else if (slot >= 27 && slot <= 35) {
            if (right) { data.removeActionsByTrack(tick, CinematicAction.TrackType.EFFECT); plugin.getGuiManager().openStudioGUI(player, id, page); }
            else plugin.getGuiManager().openEffectSelectGUI(player, id, tick, page);
        } else {
            switch (slot) {
                case 45 -> { if (page > 0) plugin.getGuiManager().openStudioGUI(player, id, page - 1); }
                case 46 -> plugin.getGuiManager().openCutsceneList(player, 0);
                case 47 -> {
                    player.closeInventory();
                    plugin.getEnvironmentRecordManager().start(player, id, page * 180);
                }
                case 48 -> { player.closeInventory(); plugin.getSessionManager().startSession(player, id); }
                case 49 -> { plugin.getDataManager().saveCinematic(data); player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_SAVE_SUCCESS)); }
                case 50 -> plugin.getSessionManager().stopSession(player);
                case 52 -> {
                    player.setMetadata("edit_tick", new FixedMetadataValue(plugin, page * 180));
                    plugin.getGuiManager().openNpcPresetMenu(player, 0, true);
                }
                case 53 -> plugin.getGuiManager().openStudioGUI(player, id, page + 1);
            }
        }
    }

    private void handleEffect(Player player, int slot) {
        String id = getMeta(player, "edit_id");
        int t = getMetaInt(player, "edit_tick"), p = getMetaInt(player, "edit_page");
        switch (slot) {
            case 10 -> { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "SOUND", t); }
            case 11 -> { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "PARTICLE", t); }
            case 12 -> { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "TITLE", t); }
            case 13 -> { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "MESSAGE", t); }
            case 14 -> { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "DIALOGUE_TITLE", t); }
            case 15 -> { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "DIALOGUE_ACTIONBAR", t); }
            case 16 -> { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "WAIT", t); }
            case 19 -> { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "COMMAND", t); }
            case 20 -> {
                CinematicData d = plugin.getDataManager().getCinematic(id);
                d.addAction(t, new CinematicAction(CinematicAction.ActionType.LIGHTNING, "lightning", player.getLocation(), null));
                plugin.getDataManager().saveCinematic(d);
                player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_LIGHTNING_ADDED));
                plugin.getGuiManager().openStudioGUI(player, id, p);
            }
            case 21 -> {
                player.closeInventory();
                plugin.getEnvironmentRecordManager().start(player, id, t);
            }
            case 31 -> plugin.getGuiManager().openStudioGUI(player, id, p);
        }
    }

    private void handleAction(Player player, int slot) {
        String id = getMeta(player, "edit_id");
        int t = getMetaInt(player, "edit_tick"), p = getMetaInt(player, "edit_page");
        switch (slot) {
            case 9 -> plugin.getGuiManager().openSpawnTypeGUI(player, id, t, p);
            case 11 -> { player.setMetadata("edit_mode", new FixedMetadataValue(plugin, "MOVE")); plugin.getGuiManager().openNPCListGUI(player, id, t, p, "MOVE"); }
            case 13 -> plugin.getGuiManager().openAnimationSelectGUI(player, id, t, p);
            case 15 -> { player.setMetadata("edit_mode", new FixedMetadataValue(plugin, "HIDE")); plugin.getGuiManager().openNPCListGUI(player, id, t, p, "HIDE"); }
            case 17 -> { player.setMetadata("edit_mode", new FixedMetadataValue(plugin, "SHOW")); plugin.getGuiManager().openNPCListGUI(player, id, t, p, "SHOW"); }
            case 19 -> { player.setMetadata("edit_mode", new FixedMetadataValue(plugin, "EQUIP")); plugin.getGuiManager().openNPCListGUI(player, id, t, p, "EQUIP"); }
            case 22 -> plugin.getGuiManager().openStudioGUI(player, id, p);
        }
    }

    private void handleSpawnType(Player player, int slot) {
        String id = getMeta(player, "edit_id");
        int t = getMetaInt(player, "edit_tick"), p = getMetaInt(player, "edit_page");
        if (slot == 10) {
            if (plugin.getNpcPresetManager().all().isEmpty()) {
                player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_NPC_LIBRARY_EMPTY));
            }
            plugin.getGuiManager().openNpcPresetMenu(player, 0, true);
        } else if (slot == 12) plugin.getGuiManager().openNPCTypeGUI(player, id, t, p);
        else if (slot == 14) { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "SPAWN", t, "mythicmobs:"); }
        else if (slot == 16) { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "SPAWN", t, "modelengine:"); }
        else if (slot == 22) plugin.getGuiManager().openActionSelectGUI(player, id, t, p);
    }

    private void handleNPCType(Player player, int slot) {
        String id = getMeta(player, "edit_id");
        int t = getMetaInt(player, "edit_tick"), p = getMetaInt(player, "edit_page");
        if (slot == 22) { plugin.getGuiManager().openSpawnTypeGUI(player, id, t, p); return; }
        if (slot == 16) { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "CUSTOM_TYPE", t, "npc:"); return; }
        String type = switch (slot) { case 10 -> "PLAYER"; case 11 -> "ZOMBIE"; case 12 -> "PIG"; case 13 -> "SKELETON"; default -> null; };
        if (type != null) { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "SPAWN", t, "npc:" + type + ":"); }
    }

    private void handleAnimation(Player player, int slot) {
        String id = getMeta(player, "edit_id");
        int t = getMetaInt(player, "edit_tick"), p = getMetaInt(player, "edit_page");
        switch (slot) {
            case 10 -> plugin.getGuiManager().openToggleGUI(player, "SPIN");
            case 11 -> plugin.getGuiManager().openToggleGUI(player, "SPRINT");
            case 12 -> plugin.getGuiManager().openToggleGUI(player, "SWIM");
            case 13 -> plugin.getGuiManager().openToggleGUI(player, "SNEAK");
            case 14 -> plugin.getGuiManager().openToggleGUI(player, "SLEEP");
            case 20 -> plugin.getGuiManager().openNPCListGUI(player, id, t, p, "STATE");
            case 21 -> plugin.getGuiManager().openNPCListGUI(player, id, t, p, "REMAP");
            case 22 -> plugin.getGuiManager().openNPCListGUI(player, id, t, p, "CHANGEPART");
            case 24 -> plugin.getGuiManager().openNPCListGUI(player, id, t, p, "STOP");
            case 31 -> plugin.getGuiManager().openActionSelectGUI(player, id, t, p);
        }
    }

    private void handleToggle(Player player, int slot) {
        String ty = getMeta(player, "pending_toggle"), id = getMeta(player, "edit_id");
        int t = getMetaInt(player, "edit_tick"), p = getMetaInt(player, "edit_page");
        if (slot == 11) plugin.getGuiManager().openNPCListGUI(player, id, t, p, ty + "_ON");
        else if (slot == 15) plugin.getGuiManager().openNPCListGUI(player, id, t, p, ty + "_OFF");
        else if (slot == 22) plugin.getGuiManager().openAnimationSelectGUI(player, id, t, p);
    }

    private void handleNPC(Player player, ItemStack item) {
        String id = getMeta(player, "edit_id"), mode = getMeta(player, "edit_mode");
        int t = getMetaInt(player, "edit_tick"), p = getMetaInt(player, "edit_page");
        if (item.getType() == Material.DARK_OAK_DOOR) {
            plugin.getGuiManager().openActionSelectGUI(player, id, t, p);
            return;
        }

        String target = resolveNpcTarget(item);
        if (target == null || target.isBlank()) {
            return;
        }

        if (mode.equals("MOVE")) {
            player.closeInventory();
            new BukkitRunnable() {
                @Override
                public void run() {
                    new RecordSession(plugin, player, id, t, CinematicAction.ActionType.MOVE_NPC, target).start();
                }
            }.runTask(plugin);
        } else if (mode.equals("EQUIP")) {
            player.removeMetadata("equip_working", plugin);
            player.removeMetadata("cosmetics_working", plugin);
            player.removeMetadata("equip_preset_id", plugin);
            plugin.getGuiManager().openNpcEquipGUI(player, null, target, id, t);
        } else if (mode.equals("STATE") || mode.equals("STOP")
                || mode.equals("REMAP") || mode.equals("CHANGEPART")) {
            player.closeInventory();
            player.setMetadata("edit_npc_target", new FixedMetadataValue(plugin, target));
            plugin.getChatInputListener().startTrackInput(player, id, mode, t);
        } else {
            CinematicAction.ActionType ty = mode.contains("HIDE")
                    ? CinematicAction.ActionType.HIDE_ENTITY
                    : (mode.contains("SHOW") ? CinematicAction.ActionType.SHOW_ENTITY : CinematicAction.ActionType.ANIMATION);
            // Toggle modes historically stored NPC id in value; keep that for spin/sprint etc.
            if (ty == CinematicAction.ActionType.ANIMATION && (mode.contains("_ON") || mode.contains("_OFF"))) {
                plugin.getDataManager().getCinematic(id).addAction(t,
                        new CinematicAction(ty, mode, player.getLocation(), target));
            } else {
                plugin.getDataManager().getCinematic(id).addAction(t, new CinematicAction(ty, target, player.getLocation(), null));
            }
            plugin.getGuiManager().openStudioGUI(player, id, p);
        }
    }


    private void handleNpcEquip(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == 35) {
            finishEquipBack(player);
            return;
        }
        if (slot == 30) {
            saveEquip(player);
            return;
        }
        if (slot == 32) {
            NpcEquipment eq = currentEquipWorking(player);
            eq.clearAll();
            storeEquipWorking(player, eq);
            player.setMetadata("cosmetics_working", new FixedMetadataValue(plugin, ""));
            reopenEquip(player);
            return;
        }
        if (slot == 33) {
            var hmc = plugin.getHmcCosmeticsHook();
            if (hmc != null && hmc.isEnabled()) {
                player.setMetadata("cosmetic_slots_page", new FixedMetadataValue(plugin, 0));
                plugin.getGuiManager().openNpcCosmeticsGUI(player);
            }
            return;
        }
        NpcEquipment.Slot eqSlot = plugin.getGuiManager().equipSlotAt(slot);
        if (eqSlot == null) return;

        NpcEquipment eq = currentEquipWorking(player);
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR && cursor.getAmount() > 0) {
            // Copy only — do not consume the player's item.
            eq.setItem(eqSlot, cursor);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(plugin.getLangManager().format(
                            LangKey.MSG_NPC_EQUIP_SET, "{item}", cursor.getType().name())));
        } else {
            eq.clear(eqSlot);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(plugin.getLangManager().get(LangKey.MSG_NPC_EQUIP_CLEARED)));
        }
        storeEquipWorking(player, eq);
        reopenEquip(player);
    }

    private NpcEquipment currentEquipWorking(Player player) {
        String encoded = getMeta(player, "equip_working");
        if (encoded != null) return NpcEquipment.parse(encoded);
        String presetId = getMeta(player, "equip_preset_id");
        if (presetId != null) {
            NpcPreset preset = plugin.getNpcPresetManager().getPreset(presetId);
            if (preset != null && preset.getEquipment() != null) {
                return NpcEquipment.parse(preset.getEquipment().encode());
            }
        }
        return new NpcEquipment();
    }

    private void storeEquipWorking(Player player, NpcEquipment eq) {
        String enc = eq.encode();
        player.setMetadata("equip_working", new FixedMetadataValue(plugin, enc == null ? "" : enc));
    }

    private void reopenEquip(Player player) {
        String presetId = getMeta(player, "equip_preset_id");
        String actionTarget = getMeta(player, "equip_action_target");
        NpcEquipment working = currentEquipWorking(player);
        storeEquipWorking(player, working);
        if (presetId != null) {
            plugin.getGuiManager().openNpcEquipGUI(player, presetId);
            return;
        }
        if (actionTarget != null) {
            String id = getMeta(player, "edit_id");
            int tick = getMetaInt(player, "edit_tick");
            plugin.getGuiManager().openNpcEquipGUI(player, null, actionTarget, id, tick);
        }
    }

    private void saveEquip(Player player) {
        NpcEquipment eq = currentEquipWorking(player);
        NpcCosmetics cos = currentCosmeticsWorking(player);
        String presetId = getMeta(player, "equip_preset_id");
        String actionTarget = getMeta(player, "equip_action_target");
        if (presetId != null) {
            NpcPreset preset = plugin.getNpcPresetManager().getPreset(presetId);
            if (preset != null) {
                preset.setEquipment(eq);
                preset.setCosmetics(cos);
                plugin.getNpcPresetManager().save(preset);
                player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_NPC_EQUIP_SAVED));
            }
            clearEquipMeta(player);
            plugin.getGuiManager().openNpcPresetMenu(player, getMetaInt(player, "npc_preset_page"), false);
            return;
        }
        if (actionTarget != null) {
            String id = getMeta(player, "edit_id");
            int tick = getMetaInt(player, "edit_tick");
            int page = getMetaInt(player, "edit_page");
            CinematicData data = plugin.getDataManager().getCinematic(id);
            if (data != null) {
                String encoded = NpcCosmetics.mergeExtra(eq.encode(), cos);
                if (encoded == null) encoded = "";
                data.addAction(tick, new CinematicAction(
                        CinematicAction.ActionType.EQUIP_NPC, encoded, player.getLocation(), actionTarget));
                plugin.getDataManager().saveCinematic(data);
                player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_NPC_EQUIP_ADDED));
            }
            clearEquipMeta(player);
            plugin.getGuiManager().openStudioGUI(player, id, page);
        }
    }

    private void finishEquipBack(Player player) {
        String presetId = getMeta(player, "equip_preset_id");
        clearEquipMeta(player);
        if (presetId != null) {
            plugin.getGuiManager().openNpcPresetMenu(player, getMetaInt(player, "npc_preset_page"), false);
            return;
        }
        String id = getMeta(player, "edit_id");
        plugin.getGuiManager().openStudioGUI(player, id, getMetaInt(player, "edit_page"));
    }

    private void clearEquipMeta(Player player) {
        player.removeMetadata("equip_working", plugin);
        player.removeMetadata("cosmetics_working", plugin);
        player.removeMetadata("equip_preset_id", plugin);
        player.removeMetadata("equip_action_target", plugin);
        player.removeMetadata("cosmetic_pick_slot", plugin);
        player.removeMetadata("cosmetic_pick_page", plugin);
        player.removeMetadata("cosmetic_slots_page", plugin);
    }

    private NpcCosmetics currentCosmeticsWorking(Player player) {
        String encoded = getMeta(player, "cosmetics_working");
        if (encoded != null) return NpcCosmetics.parse(encoded);
        String presetId = getMeta(player, "equip_preset_id");
        if (presetId != null) {
            NpcPreset preset = plugin.getNpcPresetManager().getPreset(presetId);
            if (preset != null && preset.getCosmetics() != null) {
                return NpcCosmetics.parse(preset.getCosmetics().encode());
            }
        }
        return new NpcCosmetics();
    }

    private void storeCosmeticsWorking(Player player, NpcCosmetics cos) {
        String enc = cos == null || cos.isEmpty() ? "" : cos.encode();
        player.setMetadata("cosmetics_working", new FixedMetadataValue(plugin, enc == null ? "" : enc));
    }

    private void handleNpcCosmetics(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == 49) {
            reopenEquip(player);
            return;
        }
        if (slot == 45) {
            int page = Math.max(0, getMetaInt(player, "cosmetic_slots_page") - 1);
            player.setMetadata("cosmetic_slots_page", new FixedMetadataValue(plugin, page));
            plugin.getGuiManager().openNpcCosmeticsGUI(player);
            return;
        }
        if (slot == 53) {
            int page = getMetaInt(player, "cosmetic_slots_page") + 1;
            player.setMetadata("cosmetic_slots_page", new FixedMetadataValue(plugin, page));
            plugin.getGuiManager().openNpcCosmeticsGUI(player);
            return;
        }
        if (slot < 0 || slot >= 45) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String hmcSlot = clicked.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().getHmcSlotKey(), PersistentDataType.STRING);
        if (hmcSlot == null || hmcSlot.isBlank()) return;
        if (event.getClick().isRightClick()) {
            NpcCosmetics cos = currentCosmeticsWorking(player);
            cos.clear(hmcSlot);
            storeCosmeticsWorking(player, cos);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(plugin.getLangManager().get(LangKey.MSG_NPC_COSMETIC_CLEARED)));
            plugin.getGuiManager().openNpcCosmeticsGUI(player);
            return;
        }
        plugin.getGuiManager().openNpcCosmeticPicker(player, hmcSlot, 0);
    }

    private void handleNpcCosmeticPick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        String hmcSlot = getMeta(player, "cosmetic_pick_slot");
        if (slot == 49) {
            plugin.getGuiManager().openNpcCosmeticsGUI(player);
            return;
        }
        if (slot == 48) {
            if (hmcSlot != null) {
                NpcCosmetics cos = currentCosmeticsWorking(player);
                cos.clear(hmcSlot);
                storeCosmeticsWorking(player, cos);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(plugin.getLangManager().get(LangKey.MSG_NPC_COSMETIC_CLEARED)));
            }
            plugin.getGuiManager().openNpcCosmeticsGUI(player);
            return;
        }
        if (slot == 45) {
            int page = Math.max(0, getMetaInt(player, "cosmetic_pick_page") - 1);
            plugin.getGuiManager().openNpcCosmeticPicker(player, hmcSlot, page);
            return;
        }
        if (slot == 53) {
            int page = getMetaInt(player, "cosmetic_pick_page") + 1;
            plugin.getGuiManager().openNpcCosmeticPicker(player, hmcSlot, page);
            return;
        }
        if (slot < 0 || slot >= 45) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().getHmcIdKey(), PersistentDataType.STRING);
        if (id == null || id.isBlank() || hmcSlot == null) return;
        NpcCosmetics cos = currentCosmeticsWorking(player);
        cos.set(hmcSlot, id);
        storeCosmeticsWorking(player, cos);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(plugin.getLangManager().format(LangKey.MSG_NPC_COSMETIC_SET, "{id}", id)));
        plugin.getGuiManager().openNpcCosmeticsGUI(player);
    }


    private String resolveNpcTarget(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String fromPdc = meta.getPersistentDataContainer().get(
                plugin.getGuiManager().getNpcTargetKey(),
                PersistentDataType.STRING
        );
        if (fromPdc != null && !fromPdc.isBlank()) {
            return fromPdc;
        }

        // 구버전 아이템 폴백: lore 마지막 "»" 줄에서 ID 추출
        if (meta.hasLore() && meta.getLore() != null) {
            for (int i = meta.getLore().size() - 1; i >= 0; i--) {
                String line = plugin.getLangManager().sanitize(meta.getLore().get(i));
                if (line.contains("»")) {
                    String[] parts = line.split("»", 2);
                    if (parts.length > 1) {
                        String candidate = parts[1].trim();
                        if (!candidate.isEmpty()
                                && !candidate.equals("이 개체 선택")
                                && !candidate.equalsIgnoreCase("Select this entity")
                                && !candidate.equals("この個体を選択")) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void sendCamBtns(Player p, String id, int t) {
        TextComponent s = new TextComponent(plugin.getLangManager().get(LangKey.BTN_CAMERA_STATIC));
        s.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cinematic _internal_cam static " + id + " " + t));
        TextComponent r = new TextComponent(plugin.getLangManager().get(LangKey.BTN_CAMERA_RECORD));
        r.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cinematic _internal_cam record " + id + " " + t));
        p.spigot().sendMessage(s, new TextComponent("  "), r);
    }

    private String getMeta(Player p, String k) {
        for (MetadataValue v : p.getMetadata(k)) if (v.getOwningPlugin().equals(plugin)) return v.asString();
        return null;
    }

    private int getMetaInt(Player p, String k) {
        String v = getMeta(p, k); return v != null ? Integer.parseInt(v) : 0;
    }
}