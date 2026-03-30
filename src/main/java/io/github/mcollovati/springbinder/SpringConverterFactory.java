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

import java.util.Objects;
import java.util.Optional;

import com.vaadin.flow.data.converter.Converter;
import com.vaadin.flow.data.converter.ConverterFactory;
import org.springframework.core.convert.ConversionService;

/**
 * A Vaadin {@link ConverterFactory} implementation based on Spring {@link ConversionService}.
 *
 * <p>If {@link ConversionService} cannot handle a specific conversion, this implementation
 * delegates to the provided fallback {@link ConverterFactory}.
 */
class SpringConverterFactory implements ConverterFactory {

    private final transient ConversionService service;
    private final ConverterFactory fallback;

    /**
     * Creates a new {@link ConverterFactory} based on the provided Spring {@link ConversionService},
     * delegating to the given fallback {@link ConverterFactory} if conversion is not supported.
     *
     * @param service the Spring {@link ConversionService}, not {@literal nukk}.
     * @param fallback the fallback {@link ConverterFactory}, not {@literal null}. {@link
     *     com.vaadin.flow.data.converter.DefaultConverterFactory#INSTANCE} can be used as default.
     */
    public SpringConverterFactory(ConversionService service, ConverterFactory fallback) {
        this.service = Objects.requireNonNull(service, "ConversionService must not be null");
        this.fallback = Objects.requireNonNull(
                fallback,
                "Fallback converter factory must not be null. DefaultConverterFactory.INSTANCE can be used as default.");
    }

    @Override
    public <P, M> Optional<Converter<P, M>> newInstance(Class<P> presentationType, Class<M> modelType) {
        Objects.requireNonNull(presentationType, "presentationType");
        Objects.requireNonNull(modelType, "modelType");
        if (service.canConvert(presentationType, modelType) && service.canConvert(modelType, presentationType)) {
            return Optional.of(Converter.from(
                    o -> modelType.cast(this.service.convert(o, modelType)),
                    o -> presentationType.cast(this.service.convert(o, presentationType)),
                    Throwable::getMessage));
        }
        return fallback.newInstance(presentationType, modelType);
    }
}
