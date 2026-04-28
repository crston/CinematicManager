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

        // 1. 리소스 및 폴더 초기화
        saveDefaultConfig();
        loadLangFiles();

        // 2. 매니저 초기화 (의존성 순서 준수)
        // 언어와 설정 파일을 가장 먼저 로드
        this.langManager = new LangManager(this);
        this.configManager = new ConfigManager(this);

        // 외부 플러그인 연동 확인
        this.hookManager = new HookManager(this);

        // 엔티티 및 NPC 관리자
        this.npcManager = new CustomNPCManager(this);

        // 세션 및 GUI 관리자
        this.sessionManager = new SessionManager(this);
        this.guiManager = new GUIManager(this);
        this.chatInputListener = new ChatInputListener(this);

        // 3. 리스너 등록 (누락 없이 전부 포함)
        registerListeners();

        // 4. 명령어 등록
        registerCommands();

        getLogger().info("CinematicManager has been enabled successfully.");
    }

    /**
     * 모든 리스너를 등록합니다.
     */
    private void registerListeners() {
        var pm = getServer().getPluginManager();

        pm.registerEvents(new GUIListener(this), this);
        pm.registerEvents(chatInputListener, this);
        pm.registerEvents(new InputListener(this), this);

        // 관전자 모드 조작(왼쪽 클릭 진행/Shift 스킵)을 위한 핵심 리스너
        pm.registerEvents(new CinematicControlListener(this), this);
    }

    /**
     * 명령어를 등록합니다.
     */
    private void registerCommands() {
        if (getCommand("cinematic") != null) {
            CinematicCommand cmd = new CinematicCommand(this);
            getCommand("cinematic").setExecutor(cmd);
            getCommand("cinematic").setTabCompleter(cmd);
        }
    }

    /**
     * 다국어 파일을 생성 및 로드합니다.
     */
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
        // 플러그인 종료 시 모든 세션 강제 중단 및 NPC 제거
        if (sessionManager != null) {
            sessionManager.stopAll();
        }

        // 모든 데이터 파일 저장
        if (configManager != null) {
            configManager.saveAll();
        }

        getLogger().info("CinematicManager has been disabled.");
    }

    // --- Getter Methods ---

    public static CinematicManager getInstance() {
        return instance;
    }

    // ConfigManager를 DataManager 역할로도 사용 (기존 코드 호환성)
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