package me.thegabro.playtimemanager.Commands;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.CustomArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import me.thegabro.playtimemanager.PlayTimeManager;
import me.thegabro.playtimemanager.Users.DBUsersManager;
import me.thegabro.playtimemanager.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public interface CommandRegistrar {

    void registerCommands();

    default Argument<String> customPlayerArgument(String nodeName) {
        PlayTimeManager plugin = PlayTimeManager.getInstance();
        DBUsersManager dbUsersManager = DBUsersManager.getInstance();

        return new CustomArgument<>(new StringArgument(nodeName), info -> {
            if (dbUsersManager.getUserFromNickname(info.input()) == null) {
                throw CustomArgument.CustomArgumentException.fromAdventureComponent(
                        Component.text("")
                                .style(Style.style(NamedTextColor.WHITE))
                                .append(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                        " The player &e" + info.input() + "&7 has never joined the server!")));
            } else {
                return info.input();
            }
        }).replaceSuggestions(ArgumentSuggestions.strings(info -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .toArray(String[]::new)));
    }

    default Argument<String> customTargetArgument(String nodeName) {
        PlayTimeManager plugin = PlayTimeManager.getInstance();
        DBUsersManager dbUsersManager = DBUsersManager.getInstance();
        return new CustomArgument<>(new StringArgument(nodeName), info -> {
            String input = info.input();

            if (input.equals("+")) {
                return input;
            }

            if (dbUsersManager.getUserFromNickname(input) == null) {
                throw CustomArgument.CustomArgumentException.fromAdventureComponent(
                        Component.text("")
                                .style(Style.style(NamedTextColor.WHITE))
                                .append(Utils.parseColors(plugin.getConfiguration().getPluginPrefix() +
                                        " The player &e" + info.input() + "&7 has never joined the server!")));
            }

            return input;
        }).replaceSuggestions(ArgumentSuggestions.strings(info -> {
            List<String> suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());

            if (info.sender().hasPermission("playtime.others.modify.all")) {
                suggestions.add("+");
            }

            return suggestions.toArray(new String[0]);
        }));
    }

}

