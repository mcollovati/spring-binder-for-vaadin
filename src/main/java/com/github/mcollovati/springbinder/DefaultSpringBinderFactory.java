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

import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.ConversionService;

/**
 * Builds binders with the wiring the auto-configuration resolves for injected binders.
 *
 * <p>The conversion service and the validator factory are looked up on every call rather than kept,
 * so binders created here see the same beans an injected binder would, whenever those beans were
 * registered.
 */
class DefaultSpringBinderFactory implements SpringBinderFactory {

    private final Supplier<ConversionService> conversionService;
    private final ObjectProvider<BinderValidatorFactory> validatorFactory;
    private final SpringBinderProperties properties;

    DefaultSpringBinderFactory(
            Supplier<ConversionService> conversionService,
            ObjectProvider<BinderValidatorFactory> validatorFactory,
            SpringBinderProperties properties) {
        this.conversionService = conversionService;
        this.validatorFactory = validatorFactory;
        this.properties = properties;
    }

    @Override
    public ConversionOrder getConversionOrder() {
        return properties.getConversion().getOrder();
    }

    @Override
    public <BEAN> SpringBinder<BEAN> create(
            Class<BEAN> beanType, boolean scanNestedDefinitions, ConversionOrder conversionOrder) {
        return new SpringBinder<>(beanType, scanNestedDefinitions, conversionService.get(), conversionOrder);
    }

    @Override
    public <BEAN> SpringBeanValidationBinder<BEAN> createBeanValidation(
            Class<BEAN> beanType, boolean scanNestedDefinitions, ConversionOrder conversionOrder) {
        return new SpringBeanValidationBinder<>(
                beanType,
                scanNestedDefinitions,
                conversionService.get(),
                requireValidatorFactory().get(),
                conversionOrder);
    }

    /**
     * @return the validator factory support, never {@literal null}.
     * @throws IllegalStateException when bean validation is not available, rather than letting the
     *     caller see an unhelpful missing bean error.
     */
    private BinderValidatorFactory requireValidatorFactory() {
        BinderValidatorFactory factory = validatorFactory.getIfAvailable();
        if (factory == null) {
            throw new IllegalStateException("Cannot create a bean validation binder: no JSR-303 provider is "
                    + "available. Add a validation provider, for example by depending on "
                    + "spring-boot-starter-validation, or use create(Class) instead.");
        }
        return factory;
    }
}
