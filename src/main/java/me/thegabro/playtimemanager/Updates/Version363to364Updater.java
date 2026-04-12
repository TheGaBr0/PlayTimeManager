package me.thegabro.playtimemanager.Updates;

import me.thegabro.playtimemanager.Configuration;
import me.thegabro.playtimemanager.Customizations.CommandsConfiguration;
import me.thegabro.playtimemanager.Customizations.GUIsConfiguration;
import me.thegabro.playtimemanager.JoinStreaks.ManagingClasses.RewardRegistry;
import me.thegabro.playtimemanager.JoinStreaks.Models.JoinStreakReward;
import me.thegabro.playtimemanager.JoinStreaks.Models.RewardUpdater;
import me.thegabro.playtimemanager.PlayTimeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Version363to364Updater {

    private final PlayTimeManager plugin = PlayTimeManager.getInstance();

    public Version363to364Updater() {}

    public void performUpgrade() {
        recreateConfigFile();
        updateJoinStreakRewards();
    }

    public void updateJoinStreakRewards(){
        RewardUpdater.getInstance().rewardsUpdater();

        RewardRegistry.getInstance().loadRewards();
        for(JoinStreakReward j : RewardRegistry.getInstance().getRewards()){
            j.setRepeatable(true);
        }

    }

    public void recreateConfigFile(){
        CommandsConfiguration commandsConfig = CommandsConfiguration.getInstance();
        commandsConfig.initialize(plugin);

        GUIsConfiguration guisConfiguration = GUIsConfiguration.getInstance();
        guisConfiguration.initialize(plugin);

        String borderName    = guisConfiguration.getString("rewards-gui.gui.border-item-name");
        String pageIndicator = guisConfiguration.getString("rewards-gui.pagination.page-indicator");
        String noMorePages   = guisConfiguration.getString("rewards-gui.pagination.no-more-pages");
        String firstPage     = guisConfiguration.getString("rewards-gui.pagination.first-page");
        String nextPageLore  = guisConfiguration.getString("rewards-gui.pagination.next-page.lore");
        String prevPageLore  = guisConfiguration.getString("rewards-gui.pagination.prev-page.lore");
        String noRewardsLore = guisConfiguration.getString("rewards-gui.no-rewards.lore");

        List<String> availableLore = guisConfiguration.getStringList("rewards-gui.reward-items.available.lore");
        List<String> claimedLore   = guisConfiguration.getStringList("rewards-gui.reward-items.claimed.lore");
        List<String> lockedLore    = guisConfiguration.getStringList("rewards-gui.reward-items.locked.lore");

        String infoRequiredJoins    = guisConfiguration.getString("rewards-gui.reward-items.info-lore.required-joins");
        String infoJoinStreak       = guisConfiguration.getString("rewards-gui.reward-items.info-lore.join-streak");
        String infoDescSeparator    = guisConfiguration.getString("rewards-gui.reward-items.info-lore.description-separator");
        String infoDesc             = guisConfiguration.getString("rewards-gui.reward-items.info-lore.description");
        String infoRDescSeparator   = guisConfiguration.getString("rewards-gui.reward-items.info-lore.reward-description-separator");
        String infoRDesc            = guisConfiguration.getString("rewards-gui.reward-items.info-lore.reward-description");

        String availableDescFmt  = guisConfiguration.getString("rewards-gui.reward-items.available.description-format");
        String availableRDescFmt = guisConfiguration.getString("rewards-gui.reward-items.available.reward-description-format");
        String claimedDescFmt    = guisConfiguration.getString("rewards-gui.reward-items.claimed.description-format");
        String claimedRDescFmt   = guisConfiguration.getString("rewards-gui.reward-items.claimed.reward-description-format");
        String lockedDescFmt     = guisConfiguration.getString("rewards-gui.reward-items.locked.description-format");
        String lockedRDescFmt    = guisConfiguration.getString("rewards-gui.reward-items.locked.reward-description-format");

        String claimedLoreEnabled    = guisConfiguration.getString("rewards-gui.filters.claimed.lore-enabled");
        String claimedLoreDisabled   = guisConfiguration.getString("rewards-gui.filters.claimed.lore-disabled");
        String availableLoreEnabled  = guisConfiguration.getString("rewards-gui.filters.available.lore-enabled");
        String availableLoreDisabled = guisConfiguration.getString("rewards-gui.filters.available.lore-disabled");
        String lockedLoreEnabled     = guisConfiguration.getString("rewards-gui.filters.locked.lore-enabled");
        String lockedLoreDisabled    = guisConfiguration.getString("rewards-gui.filters.locked.lore-disabled");

        guisConfiguration.updateConfig();

        guisConfiguration.getConfig().set("rewards-gui.reward-items.info-lore", null);
        guisConfiguration.clearCache();
        guisConfiguration.reload();

        if (borderName    != null) guisConfiguration.set("rewards-gui.gui.border.name",                    borderName);
        if (pageIndicator != null) guisConfiguration.set("rewards-gui.pagination.page-indicator.name",     pageIndicator);
        if (noMorePages   != null) guisConfiguration.set("rewards-gui.pagination.next-page-disabled.name", noMorePages);
        if (firstPage     != null) guisConfiguration.set("rewards-gui.pagination.prev-page-disabled.name", firstPage);

        if (nextPageLore  != null) guisConfiguration.set("rewards-gui.pagination.next-page.lore",  Collections.singletonList(nextPageLore));
        if (prevPageLore  != null) guisConfiguration.set("rewards-gui.pagination.prev-page.lore",  Collections.singletonList(prevPageLore));
        if (noRewardsLore != null) guisConfiguration.set("rewards-gui.no-rewards.lore",            Collections.singletonList(noRewardsLore));

        if (availableLore != null) guisConfiguration.set("rewards-gui.reward-items.available.lore",
                migrateLore(availableLore, availableDescFmt, availableRDescFmt,
                        infoRequiredJoins, null, infoDescSeparator, infoDesc, infoRDescSeparator, infoRDesc));
        if (claimedLore != null) guisConfiguration.set("rewards-gui.reward-items.claimed.lore",
                migrateLore(claimedLore, claimedDescFmt, claimedRDescFmt,
                        infoRequiredJoins, null, infoDescSeparator, infoDesc, infoRDescSeparator, infoRDesc));
        if (lockedLore != null) guisConfiguration.set("rewards-gui.reward-items.locked.lore",
                migrateLore(lockedLore, lockedDescFmt, lockedRDescFmt,
                        infoRequiredJoins, infoJoinStreak, infoDescSeparator, infoDesc, infoRDescSeparator, infoRDesc));

        if (claimedLoreEnabled    != null) guisConfiguration.set("rewards-gui.filters.claimed.lore-enabled",    Collections.singletonList(claimedLoreEnabled));
        if (claimedLoreDisabled   != null) guisConfiguration.set("rewards-gui.filters.claimed.lore-disabled",   Collections.singletonList(claimedLoreDisabled));
        if (availableLoreEnabled  != null) guisConfiguration.set("rewards-gui.filters.available.lore-enabled",  Collections.singletonList(availableLoreEnabled));
        if (availableLoreDisabled != null) guisConfiguration.set("rewards-gui.filters.available.lore-disabled", Collections.singletonList(availableLoreDisabled));
        if (lockedLoreEnabled     != null) guisConfiguration.set("rewards-gui.filters.locked.lore-enabled",     Collections.singletonList(lockedLoreEnabled));
        if (lockedLoreDisabled    != null) guisConfiguration.set("rewards-gui.filters.locked.lore-disabled",    Collections.singletonList(lockedLoreDisabled));

        Configuration.getInstance().updateConfig(false);
    }

    private List<String> migrateLore(List<String> lore,
                                     String descFmt, String rDescFmt,
                                     String requiredJoins, String joinStreak,
                                     String descSep, String desc,
                                     String rDescSep, String rDesc) {
        List<String> result = new ArrayList<>(lore.size());

        for (String line : lore) {
            if ("%DESCRIPTION%".equals(line.trim()) && descFmt != null && !descFmt.isBlank()) {
                result.add(descFmt);
            } else if ("%REWARD_DESCRIPTION%".equals(line.trim()) && rDescFmt != null && !rDescFmt.isBlank()) {
                result.add(rDescFmt);
            } else {
                result.add(line);
            }
        }

        if (requiredJoins != null && !requiredJoins.isBlank() && !result.contains(requiredJoins))
            result.add(requiredJoins);

        if (joinStreak != null && !joinStreak.isBlank() && !result.contains(joinStreak))
            result.add(joinStreak.replace("%JOIN_STREAK_COLOR%", "&e"));

        if (descSep != null && !descSep.isBlank() && !result.contains(descSep))
            result.add(descSep);

        if (desc != null && !desc.isBlank() && !result.contains(desc))
            result.add(desc);

        if (rDescSep != null && !rDescSep.isBlank() && !result.contains(rDescSep))
            result.add(rDescSep);

        if (rDesc != null && !rDesc.isBlank() && !result.contains(rDesc))
            result.add(rDesc);

        return result;
    }
}