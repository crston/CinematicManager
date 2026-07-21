package com.gmail.bobason01.cinematicmanager.hook;

import org.bukkit.entity.Player;

/**
 * Optional dialogue renderer. Core classes only depend on this interface so
 * BetterHud classes are never resolved when the optional plugin is absent.
 */
public interface DialogueHudHook {
    boolean isEnabled();

    void showDialogue(Player player, String speaker, String line, String hint);

    void hideDialogue(Player player);

    static DialogueHudHook disabled() {
        return DisabledDialogueHudHook.INSTANCE;
    }

    final class DisabledDialogueHudHook implements DialogueHudHook {
        private static final DisabledDialogueHudHook INSTANCE = new DisabledDialogueHudHook();

        private DisabledDialogueHudHook() {
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void showDialogue(Player player, String speaker, String line, String hint) {
        }

        @Override
        public void hideDialogue(Player player) {
        }
    }
}
