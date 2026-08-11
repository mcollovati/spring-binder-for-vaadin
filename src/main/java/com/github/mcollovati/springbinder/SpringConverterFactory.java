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
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;

/**
 * A Vaadin {@link ConverterFactory} implementation based on Spring {@link ConversionService}.
 *
 * <p>Which of the two is asked first is defined by {@link ConversionOrder}; whichever is asked
 * second is used for the conversions the first one cannot provide.
 *
 * @since 1.0
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
     * {@code transient} because a {@link ConversionService} is generally not serializable, and this
     * factory has to be: every converter it hands to a binding captures it, so a bound field would
     * otherwise drag the conversion service into the serialized session and fail the write.
     *
     * <p>It is therefore {@literal null} on a binder restored from a serialized session, and {@link
     * #conversionService()} reports that rather than letting a {@link NullPointerException} surface
     * somewhere else. See {@link AbstractSpringBinder} for what a view has to do about it.
     */
    private final transient @Nullable ConversionService service;

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
        ConversionService conversionService = conversionService();
        if (!conversionService.canConvert(presentationType, modelType)
                || !conversionService.canConvert(modelType, presentationType)) {
            return Optional.empty();
        }
        return Optional.of(Converter.from(
                value -> convert(value, modelType),
                value -> convert(value, presentationType),
                SpringConverterFactory::conversionErrorMessage));
    }

    private <T> @Nullable T convert(@Nullable Object value, Class<T> targetType) {
        Object converted = conversionService().convert(value, targetType);
        return converted != null ? wrapperType(targetType).cast(converted) : null;
    }

    /**
     * Returns the conversion service, and explains itself when there is none.
     *
     * <p>The only way to reach a {@literal null} here is a factory that came back from a serialized
     * session, where the service was skipped as {@code transient}. Saying so beats the {@link
     * NullPointerException} the caller would get otherwise, since the cause — a session written and
     * restored, possibly on another node — is nowhere near the failing conversion.
     *
     * @return the conversion service, never {@literal null}.
     * @throws IllegalStateException when the factory was restored from a serialized session.
     */
    private ConversionService conversionService() {
        if (service == null) {
            throw new IllegalStateException("This binder has no ConversionService because it was restored from a "
                    + "serialized session, which does not carry one. Build the form again from a freshly injected "
                    + "binder or from SpringBinderFactory instead of reusing a restored one.");
        }
        return service;
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
