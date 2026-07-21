package com.gmail.bobason01.cinematicmanager;

import com.gmail.bobason01.cinematicmanager.command.CinematicCommand;
import com.gmail.bobason01.cinematicmanager.hook.DialogueHudHook;
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
    private DialogueHudHook dialogueHudHook = DialogueHudHook.disabled();
    private GUIManager guiManager;
    private LangManager langManager;
    private ChatInputListener chatInputListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadLangFiles();
        loadAiAuthoringFiles();

        this.langManager = new LangManager(this);
        this.configManager = new ConfigManager(this);
        this.hookManager = new HookManager(this);
        this.dialogueHudHook = createDialogueHudHook();
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

    private DialogueHudHook createDialogueHudHook() {
        if (!getServer().getPluginManager().isPluginEnabled("BetterHud")) {
            getLogger().info("BetterHud not found. Dialogue will use the built-in bossbar renderer.");
            return DialogueHudHook.disabled();
        }
        try {
            Class<?> type = Class.forName(
                    "com.gmail.bobason01.cinematicmanager.hook.BetterHudHook",
                    true,
                    getClassLoader());
            return (DialogueHudHook) type.getConstructor(CinematicManager.class).newInstance(this);
        } catch (Throwable error) {
            getLogger().warning("BetterHud integration could not start; using bossbar dialogue: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            return DialogueHudHook.disabled();
        }
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

    private void loadAiAuthoringFiles() {
        for (String resource : new String[]{
                "ai/README.md", "ai/cinematic.schema.json", "ai/example.yml"}) {
            File file = new File(getDataFolder(), resource);
            if (!file.exists()) {
                try {
                    saveResource(resource, false);
                } catch (IllegalArgumentException exception) {
                    getLogger().warning("Missing AI authoring resource: " + resource);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            sessionManager.shutdown();
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

    public DialogueHudHook getBetterHudHook() {
        return dialogueHudHook;
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
