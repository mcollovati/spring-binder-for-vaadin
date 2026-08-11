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

import jakarta.validation.ValidatorFactory;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.data.binder.BeanPropertySet;
import com.vaadin.flow.data.binder.PropertyDefinition;
import com.vaadin.flow.data.binder.PropertySet;
import com.vaadin.flow.data.binder.RequiredFieldConfigurator;
import com.vaadin.flow.data.validator.BeanValidator;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;

/**
 * A {@link com.vaadin.flow.data.binder.Binder} extension integrated with Spring provided {@link
 * ConversionService} and {@link ValidatorFactory}. The Binder automatically adds {@link
 * BeanValidator} which validates beans using JSR-303 specification.
 *
 * <p>Spring {@link ConversionService} supplies the converter when the type of a field and the type of
 * the property it is bound to differ, and the {@link ValidatorFactory} enables JSR-303 validation.
 * Conversion applies to the properties bound by {@code bindInstanceFields}; {@code bind(field,
 * "property")} and {@code forField(field).bind("property")} do not consult it, and fail on a type
 * mismatch as they would without this add-on. Validation, unlike conversion, applies to all of them.
 *
 * <h2>Session serialization</h2>
 *
 * <p>This binder is serializable, but neither the {@link ConversionService} it converts through nor the
 * {@link ValidatorFactory} it validates with is, so both are {@code transient}. A binder that comes back
 * from a serialized session <strong>can neither convert nor validate</strong>: the first attempt fails
 * with an {@link IllegalStateException} saying so, and the validators its bindings carry do the same.
 *
 * <p>Declaring the field that holds it {@code transient} does not keep it out of the session — Vaadin
 * registers a value change listener on every bound field and that listener holds the binder — so a view
 * that survives session serialization has to build its form again rather than reuse what came back.
 * {@link AbstractSpringBinder} has the full contract, including what session replication tooling
 * changes about it.
 *
 * @param <BEAN> the type of the bean.
 *
 * @since 1.0
 */
public class SpringBeanValidationBinder<BEAN> extends AbstractSpringBinder<BEAN> {

    /**
     * {@code transient} for the same reason as in {@link SpringConverterFactory}: the binder travels
     * with a serialized session through the fields it bound, and a {@link ValidatorFactory} cannot.
     */
    private final transient @Nullable ValidatorFactory validatorFactory;

    private @Nullable RequiredFieldConfigurator requiredConfigurator = RequiredFieldConfigurator.DEFAULT;

