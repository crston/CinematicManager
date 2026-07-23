package com.gmail.bobason01.cinematicmanager;

import com.gmail.bobason01.cinematicmanager.command.CinematicCommand;
import com.gmail.bobason01.cinematicmanager.fx.EnvironmentPacketListener;
import com.gmail.bobason01.cinematicmanager.fx.EnvironmentRecordManager;
import com.gmail.bobason01.cinematicmanager.hook.HookManager;
import com.gmail.bobason01.cinematicmanager.listener.*;
import com.gmail.bobason01.cinematicmanager.manager.*;
import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class CinematicManager extends JavaPlugin {

    private static CinematicManager instance;

    private ConfigManager configManager;
    private SessionManager sessionManager;
    private CustomNPCManager npcManager;
    private NpcPresetManager npcPresetManager;
    private EnvironmentRecordManager environmentRecordManager;
    private HookManager hookManager;
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
        this.npcManager = new CustomNPCManager(this);
        this.npcPresetManager = new NpcPresetManager(this);
        this.environmentRecordManager = new EnvironmentRecordManager(this);
        this.sessionManager = new SessionManager(this);
        this.guiManager = new GUIManager(this);
        this.chatInputListener = new ChatInputListener(this);

        registerListeners();
        registerCommands();
        PacketEvents.getAPI().getEventManager().registerListener(new EnvironmentPacketListener(this));

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

    public NpcPresetManager getNpcPresetManager() {
        return npcPresetManager;
    }

    public EnvironmentRecordManager getEnvironmentRecordManager() {
        return environmentRecordManager;
    }

    public HookManager getHookManager() {
        return hookManager;
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
