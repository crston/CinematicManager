package com.gmail.bobason01.cinematicmanager.session;

import com.gmail.bobason01.cinematicmanager.CinematicManager;
import com.gmail.bobason01.cinematicmanager.api.event.CinematicEndEvent;
import com.gmail.bobason01.cinematicmanager.data.CinematicAction;
import com.gmail.bobason01.cinematicmanager.data.CinematicData;
import com.gmail.bobason01.cinematicmanager.manager.LangKey;
import com.gmail.bobason01.cinematicmanager.manager.LangManager;
import com.gmail.bobason01.cinematicmanager.util.PacketHelper;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import org.bukkit.block.data.BlockData;

public class CinematicSession {

    private final CinematicManager plugin;
    private final Player player;
    private final CinematicData data;

    private boolean active = false;
    private boolean paused = false;
    private boolean waitingForInput = false;
    private boolean hasPapi = false;
    private BukkitTask ticker;
    private int currentTick = 0;
    private int maxTick = 0;

    private Location originLocation;
    private GameMode originalGameMode;

    private final Map<String, Entity> activeEntities = new HashMap<>();
    private final Map<String, Location> spawnLocations = new HashMap<>();
    private final Map<String, ActivePath> movingNpcs = new HashMap<>();

    private Location staticCameraLoc = null;
    private boolean staticCameraApplied = false;
    /** 카메라 경로: absolute 전부 생성하지 않고 relative + origin + scratch */
    private List<Location> cameraRelative = null;
    private Location cameraOrigin = null;
    private final Location cameraScratch = new Location(null, 0, 0, 0);
    private int cameraStep = 0;

    private List<String> dialoguePages = Collections.emptyList();
    private int dialoguePageIndex = 0;
    private String dialogueDisplayMode = "both";

    /** 시청자 시야 방향 앞 1블록에만 보이는 개인 페이크 블록 */
    private Location clickProxyBlock = null;
    private static final BlockData BARRIER_DATA = Material.BARRIER.createBlockData();
    private boolean clickProxyEnabled = true;

    /** 같은 틱에서 대화 시작 후 남은 액션 (대화 끝나면 실행) */
    private final Deque<CinematicAction> deferredActions = new ArrayDeque<>();

    private String dialogueSpeaker = "";
    private String dialogueFullLine = "";
    private String dialogueSpeakerPlain = "";
    private String dialogueFullPlain = "";
    private int dialoguePlainLength = 0;
    private String dialogueHint = "";
    private int dialogueTypedChars = 0;
    private int dialogueTypeTick = 0;
    private boolean dialogueTyping = false;
    private BossBar dialogueBossBar;
    private boolean betterHudMode = false;

    private boolean typingEnabled = true;
    private int typingTicksPerChar = 2;
    private boolean typingSoundEnabled = true;
    private String typingSound = "ui.button.click";
    private float typingVolume = 0.35f;
    private float typingPitch = 1.7f;

    public CinematicSession(CinematicManager plugin, Player player, String id) {
        this(plugin, player, plugin.getDataManager().getCinematic(id));
    }

