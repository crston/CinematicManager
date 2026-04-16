package com.gmail.bobason01.cinematicmanager.command;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import com.gmail.bobason01.cinematicmanager.session.RecordSession;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CinematicCommand implements CommandExecutor, TabCompleter {

    private final CinematicManager plugin;
    private final List<String> subCommands;

    public CinematicCommand(CinematicManager plugin) {
        this.plugin = plugin;
        this.subCommands = Arrays.asList("create", "edit", "play", "stop", "list", "reload");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                plugin.getGuiManager().openMainMenu((Player) sender);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "_internal_cam": {
                if (!(sender instanceof Player) || args.length < 4) return true;
                Player player = (Player) sender;
                String camType = args[1];
                String id = args[2];
                int tick = Integer.parseInt(args[3]);
                CinematicData data = plugin.getDataManager().getCinematic(id);

                if (data == null) return true;

                if (camType.equals("static")) {
                    data.addAction(tick, new CinematicAction(CinematicAction.ActionType.CAMERA, "static", player.getLocation(), null));
                    plugin.getDataManager().saveCinematic(data);
                    player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_CAMERA_STATIC_SET));
                    plugin.getGuiManager().openStudioGUI(player, id, tick / 180);
                } else if (camType.equals("record")) {
                    RecordSession recordSession = new RecordSession(plugin, player, id, tick, CinematicAction.ActionType.CAMERA, null);
                    recordSession.start();
                }
                return true;
            }

            case "create": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                if (args.length < 2) {
                    player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_INPUT_NAME));
                    return true;
                }
                String id = args[1];
                plugin.getDataManager().createCinematic(id);

                CinematicData newData = plugin.getDataManager().getCinematic(id);
                if (newData != null) plugin.getDataManager().saveCinematic(newData);

                player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_CREATE_SUCCESS));
                plugin.getGuiManager().openStudioGUI(player, id, 0);
                return true;
            }

            case "edit": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                if (args.length < 2) {
                    player.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_INPUT_NAME));
                    return true;
                }
                plugin.getGuiManager().openStudioGUI(player, args[1], 0);
                return true;
            }

            case "play": {
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_INPUT_NAME));
                    return true;
                }

                String id = args[1];
                Player target = null;

                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else if (sender instanceof Player) {
                    target = (Player) sender;
                }

                if (target != null) {
                    plugin.getSessionManager().startSession(target, id);
                }
                return true;
            }

            case "stop": {
                Player target = null;

                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                } else if (sender instanceof Player) {
                    target = (Player) sender;
                }

                if (target != null) {
                    plugin.getSessionManager().stopSession(target);
                }
                return true;
            }

            case "list": {
                if (!(sender instanceof Player)) return true;
                plugin.getGuiManager().openCutsceneList((Player) sender, 0);
                return true;
            }

            case "reload": {
                if (sender instanceof Player && !sender.isOp()) {
                    sender.sendMessage(plugin.getLangManager().getPrefixed(LangKey.MSG_DELETE_ADMIN));
                    return true;
                }

                // 메인 configyml 리로드
                plugin.reloadConfig();

                // 다국어 언어 파일 리로드 및 캐시 갱신
                plugin.getLangManager().load();

                // 시네마틱 데이터 파일 리로드
                plugin.getDataManager().loadAll();

                sender.sendMessage("§a[CinematicManager] Configuration, Language, and Cinematic files have been reloaded.");
                return true;
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        int length = args.length;

        if (length == 1) {
            return StringUtil.copyPartialMatches(args[0], subCommands, new ArrayList<>(subCommands.size()));
        }

        String subCommand = args[0].toLowerCase();

        if (length == 2) {
            if (subCommand.equals("edit") || subCommand.equals("play")) {
                List<String> ids = new ArrayList<>(plugin.getDataManager().getIds());
                return StringUtil.copyPartialMatches(args[1], ids, new ArrayList<>(ids.size()));
            }
            if (subCommand.equals("stop")) {
                List<String> playerNames = new ArrayList<>(Bukkit.getOnlinePlayers().size());
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playerNames.add(p.getName());
                }
                return StringUtil.copyPartialMatches(args[1], playerNames, new ArrayList<>(playerNames.size()));
            }
        }

        if (length == 3) {
            if (subCommand.equals("play")) {
                List<String> playerNames = new ArrayList<>(Bukkit.getOnlinePlayers().size());
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playerNames.add(p.getName());
                }
                return StringUtil.copyPartialMatches(args[2], playerNames, new ArrayList<>(playerNames.size()));
            }
        }

        return Collections.emptyList();
    }
}