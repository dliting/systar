package com.systar.monitor.expression;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

/**
 * Spring SpEL-based implementation of {@link ExpressionEvaluator}.
 * <p>
 * Uses a restricted evaluation context that only allows variable references
 * (e.g. {@code #probe}, {@code #value}) and basic arithmetic/comparison operators.
 * Type references ({@code T(...)}) and arbitrary method calls are blocked.
 */
public class SpelExpressionEvaluator implements ExpressionEvaluator {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Override
    public CompiledExpression compile(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            org.springframework.expression.Expression spelExpr = parser.parseExpression(expression);
            return new SpelCompiledExpression(spelExpr);
        } catch (Exception e) {
            throw new ExpressionEvaluationException(
                    "Failed to compile expression: " + expression, e);
        }
    }

    private static class SpelCompiledExpression implements CompiledExpression {

        private final org.springframework.expression.Expression spelExpr;

        SpelCompiledExpression(org.springframework.expression.Expression spelExpr) {
            this.spelExpr = spelExpr;
        }

        @Override
        public Object evaluate(Map<String, Object> variables) {
            EvaluationContext ctx = new RestrictedSpelContext(variables);
            return spelExpr.getValue(ctx);
        }
    }
}