    public CinematicSession(CinematicManager plugin, Player player, CinematicData data) {
        this.plugin = plugin;
        this.player = player;
        this.data = data;
        this.hasPapi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public void start() {
        if (active || data == null) return;
        this.active = true;
        this.originLocation = player.getLocation().clone();
        this.originalGameMode = player.getGameMode();
        this.maxTick = data.getLastTick();
        this.currentTick = 0;
        this.clickProxyEnabled = plugin.getConfig().getBoolean("dialogue.click-proxy", true);
        cacheTypingConfig();
        this.betterHudMode = plugin.getBetterHudHook() != null && plugin.getBetterHudHook().isEnabled();

        player.setGameMode(GameMode.SPECTATOR);
        runTicker();
    }

    private void cacheTypingConfig() {
        typingEnabled = plugin.getConfig().getBoolean("dialogue.typing.enabled", true);
        typingTicksPerChar = Math.max(1, plugin.getConfig().getInt("dialogue.typing.ticks-per-char", 2));
        typingSound = plugin.getConfig().getString("dialogue.typing.sound", "ui.button.click");
        typingSoundEnabled = typingSound != null && !typingSound.isBlank();
        typingVolume = (float) plugin.getConfig().getDouble("dialogue.typing.volume", 0.35);
        typingPitch = (float) plugin.getConfig().getDouble("dialogue.typing.pitch", 1.7);
    }

    private void runTicker() {
        this.ticker = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || !player.isOnline()) { stop(); return; }

                // 대화/대기 중: 타임라인·이동·카메라 전부 정지 (타이핑/클릭만)
                if (waitingForInput) {
                    updateClickProxy();
                    tickDialogueTyping();
                    return;
                }
                if (paused) return;

                handleCameraPlayback();

                List<CinematicAction> actions = data.getTimeline().get(currentTick);
                if (actions != null) {
                    boolean deferRest = false;
                    for (CinematicAction action : actions) {
                        if (deferRest) {
                            deferredActions.addLast(action);
                            continue;
                        }
                        processAction(action);
                        if (waitingForInput) {
                            deferRest = true;
                        }
                    }
                }

                updateNpcMovements();

                // 이번 틱에서 대화에 들어갔으면 틱만 소모하고 멈춤 (재트리거 방지)
                if (waitingForInput) {
                    currentTick++;
                    return;
                }

                if (currentTick > maxTick && movingNpcs.isEmpty()) {
                    stop();
                    return;
                }
                currentTick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void setPaused(boolean paused) {
        if (waitingForInput) return;
        this.paused = paused;
        LangManager lang = plugin.getLangManager();
        if (paused) {
            player.sendTitle(" ", lang.get(LangKey.MSG_PAUSE_TITLE), 0, 60, 10);
            player.sendMessage(lang.getPrefixed(LangKey.MSG_PAUSE_SUBTITLE));
        } else {
            player.sendTitle("", "", 0, 5, 0);
        }
    }

    public boolean isPaused() { return paused; }
    public boolean isWaitingForInput() { return waitingForInput; }
    public void skip() { stop(); }

    /**
     * 우클릭으로 대화/대기 페이지를 넘긴다.
     * 타이핑 중이면 먼저 전체 문장을 완성한다.
     */
    public boolean advanceDialogue() {
        if (!waitingForInput) return false;

        if (dialogueTyping && plugin.getConfig().getBoolean("dialogue.typing.click-completes", true)) {
            completeDialogueTyping();
            renderDialogueFrame();
            return true;
        }

        dialoguePageIndex++;
        if (dialoguePageIndex >= dialoguePages.size()) {
            clearDialogueState();
            drainDeferredActions();
            return true;
        }

        showDialoguePage();
        return true;
    }

    /** 클릭 대상이 이 세션의 개인 페이크 블록인지 */
    public boolean isClickProxy(Location loc) {
        if (loc == null || clickProxyBlock == null) return false;
        return clickProxyBlock.getWorld() != null
                && clickProxyBlock.getWorld().equals(loc.getWorld())
                && clickProxyBlock.getBlockX() == loc.getBlockX()
                && clickProxyBlock.getBlockY() == loc.getBlockY()
                && clickProxyBlock.getBlockZ() == loc.getBlockZ();
    }

    private void processAction(CinematicAction action) {
        switch (action.getType()) {
            case SPAWN_NPC -> handleSpawn(action);
            case MOVE_NPC -> handleNpcMove(action);
            case CAMERA -> handleCamera(action);
            case SOUND -> handleSound(action);
            case PARTICLE -> handleParticle(action);
            case TITLE -> handleTitle(action);
            case MESSAGE -> handleMessage(action);
            case COMMAND -> handleCommand(action);
            case HIDE_ENTITY -> handleHide(action);
            case SHOW_ENTITY -> handleShow(action);
            case LIGHTNING -> handleLightning(action);
            case ANIMATION -> handleAnimation(action);
            case DIALOGUE -> handleDialogue(action);
            case WAIT -> handleWait(action);
        }
    }

    private void handleSound(CinematicAction action) {
        String soundName = action.getValue();
        player.playSound(player.getLocation(), soundName, SoundCategory.MASTER, 1f, 1f);
    }

    private void handleParticle(CinematicAction action) {
        try {
            Particle particle = Particle.valueOf(action.getValue().toUpperCase());
            player.spawnParticle(particle, action.getLocation(), 20, 0.5, 0.5, 0.5, 0.05);
        } catch (Exception ignored) {}
    }

    private void handleSpawn(CinematicAction action) {
        String spawnName = action.getValue();
        if (hasPapi) spawnName = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, spawnName);
        Location loc = action.getLocation();
        Entity npc = null;
        String lower = spawnName.toLowerCase();
        if (lower.contains("npc:")) {
            String val = spawnName.substring(lower.indexOf("npc:") + 4);
            String[] split = val.split(":");
            String type = "PLAYER", name = split[0], skin = split.length > 1 ? split[1] : split[0];
            if (split.length >= 2 && isEntityType(split[0])) {
                type = split[0]; name = split[1]; skin = split.length > 2 ? split[2] : name;
            }
            npc = plugin.getNpcManager().spawnNPC(player, loc, type, name, skin);
        } else if (lower.contains("mythicmobs:")) {
            npc = plugin.getNpcManager().spawnMythicMob(player, spawnName.substring(lower.indexOf("mythicmobs:") + 11).trim(), loc);
        } else if (lower.contains("modelengine:")) {
            npc = plugin.getNpcManager().spawnModelEngine(player, spawnName.substring(lower.indexOf("modelengine:") + 12).trim(), loc);
        }
        if (npc != null) {
            String key = sanitize(action.getValue());
            activeEntities.put(key, npc);
            spawnLocations.put(key, loc);
        }
    }

