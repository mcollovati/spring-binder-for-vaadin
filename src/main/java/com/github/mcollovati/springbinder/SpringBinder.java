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
import org.springframework.core.convert.ConversionService;

/**
 * A {@link com.vaadin.flow.data.binder.Binder} extension integrated with Spring
 * {@link ConversionService}.
 *
 * <p>Spring {@link ConversionService} is used to provide suitable converters for bindings when
 * presentation and model types are not compatible.
 *
 * @param <BEAN> the type of the bean.
 *
 * @since 1.0
 */
public class SpringBinder<BEAN> extends AbstractSpringBinder<BEAN> {

    /**
     * Creates a new binder for the given bean or record type, using the shared
     * {@link org.springframework.core.convert.support.DefaultConversionService}.
     *
     * @param beanType the bean type to use, not {@literal null}.
     */
    public SpringBinder(Class<BEAN> beanType) {
        this(beanType, sharedConversionService());
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService}
     * to provide suitable converters for bindings when presentation and model types are not
     * compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param conversionService the conversion service.
     */
    public SpringBinder(Class<BEAN> beanType, ConversionService conversionService) {
        super(beanType, conversionService);
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService}
     * to provide suitable converters for bindings when presentation and model types are not
     * compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     */
    public SpringBinder(Class<BEAN> beanType, ConversionService conversionService, ConversionOrder conversionOrder) {
        super(beanType, conversionService, conversionOrder);
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService}
     * to provide suitable converters for bindings when presentation and model types are not
     * compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well
     * @param conversionService the conversion service.
     */
    public SpringBinder(Class<BEAN> beanType, boolean scanNestedDefinitions, ConversionService conversionService) {
        super(beanType, scanNestedDefinitions, conversionService);
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService}
     * to provide suitable converters for bindings when presentation and model types are not
     * compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well
     * @param conversionService the conversion service.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     */
    public SpringBinder(
            Class<BEAN> beanType,
            boolean scanNestedDefinitions,
            ConversionService conversionService,
            ConversionOrder conversionOrder) {
        super(beanType, scanNestedDefinitions, conversionService, conversionOrder);
    }

    /**
     * Creates a new binder using the given property set.
     *
     * @param propertySet the property set implementation to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     */
    protected SpringBinder(
            PropertySet<BEAN> propertySet, ConversionService conversionService, ConversionOrder conversionOrder) {
        super(propertySet, conversionService, conversionOrder);
    }

    /**
     * Creates a new binder using the given property set, for parity with
     * {@link Binder#withPropertySet(PropertySet)}.
     *
     * @param propertySet the property set implementation to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param <BEAN> the bean type.
     * @return a new binder using the given property set, never {@literal null}.
     */
    public static <BEAN> SpringBinder<BEAN> withPropertySet(
            PropertySet<BEAN> propertySet, ConversionService conversionService) {
        return withPropertySet(propertySet, conversionService, ConversionOrder.VAADIN_FIRST);
    }

    /**
     * Creates a new binder using the given property set, for parity with
     * {@link Binder#withPropertySet(PropertySet)}.
     *
     * @param propertySet the property set implementation to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     * @param <BEAN> the bean type.
     * @return a new binder using the given property set, never {@literal null}.
     */
    public static <BEAN> SpringBinder<BEAN> withPropertySet(
            PropertySet<BEAN> propertySet, ConversionService conversionService, ConversionOrder conversionOrder) {
        return new SpringBinder<>(propertySet, conversionService, conversionOrder);
    }
}
