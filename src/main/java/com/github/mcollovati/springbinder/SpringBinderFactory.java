/*
 * Copyright 2026 Marco Collovati
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mcollovati.springbinder;

/**
 * Creates binders configured exactly like the injectable ones, for the cases an injection point
 * cannot cover.
 *
 * <p>Injecting a binder gives one binder per injection point, which is the wrong shape when a
 * component builds a form per row of a grid or a list: reusing a single binder for several rows
 * silently makes every row read and write the same bean, because Vaadin's {@code Binder} allows the
 * same property to be bound more than once. It is also the wrong shape for form components created
 * with {@code new}, which Spring never sees and therefore cannot inject into.
 *
 * <p>This factory is a singleton, so it can be injected once and passed to components Spring does not
 * manage, and it creates binders for any bean type:
 *
 * <pre>{@code
 * class OrderItemsEditor {
 *
 *     private final SpringBinderFactory binders;
 *
 *     OrderItemsEditor(SpringBinderFactory binders) {
 *         this.binders = binders;
 *     }
 *
 *     private OrderItemEditor createEditor() {
 *         return new OrderItemEditor(binders.createBeanValidation(OrderItem.class));
 *     }
 * }
 * }</pre>
 *
 * <p>Binders it creates use the same {@code ConversionService}, the same {@code
 * springbinder.conversion.order} and the same {@code ValidatorFactory} as injected binders,
 * so a form built through the factory behaves like one built by injection. That makes it the way to
 * build binders in tests as well, instead of calling a constructor and getting different conversion
 * behaviour than production.
 *
 * <p><strong>Not serializable.</strong> The factory reaches into the Spring context, so like the
 * binders it creates it cannot be part of a serialized HTTP session. Hold it in a {@code transient}
 * field of a Vaadin component, or take it as a constructor parameter and do not keep it at all. See
 * {@link AbstractSpringBinder} for why this cannot be worked around.
 *
 * @see SpringBinderProvider
 */
public interface SpringBinderFactory {

    /**
     * Creates a binder for the given bean or record type.
     *
     * <p>This is the method every other {@code create} overload ultimately calls, so an implementation
     * only has to provide this one, {@link #createBeanValidation(Class, boolean, ConversionOrder)} and
     * {@link #getConversionOrder()}.
     *
     * @param beanType the bean type to bind, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can, not
     *     {@literal null}.
     * @param <BEAN> the bean type.
     * @return a new binder, never {@literal null}.
     */
    <BEAN> SpringBinder<BEAN> create(
            Class<BEAN> beanType, boolean scanNestedDefinitions, ConversionOrder conversionOrder);

    /**
     * Creates a binder for the given bean or record type that also applies JSR-303 constraints.
     *
     * @param beanType the bean type to bind, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can, not
     *     {@literal null}.
     * @param <BEAN> the bean type.
     * @return a new binder, never {@literal null}.
     * @throws IllegalStateException if no JSR-303 provider is available.
     */
    <BEAN> SpringBeanValidationBinder<BEAN> createBeanValidation(
            Class<BEAN> beanType, boolean scanNestedDefinitions, ConversionOrder conversionOrder);

    /**
     * Returns the conversion order binders get when a call does not name one, which is the configured
     * {@code springbinder.conversion.order}.
     *
     * @return the default conversion order, never {@literal null}.
     */
    ConversionOrder getConversionOrder();

    /**
     * Creates a binder for the given bean or record type.
     *
     * @param beanType the bean type to bind, not {@literal null}.
     * @param <BEAN> the bean type.
     * @return a new binder, never {@literal null}.
     */
    default <BEAN> SpringBinder<BEAN> create(Class<BEAN> beanType) {
        return create(beanType, false, getConversionOrder());
    }

    /**
     * Creates a binder for the given bean or record type, optionally scanning nested properties so
     * that {@code bindInstanceFields} can bind them.
     *
     * @param beanType the bean type to bind, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well.
     * @param <BEAN> the bean type.
     * @return a new binder, never {@literal null}.
     */
    default <BEAN> SpringBinder<BEAN> create(Class<BEAN> beanType, boolean scanNestedDefinitions) {
        return create(beanType, scanNestedDefinitions, getConversionOrder());
    }

    /**
     * Creates a binder for the given bean or record type, overriding the configured conversion order
     * for this binder only.
     *
     * @param beanType the bean type to bind, not {@literal null}.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can, not
     *     {@literal null}.
     * @param <BEAN> the bean type.
     * @return a new binder, never {@literal null}.
     */
    default <BEAN> SpringBinder<BEAN> create(Class<BEAN> beanType, ConversionOrder conversionOrder) {
        return create(beanType, false, conversionOrder);
    }

    /**
     * Creates a binder for the given bean or record type that also applies JSR-303 constraints.
     *
     * @param beanType the bean type to bind, not {@literal null}.
     * @param <BEAN> the bean type.
     * @return a new binder, never {@literal null}.
     * @throws IllegalStateException if no JSR-303 provider is available.
     */
    default <BEAN> SpringBeanValidationBinder<BEAN> createBeanValidation(Class<BEAN> beanType) {
        return createBeanValidation(beanType, false, getConversionOrder());
    }

    /**
     * Creates a binder for the given bean or record type that also applies JSR-303 constraints,
     * optionally scanning nested properties so that {@code bindInstanceFields} can bind them.
     *
     * @param beanType the bean type to bind, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well.
     * @param <BEAN> the bean type.
     * @return a new binder, never {@literal null}.
     * @throws IllegalStateException if no JSR-303 provider is available.
     */
    default <BEAN> SpringBeanValidationBinder<BEAN> createBeanValidation(
            Class<BEAN> beanType, boolean scanNestedDefinitions) {
        return createBeanValidation(beanType, scanNestedDefinitions, getConversionOrder());
    }

    /**
     * Creates a binder for the given bean or record type that also applies JSR-303 constraints,
     * overriding the configured conversion order for this binder only.
     *
     * @param beanType the bean type to bind, not {@literal null}.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can, not
     *     {@literal null}.
     * @param <BEAN> the bean type.
     * @return a new binder, never {@literal null}.
     * @throws IllegalStateException if no JSR-303 provider is available.
     */
    default <BEAN> SpringBeanValidationBinder<BEAN> createBeanValidation(
            Class<BEAN> beanType, ConversionOrder conversionOrder) {
        return createBeanValidation(beanType, false, conversionOrder);
    }
}