    private void handleNpcMove(CinematicAction action) {
        String targetKey = sanitize(action.getExtra());
        List<Location> relativePath = data.getPathRecord(action.getValue());
        if (relativePath == null || relativePath.isEmpty()) return;

        Location origin = spawnLocations.get(targetKey);
        if (origin == null) {
            // 녹화 시 기준점(MOVE 액션 location)으로 폴백
            origin = action.getLocation();
        }
        if (origin == null) return;

        // 키 불일치 시에도 엔티티를 찾을 수 있으면 매핑 보강
        Entity entity = findEntity(targetKey);
        if (entity == null && action.getExtra() != null) {
            entity = findEntity(action.getExtra());
        }
        if (entity != null) {
            String foundKey = null;
            for (Map.Entry<String, Entity> e : activeEntities.entrySet()) {
                if (e.getValue().equals(entity)) {
                    foundKey = e.getKey();
                    break;
                }
            }
            if (foundKey != null) {
                targetKey = foundKey;
                spawnLocations.putIfAbsent(targetKey, origin.clone());
            }
        }

        ActivePath path = new ActivePath(relativePath, origin.clone());
        path.entity = entity;
        movingNpcs.put(targetKey, path);
    }

    private void handleCamera(CinematicAction action) {
        if (action.getValue().equalsIgnoreCase("static")) {
            this.staticCameraLoc = action.getLocation();
            this.staticCameraApplied = false;
            this.cameraRelative = null;
            this.cameraOrigin = null;
        } else {
            List<Location> relativePath = data.getPathRecord(action.getValue());
            if (relativePath != null) {
                this.cameraRelative = relativePath;
                this.cameraOrigin = action.getLocation();
                this.cameraStep = 0;
                this.staticCameraLoc = null;
                this.staticCameraApplied = false;
            }
        }
    }

    private void handleCameraPlayback() {
        if (staticCameraLoc != null) {
            // 정적 카메라: 최초 1회만 teleport (매 틱 금지)
            if (!staticCameraApplied) {
                player.teleport(staticCameraLoc);
                staticCameraApplied = true;
            }
            return;
        }
        if (cameraRelative != null && cameraOrigin != null && cameraStep < cameraRelative.size()) {
            Location rel = cameraRelative.get(cameraStep++);
            cameraScratch.setWorld(cameraOrigin.getWorld());
            cameraScratch.setX(cameraOrigin.getX() + rel.getX());
            cameraScratch.setY(cameraOrigin.getY() + rel.getY());
            cameraScratch.setZ(cameraOrigin.getZ() + rel.getZ());
            cameraScratch.setYaw(rel.getYaw());
            cameraScratch.setPitch(rel.getPitch());
            player.teleport(cameraScratch);
        }
    }

