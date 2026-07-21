package com.gmail.bobason01.cinematicmanager;

import com.gmail.bobason01.cinematicmanager.command.CinematicCommand;
import com.gmail.bobason01.cinematicmanager.hook.BetterHudHook;
import com.gmail.bobason01.cinematicmanager.hook.HookManager;
import com.gmail.bobason01.cinematicmanager.listener.*;
import com.gmail.bobason01.cinematicmanager.manager.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class CinematicManager extends JavaPlugin {

    private static CinematicManager instance;

    private ConfigManager configManager;
    private SessionManager sessionManager;
    private CustomNPCManager npcManager;
    private HookManager hookManager;
    private BetterHudHook betterHudHook;
    private GUIManager guiManager;
    private LangManager langManager;
    private ChatInputListener chatInputListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadLangFiles();

        this.langManager = new LangManager(this);
        this.configManager = new ConfigManager(this);
        this.hookManager = new HookManager(this);
        this.betterHudHook = new BetterHudHook(this);
        this.npcManager = new CustomNPCManager(this);
        this.sessionManager = new SessionManager(this);
        this.guiManager = new GUIManager(this);
        this.chatInputListener = new ChatInputListener(this);

        registerListeners();
        registerCommands();

        getLogger().info("CinematicManager has been enabled successfully.");
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new GUIListener(this), this);
        pm.registerEvents(chatInputListener, this);
        pm.registerEvents(new InputListener(this), this);
        pm.registerEvents(new CinematicControlListener(this), this);
    }

    private void registerCommands() {
        if (getCommand("cinematic") != null) {
            CinematicCommand cmd = new CinematicCommand(this);
            getCommand("cinematic").setExecutor(cmd);
            getCommand("cinematic").setTabCompleter(cmd);
        }
    }

    private void loadLangFiles() {
        File langDir = new File(getDataFolder(), "language");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        for (String lang : new String[]{"ko.yml", "en.yml", "ja.yml"}) {
            File file = new File(langDir, lang);
            if (!file.exists()) {
                try {
                    saveResource("language/" + lang, false);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            sessionManager.stopAll();
        }
        if (configManager != null) {
            configManager.saveAll();
        }
        getLogger().info("CinematicManager has been disabled.");
    }

    public static CinematicManager getInstance() {
        return instance;
    }

    public ConfigManager getDataManager() {
        return configManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public CustomNPCManager getNpcManager() {
        return npcManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public BetterHudHook getBetterHudHook() {
        return betterHudHook;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public LangManager getLangManager() {
        return langManager;
    }

    public ChatInputListener getChatInputListener() {
        return chatInputListener;
    }
}
