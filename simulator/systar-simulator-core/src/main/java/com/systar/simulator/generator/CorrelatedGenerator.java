package com.systar.simulator.generator;

import java.util.HashMap;
import java.util.Map;

public class CorrelatedGenerator implements DataGenerator {
    private String                expression;
    private Map<String, String>  references = new HashMap<>();

    public CorrelatedGenerator() {}

    @Override
    public Object generate(GenerationContext context) {
        Map<String, Double> peerValues = new HashMap<>();
        for (Map.Entry<String, String> entry : references.entrySet()) {
            context.getPeerValue(entry.getValue()).ifPresent(v -> {
                if (v instanceof Number n) {
                    peerValues.put(entry.getValue(), n.doubleValue());
                }
            });
        }
        ExpressionEvaluator evaluator = new ExpressionEvaluator(expression, references);
        return evaluator.evaluate(peerValues, context.random());
    }

    public String getExpression()                         { return expression; }
    public void setExpression(String expression)          { this.expression = expression; }
    public Map<String, String> getReferences()            { return references; }
    public void setReferences(Map<String, String> refs)   { this.references = refs; }
}
