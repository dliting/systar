package com.systar.simulator.generator;

import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal expression evaluator for CorrelatedGenerator.
 * Supports: arithmetic (+, -, *, /), parentheses, variable references,
 * and the function noise(stdDev).
 */
public class ExpressionEvaluator {

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("([a-zA-Z_]\\w*)|([0-9]*\\.?[0-9]+)|([+\\-*/()])|(,)");

    private final String             expression;
    private final Map<String, String> references;

    public ExpressionEvaluator(String expression, Map<String, String> references) {
        this.expression  = expression.trim();
        this.references  = references;
    }

    public double evaluate(Map<String, Double> peerValues, Random random) {
        String expr = substituteReferences(expression, peerValues);
        expr = substituteNoise(expr, random);
        return evalSimple(expr);
    }

    private String substituteReferences(String expr, Map<String, Double> peerValues) {
        for (Map.Entry<String, String> entry : references.entrySet()) {
            String alias   = entry.getKey();
            String pointId = entry.getValue();
            Double value   = peerValues.get(pointId);
            if (value != null) {
                // Use word boundaries to avoid partial matches (e.g. alias "a" within "abc")
                expr = expr.replaceAll("\\b" + java.util.regex.Pattern.quote(alias) + "\\b",
                                        String.valueOf(value));
            }
        }
        return expr;
    }

    private String substituteNoise(String expr, Random random) {
        Pattern  noisePattern = Pattern.compile("noise\\(([^)]+)\\)");
        Matcher m            = noisePattern.matcher(expr);
        StringBuilder sb     = new StringBuilder();
        while (m.find()) {
            double stdDev = Double.parseDouble(m.group(1).trim());
            m.appendReplacement(sb, String.valueOf(random.nextGaussian() * stdDev));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private double evalSimple(String expr) {
        expr = expr.trim();
        // Handle addition/subtraction (lowest precedence)
        int parenDepth = 0;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') parenDepth++;
            if (c == '(') parenDepth--;
            if (parenDepth == 0 && (c == '+' || c == '-')) {
                double left  = evalSimple(expr.substring(0, i));
                double right = evalSimple(expr.substring(i + 1));
                return c == '+' ? left + right : left - right;
            }
        }
        // Handle multiplication/division
        parenDepth = 0;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') parenDepth++;
            if (c == '(') parenDepth--;
            if (parenDepth == 0 && (c == '*' || c == '/')) {
                double left  = evalSimple(expr.substring(0, i));
                double right = evalSimple(expr.substring(i + 1));
                return c == '*' ? left * right : left / right;
            }
        }
        // Handle unary minus
        if (expr.startsWith("-")) {
            return -evalSimple(expr.substring(1));
        }
        // Handle parentheses
        if (expr.startsWith("(") && expr.endsWith(")")) {
            return evalSimple(expr.substring(1, expr.length() - 1));
        }
        return Double.parseDouble(expr);
    }
}
