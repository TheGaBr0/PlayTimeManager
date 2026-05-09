package me.thegabro.playtimemanager.Events;

import me.thegabro.playtimemanager.Users.OnlineUser;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class OnlineUserReadyEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final OnlineUser onlineUser;

    public OnlineUserReadyEvent(OnlineUser onlineUser) {
        this.onlineUser = onlineUser;
    }

    public OnlineUser getOnlineUser() {
        return onlineUser;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}