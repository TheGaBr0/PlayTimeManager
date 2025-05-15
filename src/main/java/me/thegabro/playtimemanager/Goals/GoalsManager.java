package me.thegabro.playtimemanager.Goals;

import me.thegabro.playtimemanager.PlayTimeManager;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GoalsManager {
    private static GoalsManager instance;
    private final Set<Goal> goals = new HashSet<>();
    private PlayTimeManager plugin;

    private GoalsManager() {}

    public static synchronized GoalsManager getInstance() {
        if (instance == null) {
            instance = new GoalsManager();
        }
        return instance;
    }

    public void initialize(PlayTimeManager playTimeManager) {
        this.plugin = playTimeManager;
        clearGoals();
        loadGoals();
    }

    public void addGoal(Goal goal) {
        goals.add(goal);
    }

    public void removeGoal(Goal goal) {
        goals.remove(goal);
    }

    public Goal getGoal(String name) {
        return goals.stream()
                .filter(g -> g.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public Set<Goal> getGoals() {
        return new HashSet<>(goals);
    }

    public List<String> getGoalsNames() {
        return goals.stream().map(Goal::getName).collect(Collectors.toList());
    }

    public void clearGoals() {
        goals.clear();
    }

    public void loadGoals() {
        File goalsFolder = new File(plugin.getDataFolder(), "Goals");
        if (goalsFolder.exists() && goalsFolder.isDirectory()) {
            File[] goalFiles = goalsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (goalFiles != null) {
                for (File file : goalFiles) {
                    String goalName = file.getName().replace(".yml", "");
                    new Goal(plugin, goalName, false);
                }
            }
        }
    }

    public boolean areAllInactive() {
        for (Goal g : goals)
            if (g.isActive())
                return false;

        return true;
    }
}