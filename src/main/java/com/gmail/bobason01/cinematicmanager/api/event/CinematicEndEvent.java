package com.gmail.bobason01.cinematicmanager.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CinematicEndEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final String cinematicId;

    public CinematicEndEvent(Player player, String cinematicId) {
        this.player = player;
        this.cinematicId = cinematicId;
    }

    public Player getPlayer() { return player; }
    public String getCinematicId() { return cinematicId; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}