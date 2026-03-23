package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Handlers;

import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.PlaceholderUtils;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.UserResolver;
import me.thegabro.playtimemanager.Goals.GoalsManager;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.OfflinePlayer;

public class GoalsHandler implements PlaceholderHandler {

    private final UserResolver resolver;
    private final PlaceholderUtils utils;
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final GoalsManager goalsManager = GoalsManager.getInstance();

    public GoalsHandler(UserResolver resolver, PlaceholderUtils utils) {
        this.resolver = resolver;
        this.utils = utils;
    }

    @Override
    public boolean canHandle(String params) {
        return params.toLowerCase().startsWith("goal_");
    }

    @Override
    public String handle(String params, OfflinePlayer player, PlaytimeFormat format) {
        String p = params.toLowerCase();

        if (p.equals("goal_count")) {
            return handleGoalCount(player);
        }

        if (p.startsWith("goal_completed_")) {
            return handleGoalCompleted(params.substring(15), player);
        }

        if (p.startsWith("goal_count_")) {
            return handleGoalCountByNickname(params.substring(11));
        }

        return utils.error("unknown goal placeholder");
    }

    private String handleGoalCount(OfflinePlayer player) {
        try {
            return String.valueOf(
                    onlineUsersManager.getOnlineUser(player.getName()).getCompletedGoals().size()
            );
        } catch (Exception e) {
            return utils.error("couldn't get goal count");
        }
    }

    /**
     * Handles goal_completed_<goalName> and goal_completed_<goalName>_<nickname>.
     * Tries to split off a trailing nickname; if no nickname is found, uses the requesting player.
     */
    private String handleGoalCompleted(String param, OfflinePlayer player) {
        String[] parts = splitGoalNameAndNickname(param);
        String goalName = parts[0];
        String nickname = parts[1];

        if (nickname == null) {
            // self form
            try {
                boolean completed = onlineUsersManager.getOnlineUser(player.getName())
                        .hasCompletedGoal(goalName);
                return String.valueOf(completed);
            } catch (Exception e) {
                return utils.error("couldn't get goal status");
            }
        }

        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        return String.valueOf(user.hasCompletedGoal(goalName));
    }


    private String handleGoalCountByNickname(String nickname) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        return String.valueOf(user.getCompletedGoals().size());
    }

    /**
     * Splits a param like "my_goal_name_Steve" into ["my_goal_name", "Steve"].
     * Falls back to [param, null] when no registered goal name matches a prefix,
     * meaning the whole string is treated as a goal name for the requesting player.
     */
    private String[] splitGoalNameAndNickname(String param) {
        int idx = param.lastIndexOf('_');
        while (idx > 0) {
            String candidateGoal = param.substring(0, idx);
            String candidateNick = param.substring(idx + 1);
            if (goalsManager.getGoal(candidateGoal) != null) {
                return new String[]{candidateGoal, candidateNick};
            }
            idx = param.lastIndexOf('_', idx - 1);
        }
        return new String[]{param, null};
    }
}