package me.thegabro.playtimemanager;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderConditionEvaluator {

    // Singleton instance
    private static PlaceholderConditionEvaluator instance;

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("(.+?)\\s*(==|!=|>=|<=|>|<)\\s*(.+)");

    private PlaceholderConditionEvaluator() {}

    public static synchronized PlaceholderConditionEvaluator getInstance() {
        if (instance == null) {
            instance = new PlaceholderConditionEvaluator();
        }
        return instance;
    }

    /**
     * Evaluates a simple condition string using placeholders and Java logic.
     * Supports operators: ==, !=, >, <, >=, <=
     */
    public boolean evaluate(Player player, String condition) {
        if (condition == null || condition.isEmpty()) {
            return false;
        }

        // Replace all placeholders
        String parsed = PlaceholderAPI.setPlaceholders(player, condition.trim());

        Matcher matcher = EXPRESSION_PATTERN.matcher(parsed);
        if (!matcher.matches()) {
            return false; // Invalid expression
        }

        String leftRaw = matcher.group(1).trim();
        String operator = matcher.group(2).trim();
        String rightRaw = matcher.group(3).trim();

        Object left = parseValue(leftRaw);
        Object right = parseValue(rightRaw);

        return compareValues(left, right, operator);
    }

    private Object parseValue(String input) {
        // Remove surrounding quotes if any
        if ((input.startsWith("\"") && input.endsWith("\"")) || (input.startsWith("'") && input.endsWith("'"))) {
            input = input.substring(1, input.length() - 1);
        }

        // Try parse as number
        try {
            if (input.contains(".")) {
                return Double.parseDouble(input);
            } else {
                return Integer.parseInt(input);
            }
        } catch (NumberFormatException ignored) {}

        return input; // fallback to string
    }

    private boolean compareValues(Object left, Object right, String operator) {
        if (left instanceof Number && right instanceof Number) {
            double leftNum = ((Number) left).doubleValue();
            double rightNum = ((Number) right).doubleValue();

            return switch (operator) {
                case "==" -> leftNum == rightNum;
                case "!=" -> leftNum != rightNum;
                case ">"  -> leftNum > rightNum;
                case "<"  -> leftNum < rightNum;
                case ">=" -> leftNum >= rightNum;
                case "<=" -> leftNum <= rightNum;
                default   -> false;
            };
        }

        String leftStr = left.toString();
        String rightStr = right.toString();

        return switch (operator) {
            case "==" -> leftStr.equals(rightStr);
            case "!=" -> !leftStr.equals(rightStr);
            case ">"  -> leftStr.compareTo(rightStr) > 0;
            case "<"  -> leftStr.compareTo(rightStr) < 0;
            case ">=" -> leftStr.compareTo(rightStr) >= 0;
            case "<=" -> leftStr.compareTo(rightStr) <= 0;
            default   -> false;
        };
    }

    /**
     * Checks if the expression is valid Java-style expression.
     */
    public boolean isValid(Player player, String expression) {
        String parsed = PlaceholderAPI.setPlaceholders(player, expression.trim());
        Matcher matcher = EXPRESSION_PATTERN.matcher(parsed);
        return matcher.matches();
    }
}
