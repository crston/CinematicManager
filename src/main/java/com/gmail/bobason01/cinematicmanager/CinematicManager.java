package com.gmail.bobason01.cinematicmanager;

import com.gmail.bobason01.cinematicmanager.command.CinematicCommand;
import com.gmail.bobason01.cinematicmanager.hook.HookManager;
import com.gmail.bobason01.cinematicmanager.manager.*;
import com.gmail.bobason01.cinematicmanager.listener.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public class CinematicManager extends JavaPlugin {

    private static CinematicManager instance;

    private ConfigManager configManager;
    private SessionManager sessionManager;
    private CustomNPCManager npcManager;
    private HookManager hookManager;
    private GUIManager guiManager;
    private LangManager langManager;
    private ChatInputListener chatInputListener;

    @Override
    public void onEnable() {
        instance = this;

        // 1. 리소스 초기화
        saveDefaultConfig();
        loadLangFiles();

        // 2. 매니저 초기화 (순차적 의존성 주입)
        this.langManager = new LangManager(this);
        this.configManager = new ConfigManager(this);

        // HookManager는 외부 플러그인 로딩 상태를 먼저 확인해야 함
        this.hookManager = new HookManager(this);

        // CustomNPCManager는 텔레포트 시 HookManager의 엔티티 판정이 필요함
        this.npcManager = new CustomNPCManager(this);

        this.sessionManager = new SessionManager(this);
        this.guiManager = new GUIManager(this);
        this.chatInputListener = new ChatInputListener(this);

        // 3. 리스너 등록
        registerListeners();

        // 4. 명령어 등록
        if (getCommand("cinematic") != null) {
            CinematicCommand cmd = new CinematicCommand(this);
            getCommand("cinematic").setExecutor(cmd);
            getCommand("cinematic").setTabCompleter(cmd);
        }

        getLogger().info("CinematicManager has been enabled successfully.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(chatInputListener, this);
        getServer().getPluginManager().registerEvents(new InputListener(this), this);
    }

    private void loadLangFiles() {
        File langDir = new File(getDataFolder(), "language");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        String[] langs = {"ko.yml", "en.yml"};
        for (String lang : langs) {
            File file = new File(langDir, lang);
            if (!file.exists()) {
                saveResource("language/" + lang, false);
            }
        }
    }

    @Override
    public void onDisable() {
        // 모든 세션 강제 종료 (NPC 제거 포함)
        if (sessionManager != null) {
            sessionManager.stopAll();
        }

        // 데이터 안전하게 저장
        if (configManager != null) {
            configManager.saveAll();
        }

        getLogger().info("CinematicManager has been disabled.");
    }

    // Getter Methods
    public static CinematicManager getInstance() { return instance; }
    public ConfigManager getDataManager() { return configManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public SessionManager getSessionManager() { return sessionManager; }
    public CustomNPCManager getNpcManager() { return npcManager; }
    public HookManager getHookManager() { return hookManager; }
    public GUIManager getGuiManager() { return guiManager; }
    public LangManager getLangManager() { return langManager; }
    public ChatInputListener getChatInputListener() { return chatInputListener; }
}