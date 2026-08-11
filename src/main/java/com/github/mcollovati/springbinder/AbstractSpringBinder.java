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

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.PropertySet;
import com.vaadin.flow.data.converter.ConverterFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * Base class for {@link Binder} implementations integrated with Spring {@link ConversionService}.
 *
 * @param <BEAN> the type of the bean.
 */
public abstract class AbstractSpringBinder<BEAN> extends Binder<BEAN> {

    private final transient SpringConverterFactory converterFactory;

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param conversionService the conversion service.
     */
    protected AbstractSpringBinder(Class<BEAN> beanType, ConversionService conversionService) {
        this(beanType, conversionService, ConversionOrder.VAADIN_FIRST);
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     */
    protected AbstractSpringBinder(
            Class<BEAN> beanType, ConversionService conversionService, ConversionOrder conversionOrder) {
        super(beanType);
        this.converterFactory = createConverterFactory(conversionService, conversionOrder);
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well
     * @param conversionService the conversion service.
     */
    protected AbstractSpringBinder(
            Class<BEAN> beanType, boolean scanNestedDefinitions, ConversionService conversionService) {
        this(beanType, scanNestedDefinitions, conversionService, ConversionOrder.VAADIN_FIRST);
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well
     * @param conversionService the conversion service.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     */
    protected AbstractSpringBinder(
            Class<BEAN> beanType,
            boolean scanNestedDefinitions,
            ConversionService conversionService,
            ConversionOrder conversionOrder) {
        super(beanType, scanNestedDefinitions);
        this.converterFactory = createConverterFactory(conversionService, conversionOrder);
    }

    /**
     * Creates a new binder using the given property set, for parity with {@link
     * Binder#withPropertySet(PropertySet)}.
     *
     * @param propertySet the property set implementation to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     */
    protected AbstractSpringBinder(
            PropertySet<BEAN> propertySet, ConversionService conversionService, ConversionOrder conversionOrder) {
        super(propertySet);
        this.converterFactory = createConverterFactory(conversionService, conversionOrder);
    }

    private SpringConverterFactory createConverterFactory(
            ConversionService conversionService, ConversionOrder conversionOrder) {
        return new SpringConverterFactory(conversionService, super.getConverterFactory(), conversionOrder);
    }

    /**
     * Returns the shared {@link DefaultConversionService}, used by the constructors that do not take
     * a conversion service.
     *
     * @return the shared conversion service, never {@literal null}.
     */
    protected static ConversionService sharedConversionService() {
        return DefaultConversionService.getSharedInstance();
    }

    @Override
    protected ConverterFactory getConverterFactory() {
        return converterFactory;
    }
}
