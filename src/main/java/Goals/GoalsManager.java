package Goals;

import me.thegabro.playtimemanager.PlayTimeManager;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GoalsManager {
    private static final Set<Goal> goals = new HashSet<>();
    private static PlayTimeManager plugin;

    public static void initialize(PlayTimeManager playTimeManager) {
        plugin = playTimeManager;
        loadGoals();
    }

    public static void addGoal(Goal goal) {
        goals.add(goal);
    }

    public static void removeGoal(Goal goal) {
        goals.remove(goal);
        goal.deleteFile();
    }

    public static Goal getGoal(String name) {
        return goals.stream()
                .filter(g -> g.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public static Set<Goal> getGoals() {
        return new HashSet<>(goals);
    }

    public static List<String> getGoalsNames() {
        return goals.stream().map(Goal::getName).collect(Collectors.toList());
    }

    public static void clearGoals() {
        goals.clear();
    }

    public static void loadGoals() {
        File goalsFolder = new File(plugin.getDataFolder(), "Goals");
        if (goalsFolder.exists() && goalsFolder.isDirectory()) {
            File[] goalFiles = goalsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (goalFiles != null) {
                for (File file : goalFiles) {
                    String goalName = file.getName().replace(".yml", "");
                    new Goal(plugin, goalName, 0L, false);
                }
            }
        }
    }

    public static boolean areAllInactive(){

        for(Goal g : goals)
            if(g.isActive())
                return false;

        return true;

    }
}