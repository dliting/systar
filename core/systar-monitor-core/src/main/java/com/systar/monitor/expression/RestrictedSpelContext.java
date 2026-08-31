package com.systar.monitor.expression;

import org.springframework.expression.BeanResolver;
import org.springframework.expression.ConstructorResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.OperatorOverloader;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypeComparator;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A restricted SpEL evaluation context that only allows variable access
 * and property reads on known safe types (e.g. {@link ProbeRef#value()}).
 * <p>
 * Security restrictions:
 * <ul>
 *   <li>Type references ({@code T(java.lang.Runtime)}) are blocked</li>
 *   <li>Constructor calls ({@code new ...}) are blocked</li>
 *   <li>Method calls on arbitrary objects are blocked</li>
 *   <li>Bean references ({@code @bean}) are blocked</li>
 *   <li>Property access is restricted to Map and ProbeRef only</li>
 * </ul>
 */
class RestrictedSpelContext implements EvaluationContext {

    private static final TypeLocator BLOCKING_TYPE_LOCATOR = typeName -> {
        throw new ExpressionEvaluationException(
                "Type reference not allowed: T(" + typeName + ")");
    };

    private static final BeanResolver BLOCKING_BEAN_RESOLVER = (ctx, name) -> {
        throw new org.springframework.expression.AccessException("Bean reference not allowed: @" + name);
    };

    /** Types whose properties may be read (e.g. ProbeRef.value). */
    private static final Set<Class<?>> ALLOWED_PROPERTY_TYPES = Set.of(
            ProbeRef.class
    );

    private final StandardEvaluationContext delegate;
    private final List<PropertyAccessor> accessors;

    RestrictedSpelContext(Map<String, Object> variables) {
        this.delegate = new StandardEvaluationContext();
        if (variables != null) {
            variables.forEach(delegate::setVariable);
        }
        // MapAccessor for #probe[id] index access, plus a restricted accessor for ProbeRef.value
        this.accessors = List.of(new RestrictedPropertyAccessor());
    }

    @Override
    public TypedValue getRootObject() {
        return TypedValue.NULL;
    }

    @Override
    public List<PropertyAccessor> getPropertyAccessors() {
        return accessors;
    }

    @Override
    public List<ConstructorResolver> getConstructorResolvers() {
        return Collections.emptyList();
    }

    @Override
    public List<MethodResolver> getMethodResolvers() {
        return Collections.singletonList(new RestrictedMethodResolver());
    }

    @Override
    public BeanResolver getBeanResolver() {
        return BLOCKING_BEAN_RESOLVER;
    }

    @Override
    public TypeLocator getTypeLocator() {
        return BLOCKING_TYPE_LOCATOR;
    }

    @Override
    public TypeConverter getTypeConverter() {
        return delegate.getTypeConverter();
    }

    @Override
    public TypeComparator getTypeComparator() {
        return delegate.getTypeComparator();
    }

    @Override
    public OperatorOverloader getOperatorOverloader() {
        return delegate.getOperatorOverloader();
    }

    @Override
    public void setVariable(String name, Object value) {
        delegate.setVariable(name, value);
    }

    @Override
    public Object lookupVariable(String name) {
        return delegate.lookupVariable(name);
    }

    /**
     * Property accessor that only allows reads on whitelisted types.
     * Blocks access to .getClass(), .hashCode(), .toString() etc. on arbitrary objects.
     */
    private static class RestrictedPropertyAccessor implements PropertyAccessor {

        private static final Set<String> ALLOWED_PROPERTIES = Set.of("value");

        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return ALLOWED_PROPERTY_TYPES.toArray(new Class<?>[0]);
        }

        @Override
        public boolean canRead(EvaluationContext context, Object target, String name) {
            return target != null
                    && ALLOWED_PROPERTY_TYPES.contains(target.getClass())
                    && ALLOWED_PROPERTIES.contains(name);
        }

        @Override
        public TypedValue read(EvaluationContext context, Object target, String name) {
            if (!canRead(context, target, name)) {
                throw new ExpressionEvaluationException(
                        "Property access denied: " + target.getClass().getSimpleName() + "." + name);
            }
            // Use reflection to access the property on allowed types
            try {
                java.lang.reflect.Method method = target.getClass().getMethod(name);
                Object value = method.invoke(target);
                return new TypedValue(value);
            } catch (NoSuchMethodException e) {
                throw new ExpressionEvaluationException(
                        "Property not found: " + target.getClass().getSimpleName() + "." + name);
            } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                throw new ExpressionEvaluationException(
                        "Property access failed: " + target.getClass().getSimpleName() + "." + name, e);
            }
        }

        @Override
        public boolean canWrite(EvaluationContext context, Object target, String name) {
            return false;
        }

        @Override
        public void write(EvaluationContext context, Object target, String name, Object newValue) {
            throw new UnsupportedOperationException("Read-only accessor");
        }
    }
}