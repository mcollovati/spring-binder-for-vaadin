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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.vaadin.flow.data.converter.Converter;
import com.vaadin.flow.data.converter.ConverterFactory;
import org.springframework.core.convert.ConversionService;

/**
 * A Vaadin {@link ConverterFactory} implementation based on Spring {@link ConversionService}.
 *
 * <p>Which of the two is asked first is defined by {@link ConversionOrder}; whichever is asked
 * second is used for the conversions the first one cannot provide.
 */
public class SpringConverterFactory implements ConverterFactory {

    private static final Map<Class<?>, Class<?>> WRAPPER_TYPES = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            char.class, Character.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class);

    /**
     * Deliberately not {@code transient}. A {@link ConversionService} is generally not serializable,
     * and skipping it would produce a factory that deserializes into one unable to convert anything.
     * Keeping it in the serialized form means the attempt fails immediately, naming the service.
     */
    private final ConversionService service;

    private final ConverterFactory fallback;
    private final ConversionOrder order;

    /**
     * Creates a new {@link ConverterFactory} based on the provided Spring {@link ConversionService},
     * using {@link ConversionOrder#VAADIN_FIRST}.
     *
     * @param service the Spring {@link ConversionService}, not {@literal null}.
     * @param fallback the Vaadin {@link ConverterFactory}, not {@literal null}. {@link
     *     com.vaadin.flow.data.converter.DefaultConverterFactory#INSTANCE} can be used as default.
     */
    public SpringConverterFactory(ConversionService service, ConverterFactory fallback) {
        this(service, fallback, ConversionOrder.VAADIN_FIRST);
    }

    /**
     * Creates a new {@link ConverterFactory} based on the provided Spring {@link ConversionService}.
     *
     * @param service the Spring {@link ConversionService}, not {@literal null}.
     * @param fallback the Vaadin {@link ConverterFactory}, not {@literal null}. {@link
     *     com.vaadin.flow.data.converter.DefaultConverterFactory#INSTANCE} can be used as default.
     * @param order which of the two is consulted first, not {@literal null}.
     */
    public SpringConverterFactory(ConversionService service, ConverterFactory fallback, ConversionOrder order) {
        this.service = Objects.requireNonNull(service, "ConversionService must not be null");
        this.fallback = Objects.requireNonNull(
                fallback,
                "Fallback converter factory must not be null. DefaultConverterFactory.INSTANCE can be used as default.");
        this.order = Objects.requireNonNull(order, "ConversionOrder must not be null");
    }

    /**
     * Gets the order in which Spring and Vaadin converters are considered.
     *
     * @return the conversion order, never {@literal null}.
     */
    public ConversionOrder getConversionOrder() {
        return order;
    }

    @Override
    public <P, M> Optional<Converter<P, M>> newInstance(Class<P> presentationType, Class<M> modelType) {
        Objects.requireNonNull(presentationType, "presentationType");
        Objects.requireNonNull(modelType, "modelType");
        if (order == ConversionOrder.VAADIN_FIRST) {
            return fallback.<P, M>newInstance(presentationType, modelType)
                    .or(() -> springConverter(presentationType, modelType));
        }
        return springConverter(presentationType, modelType).or(() -> fallback.newInstance(presentationType, modelType));
    }

    /**
     * Creates a converter backed by the {@link ConversionService}, if it supports both directions.
     *
     * <p>Both directions are required because a binding always has to convert back and forth. Using
     * Spring for one direction and Vaadin for the other could produce a value that does not survive
     * a round trip.
     */
    private <P, M> Optional<Converter<P, M>> springConverter(Class<P> presentationType, Class<M> modelType) {
        if (!service.canConvert(presentationType, modelType) || !service.canConvert(modelType, presentationType)) {
            return Optional.empty();
        }
        return Optional.of(Converter.from(
                value -> convert(value, modelType),
                value -> convert(value, presentationType),
                SpringConverterFactory::conversionErrorMessage));
    }

    private <T> T convert(Object value, Class<T> targetType) {
        Object converted = service.convert(value, targetType);
        return converted != null ? wrapperType(targetType).cast(converted) : null;
    }

    /**
     * Returns the wrapper type for primitives, so that casting the converted value does not fail on
     * a primitive property type.
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> wrapperType(Class<T> type) {
        return type.isPrimitive() ? (Class<T>) WRAPPER_TYPES.get(type) : type;
    }

    /**
     * Builds the message shown when a conversion fails.
     *
     * <p>Spring wraps conversion failures in a {@link
     * org.springframework.core.convert.ConversionFailedException} describing source and target
     * types, which means little to the person filling in the form. The message of the original
     * failure is used instead, when there is one.
     */
    private static String conversionErrorMessage(Exception error) {
        Throwable rootCause = error;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getMessage();
        }
        return message == null || message.isBlank() ? "Invalid value" : message;
    }
}
