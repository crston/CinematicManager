package com.gmail.bobason01.cinematicmanager.manager;

public enum LangKey {
    // 시스템 공통
    PREFIX("prefix"),

    // 메뉴 관련
    MENU_MAIN("menu.main"),
    MENU_MAIN_LIST("menu.main_list"),
    MENU_MAIN_LIST_LORE("menu.main_list_lore"),
    MENU_MAIN_CREATE("menu.main_create"),
    MENU_MAIN_CREATE_LORE("menu.main_create_lore"),

    MENU_LIST("menu.list"),
    MENU_LIST_ID("menu.list_id"),
    MENU_LIST_EDIT_LORE("menu.list_edit_lore"),
    MENU_LIST_DELETE_LORE("menu.list_delete_lore"),
    MENU_LIST_PREV("menu.list_prev"),
    MENU_LIST_BACK("menu.list_back"),
    MENU_LIST_NEXT("menu.list_next"),

    MENU_STUDIO("menu.studio"),
    MENU_STUDIO_TIME("menu.studio_time"),
    MENU_STUDIO_CURRENT("menu.studio_current"),
    MENU_STUDIO_ADD_ACTION("menu.studio_add_action"),
    MENU_STUDIO_ADD_ACTION_LORE("menu.studio_add_action_lore"),
    MENU_STUDIO_ACTION_NAME("menu.studio_action_name"),
    MENU_STUDIO_ACTION_TARGET("menu.studio_action_target"),
    MENU_STUDIO_ADD_CAMERA("menu.studio_add_camera"),
    MENU_STUDIO_ADD_CAMERA_LORE("menu.studio_add_camera_lore"),
    MENU_STUDIO_CAMERA_NAME("menu.studio_camera_name"),
    MENU_STUDIO_ADD_EFFECT("menu.studio_add_effect"),
    MENU_STUDIO_ADD_EFFECT_LORE("menu.studio_add_effect_lore"),
    MENU_STUDIO_EFFECT_NAME("menu.studio_effect_name"),
    MENU_STUDIO_EFFECT_VALUE("menu.studio_effect_value"),
    MENU_STUDIO_DELETE_LORE("menu.studio_delete_lore"),
    MENU_STUDIO_PREV("menu.studio_prev"),
    MENU_STUDIO_BACK("menu.studio_back"),
    MENU_STUDIO_PLAY("menu.studio_play"),
    MENU_STUDIO_SAVE("menu.studio_save"),
    MENU_STUDIO_STOP("menu.studio_stop"),
    MENU_STUDIO_NEXT("menu.studio_next"),

    MENU_ACTION("menu.action"),
    MENU_ACTION_SPAWN("menu.action_spawn"),
    MENU_ACTION_SPAWN_LORE("menu.action_spawn_lore"),
    MENU_ACTION_MOVE("menu.action_move"),
    MENU_ACTION_MOVE_LORE("menu.action_move_lore"),
    MENU_ACTION_ANIMATION("menu.action_animation"),
    MENU_ACTION_ANIMATION_LORE("menu.action_animation_lore"),
    MENU_ACTION_HIDE("menu.action_hide"),
    MENU_ACTION_HIDE_LORE("menu.action_hide_lore"),
    MENU_ACTION_SHOW("menu.action_show"),
    MENU_ACTION_SHOW_LORE("menu.action_show_lore"),
    MENU_ACTION_BACK("menu.action_back"),

    MENU_SPAWN_TYPE("menu.spawn_type"),
    MENU_SPAWN_TYPE_NPC("menu.spawn_type_npc"),
    MENU_SPAWN_TYPE_MM("menu.spawn_type_mm"),
    MENU_SPAWN_TYPE_ME("menu.spawn_type_me"),

    MENU_NPC_TYPE("menu.npc_type"),
    MENU_NPC_TYPE_PLAYER("menu.npc_type_player"),
    MENU_NPC_TYPE_ZOMBIE("menu.npc_type_zombie"),
    MENU_NPC_TYPE_PIG("menu.npc_type_pig"),
    MENU_NPC_TYPE_SKELETON("menu.npc_type_skeleton"),
    MENU_NPC_TYPE_CUSTOM("menu.npc_type_custom"),
    MENU_NPC_TYPE_CUSTOM_LORE("menu.npc_type_custom_lore"),

