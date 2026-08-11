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
import com.vaadin.flow.data.binder.RequiredFieldConfigurator;
import com.vaadin.flow.data.validator.BeanValidator;
import org.springframework.core.convert.ConversionService;

/**
 * A {@link com.vaadin.flow.data.binder.Binder} extension integrated with Spring provided {@link
 * ConversionService} and {@link ValidatorFactory}. The Binder automatically adds {@link
 * BeanValidator} which validates beans using JSR-303 specification.
 *
 * <p>Spring {@link ConversionService} is used to provide suitable converters for bindings * when
 * presentation and model types are not compatible. The {@link ValidatorFactory} enables JSR-303
 * validation.
 *
 * @param <BEAN> the type of the bean.
 */
public class SpringBeanValidationBinder<BEAN> extends AbstractSpringBinder<BEAN> {

    private final Class<BEAN> beanType;
    private final ValidatorFactory validatorFactory;
    private RequiredFieldConfigurator requiredConfigurator = RequiredFieldConfigurator.DEFAULT;

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param validatorFactory
     */
    public SpringBeanValidationBinder(
            Class<BEAN> beanType, ConversionService conversionService, ValidatorFactory validatorFactory) {
        super(beanType, conversionService);
        this.beanType = beanType;
        this.validatorFactory = validatorFactory;
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well
     * @param conversionService the conversion service.
     */
    public SpringBeanValidationBinder(
            Class<BEAN> beanType,
            boolean scanNestedDefinitions,
            ConversionService conversionService,
            ValidatorFactory validatorFactory) {
        super(beanType, scanNestedDefinitions, conversionService);
        this.beanType = beanType;
        this.validatorFactory = validatorFactory;
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
    public void setRequiredConfigurator(RequiredFieldConfigurator configurator) {
        requiredConfigurator = configurator;
    }

    /**
     * Gets field required indicator configuration logic.
     *
     * @see #setRequiredConfigurator(RequiredFieldConfigurator)
     * @return required indicator configurator, may be {@code null}
     */
    public RequiredFieldConfigurator getRequiredConfigurator() {
        return requiredConfigurator;
    }

    @Override
    protected BindingBuilder<BEAN, ?> configureBinding(
            BindingBuilder<BEAN, ?> binding, PropertyDefinition<BEAN, ?> definition) {
        Class<?> actualBeanType = findBeanType(beanType, definition);
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
     * @param beanType the root beanType
     * @param definition the definition for the property
     * @return the bean type containing the given property
     */
    @SuppressWarnings({"rawtypes"})
    private Class<?> findBeanType(Class<BEAN> beanType, PropertyDefinition<BEAN, ?> definition) {
        if (definition instanceof BeanPropertySet.NestedBeanPropertyDefinition) {
            return ((BeanPropertySet.NestedBeanPropertyDefinition) definition)
                    .getParent()
                    .getType();
        } else {
            // Non nested properties must be defined in the main type
            return beanType;
        }
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
