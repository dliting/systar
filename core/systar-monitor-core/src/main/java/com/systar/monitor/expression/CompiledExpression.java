package com.systar.monitor.expression;

import java.util.Map;

/**
 * A compiled expression that can be evaluated against a set of variables.
 */
public interface CompiledExpression {

    /**
     * Evaluates the expression with the given variable bindings.
     *
     * @param variables name-to-value bindings available in the expression context
     * @return the evaluation result
     */
    Object evaluate(Map<String, Object> variables);
}
