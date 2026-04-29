package com.gmail.bobason01.cinematicmanager.listener;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import com.gmail.bobason01.cinematicmanager.manager.LangManager;
import com.gmail.bobason01.cinematicmanager.session.RecordSession;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
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
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        if (title.equals(lang.sanitize(lang.get(LangKey.MENU_MAIN)))) {
            event.setCancelled(true); handleMain(player, event.getSlot());
        } else if (title.startsWith(lang.sanitize(lang.get(LangKey.MENU_LIST)))) {
            event.setCancelled(true); handleList(player, event.getSlot(), item, event.getClick().isRightClick());
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
        else if (slot == 15) {
            player.closeInventory();
            plugin.getChatInputListener().startCreationInput(player);
        }
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
                case 48 -> { player.closeInventory(); plugin.getSessionManager().startSession(player, id); }
                case 49 -> { plugin.getDataManager().saveCinematic(data); player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_SAVE_SUCCESS)); }
                case 50 -> plugin.getSessionManager().stopSession(player);
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
            case 15 -> { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "COMMAND", t); }
            case 16 -> {
                CinematicData d = plugin.getDataManager().getCinematic(id);
                d.addAction(t, new CinematicAction(CinematicAction.ActionType.LIGHTNING, "lightning", player.getLocation(), null));
                plugin.getDataManager().saveCinematic(d);
                player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_LIGHTNING_ADDED));
                plugin.getGuiManager().openStudioGUI(player, id, p);
            }
            case 22 -> plugin.getGuiManager().openStudioGUI(player, id, p);
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
            case 22 -> plugin.getGuiManager().openStudioGUI(player, id, p);
        }
    }

    private void handleSpawnType(Player player, int slot) {
        String id = getMeta(player, "edit_id");
        int t = getMetaInt(player, "edit_tick"), p = getMetaInt(player, "edit_page");
        if (slot == 11) plugin.getGuiManager().openNPCTypeGUI(player, id, t, p);
        else if (slot == 13) { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "SPAWN", t, "mythicmobs:"); }
        else if (slot == 15) { player.closeInventory(); plugin.getChatInputListener().startTrackInput(player, id, "SPAWN", t, "modelengine:"); }
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
        if (item.getType() == Material.DARK_OAK_DOOR) { plugin.getGuiManager().openActionSelectGUI(player, id, t, p); return; }
        String target = plugin.getLangManager().sanitize(item.getItemMeta().getLore().get(1).split("»")[1].trim());
        if (mode.equals("MOVE")) { player.closeInventory(); new BukkitRunnable() { @Override public void run() { new RecordSession(plugin, player, id, t, CinematicAction.ActionType.MOVE_NPC, target).start(); } }.runTask(plugin); }
        else if (mode.equals("STATE") || mode.equals("STOP")) { player.closeInventory(); player.setMetadata("edit_npc_target", new FixedMetadataValue(plugin, target)); plugin.getChatInputListener().startTrackInput(player, id, mode, t); }
        else {
            CinematicAction.ActionType ty = mode.contains("HIDE") ? CinematicAction.ActionType.HIDE_ENTITY : (mode.contains("SHOW") ? CinematicAction.ActionType.SHOW_ENTITY : CinematicAction.ActionType.ANIMATION);
            plugin.getDataManager().getCinematic(id).addAction(t, new CinematicAction(ty, target, player.getLocation(), null));
            plugin.getGuiManager().openStudioGUI(player, id, p);
        }
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