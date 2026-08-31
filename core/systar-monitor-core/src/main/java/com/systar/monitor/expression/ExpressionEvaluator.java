package com.systar.monitor.expression;

/**
 * Compiles expression strings into {@link CompiledExpression} instances.
 */
public interface ExpressionEvaluator {

    /**
     * Compiles the given expression string.
     *
     * @param expression the expression text
     * @return the compiled expression, or {@code null} if the input is blank
     * @throws ExpressionEvaluationException if the expression syntax is invalid
     */
    CompiledExpression compile(String expression);
}
