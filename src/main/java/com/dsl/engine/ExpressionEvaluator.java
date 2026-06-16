package com.dsl.engine;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ExpressionEvaluator {

    /**
     * Evaluate a simple condition expression like:
     * gender == "M"
     * age > 18
     * status != null
     * name contains "test"
     */
    public boolean evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) return true;

        expression = expression.trim();

        // Handle AND/OR
        if (expression.contains(" && ")) {
            String[] parts = expression.split(" && ");
            for (String part : parts) {
                if (!evaluate(part.trim(), context)) return false;
            }
            return true;
        }
        if (expression.contains(" || ")) {
            String[] parts = expression.split(" || ");
            for (String part : parts) {
                if (evaluate(part.trim(), context)) return true;
            }
            return false;
        }

        // Handle NOT
        if (expression.startsWith("!")) {
            return !evaluate(expression.substring(1).trim(), context);
        }

        // Replace variables with values
        String resolved = resolveVariables(expression, context);

        // Parse comparison
        return evaluateComparison(resolved);
    }

    private String resolveVariables(String expression, Map<String, Object> context) {
        String result = expression;
        // Sort by key length descending to avoid partial replacements (e.g. "code" before "gender_code")
        List<Map.Entry<String, Object>> sorted = new ArrayList<>(context.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        for (Map.Entry<String, Object> entry : sorted) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String replacement = value == null ? "null" : String.valueOf(value);
            // Skip internal keys
            if (key.startsWith("__")) continue;
            // Replace ${varName} format
            result = result.replace("${" + key + "}", replacement);
            // Replace bare varName with word boundary check
            result = result.replaceAll("(?<![\\w.])" + Pattern.quote(key) + "(?![\\w.])", replacement);
        }
        return result;
    }

    private boolean evaluateComparison(String expression) {
        // Try different operators
        String[] operators = {"!=", ">=", "<=", "==", ">", "<", " contains ", " startsWith ", " endsWith "};
        for (String op : operators) {
            int idx = expression.indexOf(op);
            if (idx > 0) {
                String left = expression.substring(0, idx).trim();
                String right = expression.substring(idx + op.length()).trim();

                // Remove quotes
                left = unquote(left);
                right = unquote(right);

                switch (op.trim()) {
                    case "==": return safeEquals(left, right);
                    case "!=": return !safeEquals(left, right);
                    case ">": return safeCompare(left, right) > 0;
                    case "<": return safeCompare(left, right) < 0;
                    case ">=": return safeCompare(left, right) >= 0;
                    case "<=": return safeCompare(left, right) <= 0;
                    case "contains": return left != null && left.contains(right);
                    case "startsWith": return left != null && left.startsWith(right);
                    case "endsWith": return left != null && left.endsWith(right);
                }
            }
        }

        // Boolean check
        if ("true".equalsIgnoreCase(expression)) return true;
        if ("false".equalsIgnoreCase(expression)) return false;
        if ("null".equals(expression)) return false;

        // Truthy check
        return expression != null && !expression.isEmpty() && !"null".equals(expression);
    }

    private String unquote(String s) {
        if (s == null) return null;
        s = s.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return "null".equals(a) || "null".equals(b);
        return a.equals(b);
    }

    private int safeCompare(String a, String b) {
        try {
            double da = Double.parseDouble(a);
            double db = Double.parseDouble(b);
            return Double.compare(da, db);
        } catch (NumberFormatException e) {
            if (a == null) a = "";
            if (b == null) b = "";
            return a.compareTo(b);
        }
    }
}
