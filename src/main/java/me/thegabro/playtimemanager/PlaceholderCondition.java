package me.thegabro.playtimemanager;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderCondition {

    // Pattern to find comparison operators
    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
            "(.+?)(==|!=|>=|<=|>|<)(.+)");

    // Pattern to identify numeric values
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    /**
     * Evaluate a condition string containing placeholders
     *
     * @param player The player to evaluate placeholders for
     * @param conditionString The condition string (e.g., "%player_level% > 10")
     * @return true if the condition is met, false otherwise
     */
    public static boolean evaluate(Player player, String conditionString) {
        // Replace all placeholders with their actual values
        String parsedCondition = PlaceholderAPI.setPlaceholders(player, conditionString.trim());

        // Find the comparison in the string
        Matcher comparisonMatcher = COMPARISON_PATTERN.matcher(parsedCondition);

        if (!comparisonMatcher.find()) {
            // No valid comparison found
            return false;
        }

        String leftSide = comparisonMatcher.group(1).trim();
        String operator = comparisonMatcher.group(2).trim();
        String rightSide = comparisonMatcher.group(3).trim();

        // Try to convert sides to numbers if possible
        Object leftValue = parseValue(leftSide);
        Object rightValue = parseValue(rightSide);

        // Perform the comparison
        return compare(leftValue, operator, rightValue);
    }

    /**
     * Parse a string value into the appropriate type (number or string)
     */
    private static Object parseValue(String value) {
        // Check if the value is numeric
        if (NUMERIC_PATTERN.matcher(value).matches()) {
            // Parse as number
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Integer.parseInt(value);
            }
        }

        // Keep as string (remove quotes if present)
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }

    /**
     * Compare two values using the specified operator
     */
    private static boolean compare(Object left, String operator, Object right) {
        // Handle numeric comparisons
        if (left instanceof Number && right instanceof Number) {
            double leftNum = ((Number) left).doubleValue();
            double rightNum = ((Number) right).doubleValue();

            switch (operator) {
                case "==": return leftNum == rightNum;
                case "!=": return leftNum != rightNum;
                case ">": return leftNum > rightNum;
                case "<": return leftNum < rightNum;
                case ">=": return leftNum >= rightNum;
                case "<=": return leftNum <= rightNum;
                default: return false;
            }
        }

        // Handle string comparisons
        String leftStr = String.valueOf(left);
        String rightStr = String.valueOf(right);

        switch (operator) {
            case "==": return leftStr.equals(rightStr);
            case "!=": return !leftStr.equals(rightStr);
            case ">": return leftStr.compareTo(rightStr) > 0;
            case "<": return leftStr.compareTo(rightStr) < 0;
            case ">=": return leftStr.compareTo(rightStr) >= 0;
            case "<=": return leftStr.compareTo(rightStr) <= 0;
            default: return false;
        }
    }

    /**
     * For more complex expressions, you can use JavaScript engine
     * Note: This is more powerful but may have performance implications
     */
    public static boolean evaluateComplex(Player player, String expression) {
        // Replace placeholders
        String parsedExpression = PlaceholderAPI.setPlaceholders(player, expression);

        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");
            Object result = engine.eval(parsedExpression);

            if (result instanceof Boolean) {
                return (Boolean) result;
            } else if (result instanceof Number) {
                return ((Number) result).doubleValue() != 0;
            } else {
                return result != null;
            }
        } catch (ScriptException e) {
            e.printStackTrace();
            return false;
        }
    }
}