    MENU_ANIMATION("menu.animation"),
    MENU_ANIMATION_SPIN("menu.animation_spin"),
    MENU_ANIMATION_SPIN_LORE("menu.animation_spin_lore"),
    MENU_ANIMATION_SPRINT("menu.animation_sprint"),
    MENU_ANIMATION_SPRINT_LORE("menu.animation_sprint_lore"),
    MENU_ANIMATION_SWIM("menu.animation_swim"),
    MENU_ANIMATION_SWIM_LORE("menu.animation_swim_lore"),
    MENU_ANIMATION_SNEAK("menu.animation_sneak"),
    MENU_ANIMATION_SNEAK_LORE("menu.animation_sneak_lore"),
    MENU_ANIMATION_SLEEP("menu.animation_sleep"),
    MENU_ANIMATION_SLEEP_LORE("menu.animation_sleep_lore"),
    MENU_ANIMATION_CUSTOM("menu.animation_custom"),
    MENU_ANIMATION_CUSTOM_LORE("menu.animation_custom_lore"),
    MENU_ANIMATION_STOP("menu.animation_stop"),
    MENU_ANIMATION_STOP_LORE("menu.animation_stop_lore"),

    MENU_TOGGLE_TITLE("menu.toggle.title"),
    MENU_TOGGLE_ON_NAME("menu.toggle.on_name"),
    MENU_TOGGLE_ON_LORE("menu.toggle.on_lore"),
    MENU_TOGGLE_OFF_NAME("menu.toggle.off_name"),
    MENU_TOGGLE_OFF_LORE("menu.toggle.off_lore"),

    MENU_NPC("menu.npc"),
    MENU_NPC_NAME("menu.npc_name"),
    MENU_NPC_SELECT_LORE("menu.npc_select_lore"),
    MENU_NPC_ID_LORE("menu.npc_id_lore"),
    MENU_NPC_BACK("menu.npc_back"),

    MENU_EFFECT("menu.effect"),
    MENU_EFFECT_SOUND("menu.effect_sound"),
    MENU_EFFECT_SOUND_LORE("menu.effect_sound_lore"),
    MENU_EFFECT_PARTICLE("menu.effect_particle"),
    MENU_EFFECT_PARTICLE_LORE("menu.effect_particle_lore"),
    MENU_EFFECT_TITLE("menu.effect_title"),
    MENU_EFFECT_TITLE_LORE("menu.effect_title_lore"),
    MENU_EFFECT_MESSAGE("menu.effect_message"),
    MENU_EFFECT_MESSAGE_LORE("menu.effect_message_lore"),
    MENU_EFFECT_BACK("menu.effect_back"),

    // 메시지 관련
    MSG_INPUT_NAME("msg.input_name"),
    MSG_INPUT_SPAWN("msg.input_spawn"),
    MSG_INPUT_SPAWN_MM("msg.input_spawn_mm"),
    MSG_INPUT_SPAWN_ME("msg.input_spawn_me"),
    MSG_INPUT_SPAWN_NPC_PLAYER("msg.input_spawn_npc_player"),
    MSG_INPUT_SPAWN_NPC_MOB("msg.input_spawn_npc_mob"),
    MSG_INPUT_CUSTOM_TYPE("msg.input_custom_type"),
    MSG_INPUT_CUSTOM_TYPE_CONFIRM("msg.input_custom_type_confirm"),
    MSG_ERROR_INVALID_TYPE("msg.error_invalid_type"),
    MSG_INPUT_SOUND("msg.input_sound"),
    MSG_INPUT_PARTICLE("msg.input_particle"),
    MSG_INPUT_TITLE("msg.input_title"),
    MSG_INPUT_MESSAGE("msg.input_message"),
    MSG_INPUT_ANIMATION("msg.input_animation"),
    MSG_INPUT_ANIM_STOP("msg.input_anim_stop"),
    MSG_CREATE_SUCCESS("msg.create_success"),
    MSG_SAVE_SUCCESS("msg.save_success"),
    MSG_DELETE_ADMIN("msg.delete_admin"),
    MSG_CAMERA_STATIC_SET("msg.camera_static_set"),
    MSG_RECORD_START("msg.record_start"),
    MSG_RECORD_END("msg.record_end"),

    BTN_CAMERA_STATIC("btn.camera_static"),
    BTN_CAMERA_RECORD("btn.camera_record");

    private final String path;

    LangKey(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}