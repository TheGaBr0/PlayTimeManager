package me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Handlers;

import me.thegabro.playtimemanager.Customizations.PlaytimeFormats.PlaytimeFormat;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.PlaceholderUtils;
import me.thegabro.playtimemanager.ExternalPluginSupport.PlaceHolders.PlaceholderExpansion.Utils.UserResolver;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardRegistry;
import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardSubInstance;
import me.thegabro.playtimemanager.Users.DBUser;
import me.thegabro.playtimemanager.Users.OnlineUser;
import me.thegabro.playtimemanager.Users.OnlineUsersManager;
import org.bukkit.OfflinePlayer;

import java.util.List;

public class JoinStreakRewardHandler implements PlaceholderHandler {

    private static final String PREFIX = "streak_reward_";

    private final UserResolver resolver;
    private final PlaceholderUtils utils;
    private final OnlineUsersManager onlineUsersManager = OnlineUsersManager.getInstance();
    private final RewardRegistry rewardRegistry = RewardRegistry.getInstance();

    public JoinStreakRewardHandler(UserResolver resolver, PlaceholderUtils utils) {
        this.resolver = resolver;
        this.utils = utils;
    }

    @Override
    public boolean canHandle(String params) {
        String p = params.toLowerCase();
        return p.equals("joinstreak")
                || p.equals("relative_joinstreak")
                || p.startsWith("joinstreak_")
                || p.startsWith("relative_joinstreak_")
                || p.startsWith(PREFIX);
    }

    /**
     * Entry point. Routes joinstreak info placeholders and streak_reward_ placeholders.
     */
    @Override
    public String handle(String params, OfflinePlayer player, PlaytimeFormat format) {
        String p = params.toLowerCase();

        // --- Joinstreak info placeholders ---

        if (p.equals("joinstreak")) {
            OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());
            if (onlineUser == null) return utils.error("Loading...");
            try {
                return String.valueOf(onlineUser.getAbsoluteJoinStreak());
            } catch (Exception e) {
                return utils.error("couldn't get join streak");
            }
        }

        if (p.equals("relative_joinstreak")) {
            OnlineUser onlineUser = onlineUsersManager.getOnlineUser(player.getName());
            if (onlineUser == null) return utils.error("Loading...");
            try {
                return String.valueOf(onlineUser.getRelativeJoinStreak());
            } catch (Exception e) {
                return utils.error("couldn't get join streak");
            }
        }

        if (p.startsWith("relative_joinstreak_")) {
            return handleRelativeJoinStreak(params.substring(20));
        }

        if (p.startsWith("joinstreak_")) {
            return handleAbsoluteJoinStreak(params.substring(11));
        }

        // --- Streak reward placeholders ---

        String remainder = params.substring(PREFIX.length()); // strip "streak_reward_"

        // Parse optional sub-instance qualifier: "<id>.<requiredJoins>_..." vs "<id>_..."
        int dotIndex = remainder.indexOf('.');
        int firstUnderscore = remainder.indexOf('_');

        // Determine whether a dot qualifier is present and comes before the first underscore
        boolean hasDot = dotIndex != -1 && (firstUnderscore == -1 || dotIndex < firstUnderscore);

        int rewardId;
        Integer requiredJoins; // null means no sub-instance qualifier was given
        String afterId;        // the "_<property>" or "_<property>_<nickname>" tail

        if (hasDot) {
            // Format: <id>.<requiredJoins>_<property>[_<nickname>]
            String idStr = remainder.substring(0, dotIndex);
            rewardId = parseIntOrError(idStr);
            if (rewardId == -1) return utils.error("invalid reward id");

            String afterDot = remainder.substring(dotIndex + 1);
            int underscoreAfterDot = afterDot.indexOf('_');
            if (underscoreAfterDot == -1) return utils.error("unknown streak reward placeholder");

            String joinsStr = afterDot.substring(0, underscoreAfterDot);
            requiredJoins = parseIntOrError(joinsStr);
            if (requiredJoins == -1) return utils.error("invalid required joins");

            afterId = afterDot.substring(underscoreAfterDot + 1); // "<property>[_<nickname>]"
        } else {
            // Format: <id>_<property>[_<nickname>]
            if (firstUnderscore == -1) return utils.error("unknown streak reward placeholder");

            String idStr = remainder.substring(0, firstUnderscore);
            rewardId = parseIntOrError(idStr);
            if (rewardId == -1) return utils.error("invalid reward id");

            requiredJoins = null;
            afterId = remainder.substring(firstUnderscore + 1); // "<property>[_<nickname>]"
        }

        // Validate reward exists
        JoinStreakReward reward = rewardRegistry.getReward(rewardId);
        if (reward == null) return utils.error("reward not found");

        // Enforce single-join / range rules
        if (requiredJoins == null && !reward.isSingleJoinReward()) {
            return utils.error("reward has join range, specify join count");
        }
        if (requiredJoins != null && reward.isSingleJoinReward()) {
            return utils.error("reward has no join range");
        }