    private void updateNpcMovements() {
        Iterator<Map.Entry<String, ActivePath>> it = movingNpcs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActivePath> entry = it.next();
            ActivePath path = entry.getValue();
            Entity entity = path.entity;
            if (entity == null || !entity.isValid()) {
                entity = findEntity(entry.getKey());
                path.entity = entity;
            }
            if (entity == null || !entity.isValid() || path.step >= path.relativeLocations.size()) {
                it.remove();
                continue;
            }
            Location rel = path.relativeLocations.get(path.step++);
            Location scratch = path.scratch;
            scratch.setWorld(path.baseOrigin.getWorld());
            scratch.setX(path.baseOrigin.getX() + rel.getX());
            scratch.setY(path.baseOrigin.getY() + rel.getY());
            scratch.setZ(path.baseOrigin.getZ() + rel.getZ());
            scratch.setYaw(rel.getYaw());
            scratch.setPitch(rel.getPitch());
            plugin.getNpcManager().move(player, entity, scratch);
        }
    }

    private void handleAnimation(CinematicAction action) {
        Entity e = findEntity(action.getExtra());
        if (e != null) {
            String val = action.getValue();
            if (hasPapi) val = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, val);
            plugin.getNpcManager().playAnimation(player, e, val);
        }
    }

    private void handleCommand(CinematicAction action) {
        String cmd = action.getValue().replace("%player%", player.getName());
        if (hasPapi) cmd = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, cmd);
        if (cmd.startsWith("#")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring(1).trim());
        else player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
    }

    private void handleLightning(CinematicAction action) {
        Location loc = action.getLocation();
        try {
            PacketHelper.sendLightning(player, loc);
            player.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 10f, 1f);
        } catch (Exception ignored) {}
    }

    private void handleTitle(CinematicAction action) {
        String raw = action.getValue();
        if (hasPapi) raw = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, raw);
        String[] split = raw.split(";", 2);
        player.sendTitle(split[0].replace("&", "§"), split.length > 1 ? split[1].replace("&", "§") : "", 10, 70, 20);
    }

    private void handleMessage(CinematicAction action) {
        String msg = action.getValue();
        if (hasPapi) msg = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, msg);
        player.sendMessage(msg.replace("&", "§"));
    }

    private void handleDialogue(CinematicAction action) {
        String raw = action.getValue() != null ? action.getValue() : "";
        if (hasPapi) raw = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, raw);

        String separator = plugin.getConfig().getString("dialogue.page-separator", "||");
        String[] pages = raw.split(java.util.regex.Pattern.quote(separator), -1);
        List<String> parsed = new ArrayList<>();
        for (String page : pages) {
            if (!page.isBlank()) parsed.add(page.trim());
        }
        if (parsed.isEmpty()) parsed.add(" ");

        dialogueDisplayMode = resolveDisplayMode(action.getExtra());
        dialoguePages = parsed;
        dialoguePageIndex = 0;
        waitingForInput = true;
        updateClickProxy();
        showDialoguePage();
    }

    private void handleWait(CinematicAction action) {
        String raw = action.getValue() != null ? action.getValue() : "";
        if (hasPapi && !raw.isEmpty()) {
            raw = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, raw);
        }
        dialogueDisplayMode = resolveDisplayMode(action.getExtra());
        dialoguePages = List.of(raw.isBlank() ? " " : raw.trim());
        dialoguePageIndex = 0;
        waitingForInput = true;
        updateClickProxy();
        showDialoguePage();
    }

    private String resolveDisplayMode(String extra) {
        if (extra != null && !extra.isBlank()) {
            String mode = extra.trim().toLowerCase(Locale.ROOT);
            if (mode.equals("title") || mode.equals("actionbar") || mode.equals("both")) {
                return mode;
            }
        }
        return plugin.getConfig().getString("dialogue.display-mode", "bossbar").toLowerCase(Locale.ROOT);
    }

    private void showDialoguePage() {
        String page = normalizeLineBreaks(dialoguePages.get(dialoguePageIndex));
        String[] split = page.split(";", 2);
        dialogueSpeaker = color(split[0]);
        dialogueFullLine = split.length > 1 ? color(split[1]) : "";
        // 화자;대사 형식이 아니면 전체를 대사로
        if (dialogueFullLine.isEmpty() && split.length == 1) {
            dialogueSpeaker = "";
            dialogueFullLine = color(split[0]);
        }
        dialogueSpeaker = normalizeLineBreaks(dialogueSpeaker);
        dialogueFullLine = normalizeLineBreaks(dialogueFullLine);

        dialogueHint = ""; // 우클릭 힌트 미표시

        dialogueSpeakerPlain = stripLegacy(dialogueSpeaker);
        dialogueFullPlain = stripLegacy(dialogueFullLine);
        dialoguePlainLength = dialogueFullPlain.length();
        dialogueTypedChars = 0;
        dialogueTypeTick = 0;
        dialogueTyping = typingEnabled && dialoguePlainLength > 0;
        betterHudMode = plugin.getBetterHudHook() != null && plugin.getBetterHudHook().isEnabled();

        // 페이지 시작 때 1회만 클리어
        if (betterHudMode) {
            clearDialogueBossBar();
            player.sendTitle("", "", 0, 1, 0);
            sendActionBar("");
        }
        renderDialogueFrame();
    }

    private void tickDialogueTyping() {
        if (!dialogueTyping) return;

        dialogueTypeTick++;
        if (dialogueTypeTick < typingTicksPerChar) return;
        dialogueTypeTick = 0;

        if (dialogueTypedChars >= dialoguePlainLength) {
            dialogueTyping = false;
            renderDialogueFrame();
            return;
        }

        dialogueTypedChars++;
        playTypingSound();
        renderDialogueFrame();
    }

    private void completeDialogueTyping() {
        dialogueTyping = false;
        dialogueTypedChars = dialoguePlainLength;
    }

    private void playTypingSound() {
        if (!typingSoundEnabled) return;
        try {
            player.playSound(player.getLocation(), typingSound, SoundCategory.MASTER, typingVolume, typingPitch);
        } catch (Exception ignored) {}
    }

    private void renderDialogueFrame() {
        if (betterHudMode) {
            String plainVisible = dialogueTypedChars <= 0
                    ? ""
                    : (dialogueTypedChars >= dialoguePlainLength
                    ? dialogueFullPlain
                    : dialogueFullPlain.substring(0, dialogueTypedChars));
            plugin.getBetterHudHook().showDialogue(player, dialogueSpeakerPlain, plainVisible, "");
            return;
        }

        String visibleLine = visibleTypedText(dialogueFullLine, dialogueTypedChars);

        // 폴백: BetterHud 없을 때 단순 보스바 텍스트
        String body;
        if (!dialogueSpeaker.isEmpty() && !visibleLine.isEmpty()) {
            body = "§e" + dialogueSpeaker + "§7 » §f" + visibleLine;
        } else if (!visibleLine.isEmpty()) {
            body = "§f" + visibleLine;
        } else if (!dialogueSpeaker.isEmpty()) {
            body = "§e" + dialogueSpeaker;
        } else {
            body = "§f ";
        }
        ensureDialogueBossBar();
        dialogueBossBar.setTitle(body);
        dialogueBossBar.setProgress(1.0);
        dialogueBossBar.setVisible(true);
        if (!dialogueBossBar.getPlayers().contains(player)) {
            dialogueBossBar.addPlayer(player);
        }
    }

    private void ensureDialogueBossBar() {
        if (dialogueBossBar != null) return;
        dialogueBossBar = Bukkit.createBossBar(" ", BarColor.YELLOW, BarStyle.SOLID);
        dialogueBossBar.setProgress(1.0);
        dialogueBossBar.addPlayer(player);
    }

    private void clearDialogueBossBar() {
        if (dialogueBossBar == null) return;
        dialogueBossBar.setVisible(false);
        dialogueBossBar.removeAll();
        dialogueBossBar = null;
    }

    private String visibleTypedText(String colored, int plainChars) {
        if (!dialogueTyping && plainChars >= dialoguePlainLength) {
            return colored;
        }
        if (plainChars <= 0) return "";
        StringBuilder out = new StringBuilder(plainChars + 8);
        int seen = 0;
        for (int i = 0; i < colored.length(); i++) {
            char c = colored.charAt(i);
            if (c == '§' && i + 1 < colored.length()) {
                out.append(c).append(colored.charAt(++i));
                continue;
            }
            if (seen >= plainChars) break;
            out.append(c);
            seen++;
        }
        return out.toString();
    }

    private static String stripLegacy(String text) {
        if (text == null || text.isEmpty()) return "";
        int len = text.length();
        StringBuilder sb = null;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < len) {
                if (sb == null) {
                    sb = new StringBuilder(len);
                    sb.append(text, 0, i);
                }
                i++;
                continue;
            }
            if (sb != null) sb.append(c);
        }
        return sb == null ? text : sb.toString();
    }

    /** 입력의 \\n / &lt;br&gt; 을 실제 개행으로 (BetterHud 여러 줄용) */
    private String normalizeLineBreaks(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        return text
                .replace("\\n", "\n")
                .replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n");
    }

    private void clearDialogueState() {
        waitingForInput = false;
        dialoguePages = Collections.emptyList();
        dialoguePageIndex = 0;
        dialogueTyping = false;
        dialogueTypedChars = 0;
        dialogueSpeaker = "";
        dialogueFullLine = "";
        dialogueHint = "";
        clearClickProxy();
        clearDialogueBossBar();
        if (plugin.getBetterHudHook() != null) {
            plugin.getBetterHudHook().hideDialogue(player);
        }
        player.sendTitle("", "", 0, 1, 0);
        sendActionBar("");
    }
    private void drainDeferredActions() {
        while (!deferredActions.isEmpty() && !waitingForInput) {
            processAction(deferredActions.pollFirst());
        }
    }

    /**
     * 시야 방향 정면 1블록에만 개인 페이크 블록을 둔다.
     * 실제 월드에는 설치되지 않는다 (player.sendBlockChange).
     */
    private void updateClickProxy() {
        if (!player.isOnline()) return;
        if (!clickProxyEnabled) {
            clearClickProxy();
            return;
        }

        Location eye = player.getEyeLocation();
        Location next = eye.clone()
                .add(eye.getDirection().normalize())
                .getBlock()
                .getLocation();

        if (clickProxyBlock != null && sameBlock(clickProxyBlock, next)) {
            // 같은 블록이면 패킷 재전송 안 함
            return;
        }

        if (clickProxyBlock != null) {
            restoreBlock(clickProxyBlock);
        }
        clickProxyBlock = next;
        player.sendBlockChange(clickProxyBlock, BARRIER_DATA);
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private void restoreBlock(Location loc) {
        if (loc.getWorld() == null || !player.isOnline()) return;
        player.sendBlockChange(loc, loc.getBlock().getBlockData());
    }

    private void clearClickProxy() {
        if (clickProxyBlock != null) {
            restoreBlock(clickProxyBlock);
            clickProxyBlock = null;
        }
    }

    private void sendActionBar(String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message == null ? "" : message));
    }

    private String color(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

    private void handleHide(CinematicAction action) {
        Entity e = findEntity(action.getValue());
        if (e != null) e.teleport(e.getLocation().add(0, -100, 0));
    }

    private void handleShow(CinematicAction action) {
        Entity e = findEntity(action.getExtra() != null ? action.getExtra() : action.getValue());
        if (e != null) e.teleport(action.getLocation());
    }

    private Entity findEntity(String key) {
        if (key == null) return null;
        String sk = sanitize(key);
        Entity exact = activeEntities.get(sk);
        if (exact != null) return exact;
        // 폴백 fuzzy는 NPC 수가 적을 때만
        if (activeEntities.size() <= 8) {
            for (Map.Entry<String, Entity> entry : activeEntities.entrySet()) {
                if (entry.getKey().contains(sk) || sk.contains(entry.getKey())) return entry.getValue();
            }
        }
        return null;
    }

    private String sanitize(String id) {
        if (id == null) return "";
        return ChatColor.stripColor(id).toLowerCase().trim();
    }

    private boolean isEntityType(String s) {
        try { EntityType.valueOf(s.toUpperCase()); return true; } catch (Exception e) { return false; }
    }

    public void stop() {
        if (!active) return;
        active = false;
        waitingForInput = false;
        dialoguePages = Collections.emptyList();
        dialoguePageIndex = 0;
        dialogueTyping = false;
        deferredActions.clear();
        clearDialogueBossBar();
        if (plugin.getBetterHudHook() != null) {
            plugin.getBetterHudHook().hideDialogue(player);
        }
        if (ticker != null) ticker.cancel();
        clearClickProxy();
        activeEntities.values().forEach(e -> plugin.getNpcManager().remove(e));
        activeEntities.clear(); movingNpcs.clear(); spawnLocations.clear();
        if (player.isOnline()) {
            player.sendTitle("", "", 0, 1, 0);
            sendActionBar("");
            player.setGameMode(originalGameMode);
            player.teleport(originLocation);
        }

        // 시네마틱 종료 이벤트 호출
        if (data != null) {
            CinematicEndEvent event = new CinematicEndEvent(player, data.getId());
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    public boolean isActive() { return active; }

    private static class ActivePath {
        final List<Location> relativeLocations;
        final Location baseOrigin;
        final Location scratch;
        Entity entity;
        int step;

        ActivePath(List<Location> rl, Location bo) {
            this.relativeLocations = rl;
            this.baseOrigin = bo;
            this.scratch = new Location(bo.getWorld(), 0, 0, 0);
            this.step = 0;
        }
    }
}