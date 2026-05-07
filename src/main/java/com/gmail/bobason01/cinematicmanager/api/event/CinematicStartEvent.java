package com.gmail.bobason01.cinematicmanager.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CinematicStartEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final String cinematicId;
    private boolean cancelled;

    public CinematicStartEvent(Player player, String cinematicId) {
        this.player = player;
        this.cinematicId = cinematicId;
        this.cancelled = false;
    }

    public Player getPlayer() { return player; }
    public String getCinematicId() { return cinematicId; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}