    /**
     * Creates a new binder for the given bean or record type, using the shared {@link
     * org.springframework.core.convert.support.DefaultConversionService}.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param validatorFactory the factory providing the bean validator and the message interpolator.
     */
    public SpringBeanValidationBinder(Class<BEAN> beanType, ValidatorFactory validatorFactory) {
        this(beanType, sharedConversionService(), validatorFactory);
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param validatorFactory the factory providing the bean validator and the message interpolator.
     */
    public SpringBeanValidationBinder(
            Class<BEAN> beanType, ConversionService conversionService, ValidatorFactory validatorFactory) {
        super(beanType, conversionService);
        this.validatorFactory = validatorFactory;
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param validatorFactory the factory providing the bean validator and the message interpolator.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     */
    public SpringBeanValidationBinder(
            Class<BEAN> beanType,
            ConversionService conversionService,
            ValidatorFactory validatorFactory,
            ConversionOrder conversionOrder) {
        super(beanType, conversionService, conversionOrder);
        this.validatorFactory = validatorFactory;
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well
     * @param conversionService the conversion service.
     * @param validatorFactory the factory providing the bean validator and the message interpolator.
     */
    public SpringBeanValidationBinder(
            Class<BEAN> beanType,
            boolean scanNestedDefinitions,
            ConversionService conversionService,
            ValidatorFactory validatorFactory) {
        super(beanType, scanNestedDefinitions, conversionService);
        this.validatorFactory = validatorFactory;
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well
     * @param conversionService the conversion service.
     * @param validatorFactory the factory providing the bean validator and the message interpolator.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     */
    public SpringBeanValidationBinder(
            Class<BEAN> beanType,
            boolean scanNestedDefinitions,
            ConversionService conversionService,
            ValidatorFactory validatorFactory,
            ConversionOrder conversionOrder) {
        super(beanType, scanNestedDefinitions, conversionService, conversionOrder);
        this.validatorFactory = validatorFactory;
    }

    /**
     * Creates a new binder using the given property set.
     *
     * @param propertySet the property set implementation to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param validatorFactory the factory providing the bean validator and the message interpolator.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     */
    protected SpringBeanValidationBinder(
            PropertySet<BEAN> propertySet,
            ConversionService conversionService,
            ValidatorFactory validatorFactory,
            ConversionOrder conversionOrder) {
        super(propertySet, conversionService, conversionOrder);
        this.validatorFactory = validatorFactory;
    }

    /**
     * Creates a new binder using the given property set, for parity with {@link
     * com.vaadin.flow.data.binder.Binder#withPropertySet(PropertySet)}.
     *
     * @param propertySet the property set implementation to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param validatorFactory the factory providing the bean validator and the message interpolator.
     * @param <BEAN> the bean type.
     * @return a new binder using the given property set, never {@literal null}.
     */
    public static <BEAN> SpringBeanValidationBinder<BEAN> withPropertySet(
            PropertySet<BEAN> propertySet, ConversionService conversionService, ValidatorFactory validatorFactory) {
        return new SpringBeanValidationBinder<>(
                propertySet, conversionService, validatorFactory, ConversionOrder.VAADIN_FIRST);
    }

    /**
     * Sets a logic which allows to configure require indicator via {@link
     * HasValue#setRequiredIndicatorVisible(boolean)} based on property descriptor.
     *
     * <p>Required indicator configuration will not be used at all if {@code configurator} is null.
     *
     * <p>By default, the {@link RequiredFieldConfigurator#DEFAULT} configurator is used.
     *
     * @param configurator required indicator configurator, may be {@code null}
     */
    public void setRequiredConfigurator(@Nullable RequiredFieldConfigurator configurator) {
        requiredConfigurator = configurator;
    }

    /**
     * Gets field required indicator configuration logic.
     *
     * @see #setRequiredConfigurator(RequiredFieldConfigurator)
     * @return required indicator configurator, may be {@code null}
     */
    public @Nullable RequiredFieldConfigurator getRequiredConfigurator() {
        return requiredConfigurator;
    }

    @Override
    protected BindingBuilder<BEAN, ?> configureBinding(
            BindingBuilder<BEAN, ?> binding, PropertyDefinition<BEAN, ?> definition) {
        Class<?> actualBeanType = findBeanType(definition);
        BeanValidator validator =
                new SpringBeanValidator(actualBeanType, definition.getTopLevelName(), validatorFactory);
        if (requiredConfigurator != null) {
            configureRequired(binding, definition, validator);
        }
        return binding.withValidator(validator);
    }

    /**
     * Finds the bean type containing the property the given definition refers to.
     *
     * @param definition the definition for the property
     * @return the bean type containing the given property
     */
    @SuppressWarnings({"rawtypes"})
    private Class<?> findBeanType(PropertyDefinition<BEAN, ?> definition) {
        if (definition instanceof BeanPropertySet.NestedBeanPropertyDefinition) {
            return ((BeanPropertySet.NestedBeanPropertyDefinition) definition)
                    .getParent()
                    .getType();
        }
        Class<BEAN> knownBeanType = findBeanType().orElse(null);
        if (knownBeanType != null) {
            // Non nested properties must be defined in the main type
            return knownBeanType;
        }
        // Binder created from a property set, so the bean type is not known upfront
        return definition.getPropertyHolderType();
    }

    private void configureRequired(
            BindingBuilder<BEAN, ?> binding, PropertyDefinition<BEAN, ?> definition, BeanValidator validator) {
        assert requiredConfigurator != null;
        Class<?> propertyHolderType = definition.getPropertyHolderType();
        BeanDescriptor descriptor = validator.getJavaxBeanValidator().getConstraintsForClass(propertyHolderType);
        PropertyDescriptor propertyDescriptor = descriptor.getConstraintsForProperty(definition.getTopLevelName());
        if (propertyDescriptor == null) {
            return;
        }
        if (propertyDescriptor.getConstraintDescriptors().stream()
                .map(ConstraintDescriptor::getAnnotation)
                .anyMatch(constraint -> requiredConfigurator.test(constraint, binding))) {
            binding.getField().setRequiredIndicatorVisible(true);
        }
    }
}