        // Determine effective requiredJoins for sub-instance lookup
        int effectiveRequiredJoins = (requiredJoins != null)
                ? requiredJoins
                : reward.getMinRequiredJoins();

        // Validate requiredJoins is within the reward's range
        if (requiredJoins != null &&
                (requiredJoins < reward.getMinRequiredJoins() || requiredJoins > reward.getMaxRequiredJoins())) {
            return utils.error("join count out of reward range");
        }

        // Split property and optional nickname from the remainder
        // Properties: received, claimed, expired, required_joins, description, repeatable
        String[] parts = splitPropertyAndNickname(afterId);
        String property = parts[0];
        String nickname = parts[1];

        // Resolve user
        DBUser user;
        if (nickname == null) {
            user = onlineUsersManager.getOnlineUser(player.getName());
            if (user == null) return utils.error("user not found");
        } else {
            user = resolver.resolve(nickname);
            if (user == DBUser.LOADING) return utils.error("Loading...");
            if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        }

        return resolveProperty(property, reward, effectiveRequiredJoins, user);
    }

    private String handleAbsoluteJoinStreak(String nickname) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        return String.valueOf(user.getAbsoluteJoinStreak());
    }

    private String handleRelativeJoinStreak(String nickname) {
        DBUser user = resolver.resolve(nickname);
        if (user == DBUser.LOADING) return utils.error("Loading...");
        if (user == DBUser.NOT_FOUND) return utils.error("Player not found in db");
        return String.valueOf(user.getRelativeJoinStreak());
    }

    /**
     * Resolves the requested property for the given reward sub-instance and user.
     */
    private String resolveProperty(String property, JoinStreakReward reward, int effectiveRequiredJoins, DBUser user) {
        switch (property.toLowerCase()) {

            case "received": {
                RewardSubInstance sub = findSubInstance(user, reward.getId(), effectiveRequiredJoins);
                return String.valueOf(sub != null);
            }

            case "to_be_claimed": {
                RewardSubInstance sub = findSubInstanceInToBeClaimed(user, reward.getId(), effectiveRequiredJoins);
                if (sub == null) return "false";
                return String.valueOf(!sub.expired());
            }

            case "expired": {
                RewardSubInstance sub = findSubInstanceInToBeClaimed(user, reward.getId(), effectiveRequiredJoins);
                if (sub == null) return "false";
                return String.valueOf(sub.expired());
            }

            case "required_joins":
                return reward.getRequiredJoinsDisplay();

            case "description":
                return reward.getDescription();

            case "reward_description":
                return reward.getRewardDescription();

            case "repeatable":
                return String.valueOf(reward.isRepeatable());

            default:
                return null;
        }
    }


    private RewardSubInstance findSubInstanceInToBeClaimed(DBUser user, int rewardId, int requiredJoins) {
        List<RewardSubInstance> rewards = user.getRewardsToBeClaimed();
        if (rewards == null) return null;
        for (RewardSubInstance sub : rewards) {
            if (sub.mainInstanceID() == rewardId && sub.requiredJoins() == requiredJoins) {
                return sub;
            }
        }
        return null;
    }

    /**
     * Finds a RewardSubInstance in the user's reward list matching both mainInstanceID and requiredJoins.
     * Returns null if not found.
     */
    private RewardSubInstance findSubInstance(DBUser user, int rewardId, int requiredJoins) {
        List<RewardSubInstance> rewards = user.getReceivedRewards();
        if (rewards == null) return null;
        for (RewardSubInstance sub : rewards) {
            if (sub.mainInstanceID() == rewardId && sub.requiredJoins() == requiredJoins) {
                return sub;
            }
        }
        return null;
    }

    /**
     * Splits a string like "received_Steve" into ["received", "Steve"],
     * or "received" into ["received", null] when no nickname is present.
     *
     * Known multi-word properties (required_joins, reward_description) are checked first
     * to avoid mistaking their underscore for a nickname separator.
     */
    private String[] splitPropertyAndNickname(String input) {
        // Known two-word properties that contain an underscore
        String[] twoWordProperties = {"required_joins", "reward_description", "to_be_claimed"};

        for (String prop : twoWordProperties) {
            if (input.toLowerCase().startsWith(prop + "_")) {
                String nickname = input.substring(prop.length() + 1);
                return new String[]{prop, nickname.isEmpty() ? null : nickname};
            }
            if (input.equalsIgnoreCase(prop)) {
                return new String[]{prop, null};
            }
        }

        // Single-word property
        int underscore = input.indexOf('_');
        if (underscore == -1) return new String[]{input, null};
        return new String[]{input.substring(0, underscore), input.substring(underscore + 1)};
    }

    /** Returns -1 if the string is not a valid non-negative integer. */
    private int parseIntOrError(String s) {
        try {
            int v = Integer.parseInt(s);
            return v >= 0 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}