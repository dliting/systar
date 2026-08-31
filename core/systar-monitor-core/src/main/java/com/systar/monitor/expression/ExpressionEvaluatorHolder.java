package com.systar.monitor.expression;

/**
 * Global holder for the {@link ExpressionEvaluator} instance.
 * <p>
 * Uses a volatile field for thread-safe lazy replacement.
 * The default implementation is {@link SpelExpressionEvaluator}.
 */
public final class ExpressionEvaluatorHolder {

    private static volatile ExpressionEvaluator instance = new SpelExpressionEvaluator();

    private ExpressionEvaluatorHolder() {
    }

    public static ExpressionEvaluator getInstance() {
        return instance;
    }

    public static void setInstance(ExpressionEvaluator evaluator) {
        if (evaluator == null) {
            throw new IllegalArgumentException("evaluator must not be null");
        }
        instance = evaluator;
    }

    /** Resets to the default SpEL-based evaluator. */
    public static void reset() {
        instance = new SpelExpressionEvaluator();
    }
}
