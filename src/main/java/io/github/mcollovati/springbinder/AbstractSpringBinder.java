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
package io.github.mcollovati.springbinder;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.converter.ConverterFactory;
import org.springframework.core.convert.ConversionService;

/**
 * Base class for {@link Binder} implementations integrated with Spring {@link ConversionService}.
 *
 * @param <BEAN> the type of the bean.
 */
abstract class AbstractSpringBinder<BEAN> extends Binder<BEAN> {

    private final transient SpringConverterFactory converterFactory;

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param conversionService the conversion service.
     */
    protected AbstractSpringBinder(Class<BEAN> beanType, ConversionService conversionService) {
        super(beanType);
        this.converterFactory = new SpringConverterFactory(conversionService, super.getConverterFactory());
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
        super(beanType, scanNestedDefinitions);
        this.converterFactory = new SpringConverterFactory(conversionService, super.getConverterFactory());
    }

    @Override
    protected ConverterFactory getConverterFactory() {
        return converterFactory;
    }
}
