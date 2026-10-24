package com.systar.monitor.expression;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.TypedValue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * A restricted method resolver that only allows accessor methods on
 * known safe types (e.g. {@link ProbeRef#value()}).
 * <p>
 * This prevents arbitrary method calls like {@code Runtime.getRuntime().exec(...)}.
 */
class RestrictedMethodResolver implements MethodResolver {

    private static final Set<Class<?>> ALLOWED_TYPES = Set.of(
            ProbeRef.class
    );

    @Override
    public MethodExecutor resolve(EvaluationContext context, Object targetObject,
                                  String name, List<TypeDescriptor> argumentTypes) {
        if (targetObject == null) {
            return null;
        }
        Class<?> targetClass = targetObject.getClass();
        if (!ALLOWED_TYPES.contains(targetClass)) {
            return null;
        }
        try {
            Method method = targetClass.getMethod(name);
            if (method.getReturnType() == void.class) {
                return null;
            }
            return (ctx, target, arguments) -> {
                try {
                    Object result = method.invoke(target);
                    return new TypedValue(result, TypeDescriptor.valueOf(method.getReturnType()));
                } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                    throw new ExpressionEvaluationException(
                            "Method invocation failed: " + method.getName(), e);
                }
            };
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
