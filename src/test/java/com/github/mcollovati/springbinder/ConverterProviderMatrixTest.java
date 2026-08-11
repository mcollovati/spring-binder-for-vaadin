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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.vaadin.flow.data.converter.Converter;
import com.vaadin.flow.data.converter.ConverterFactory;
import com.vaadin.flow.data.converter.DefaultConverterFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;

import com.github.mcollovati.springbinder.data.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which side provides the converter for each type pair, so that a change in either library, or
 * in the resolution rule, shows up as a failing row rather than as a subtly different value in a
 * form.
 *
 * <p>Spring reports many more conversions as supported than one would expect: {@code
 * ObjectToObjectConverter} matches any type with a {@code valueOf} method or a {@code String}
 * constructor, and everything converts to {@link String} through {@code toString()}. That is why
 * {@code String <-> Date} and the numeric pairs below are claimed by both sides.
 */
class ConverterProviderMatrixTest {

    enum Provider {
        /** Vaadin's own converter, locale aware and with readable error messages. */
        VAADIN,
        /** A converter backed by the Spring conversion service. */
        SPRING,
        /** Neither side can convert the pair, so the binding needs an explicit converter. */
        NONE
    }

    private final ConversionService conversionService = DefaultConversionService.getSharedInstance();

    static Stream<Arguments> pairs() {
        return Stream.of(
                // pair                            VAADIN_FIRST      SPRING_FIRST
                // Known to both: Vaadin wins by default, Spring only when asked to
                Arguments.of(String.class, Integer.class, Provider.VAADIN, Provider.SPRING),
                Arguments.of(String.class, Long.class, Provider.VAADIN, Provider.SPRING),
                Arguments.of(String.class, BigDecimal.class, Provider.VAADIN, Provider.SPRING),
                Arguments.of(String.class, Boolean.class, Provider.VAADIN, Provider.SPRING),
                Arguments.of(String.class, Date.class, Provider.VAADIN, Provider.SPRING),
                Arguments.of(String.class, UUID.class, Provider.VAADIN, Provider.SPRING),
                Arguments.of(Integer.class, Long.class, Provider.VAADIN, Provider.SPRING),
                Arguments.of(Float.class, Double.class, Provider.VAADIN, Provider.SPRING),
                Arguments.of(BigDecimal.class, Integer.class, Provider.VAADIN, Provider.SPRING),
                // Known to Vaadin only, so the order makes no difference
                Arguments.of(LocalDate.class, Date.class, Provider.VAADIN, Provider.VAADIN),
                Arguments.of(LocalDateTime.class, Date.class, Provider.VAADIN, Provider.VAADIN),
                Arguments.of(Date.class, Long.class, Provider.VAADIN, Provider.VAADIN),
                // Known to Spring only, which is what the add-on exists for
                Arguments.of(String.class, Duration.class, Provider.SPRING, Provider.SPRING),
                Arguments.of(Integer.class, String.class, Provider.SPRING, Provider.SPRING),
                Arguments.of(Date.class, String.class, Provider.SPRING, Provider.SPRING),
                Arguments.of(BigInteger.class, BigDecimal.class, Provider.SPRING, Provider.SPRING),
                // Known to neither: a plain conversion service has no date and time formatters
                Arguments.of(String.class, LocalDate.class, Provider.NONE, Provider.NONE));
    }

    @ParameterizedTest(name = "{0} <-> {1}")
    @MethodSource("pairs")
    void converterProviderMatchesTheConfiguredOrder(
            Class<?> presentationType, Class<?> modelType, Provider vaadinFirst, Provider springFirst) {
        assertThat(providerOf(factory(conversionService, ConversionOrder.VAADIN_FIRST), presentationType, modelType))
                .as("VAADIN_FIRST")
                .isEqualTo(vaadinFirst);
        assertThat(providerOf(factory(conversionService, ConversionOrder.SPRING_FIRST), presentationType, modelType))
                .as("SPRING_FIRST")
                .isEqualTo(springFirst);
    }

    /**
     * A conversion service holding only explicitly registered converters keeps Spring out of the
     * pairs it was never asked about, even with {@link ConversionOrder#SPRING_FIRST}.
     */
    @ParameterizedTest(name = "{0} <-> {1}")
    @MethodSource("pairs")
    void minimalRegistry_springOnlyProvidesWhatWasRegistered(
            Class<?> presentationType, Class<?> modelType, Provider vaadinFirst, Provider springFirst) {
        GenericConversionService registered = new GenericConversionService();
        registered.addConverter(String.class, Duration.class, Duration::valueOf);
        registered.addConverter(Duration.class, String.class, Duration::toString);

        Provider expected = presentationType == String.class && modelType == Duration.class
                ? Provider.SPRING
                : vaadinFirst == Provider.VAADIN ? Provider.VAADIN : Provider.NONE;

        assertThat(providerOf(factory(registered, ConversionOrder.SPRING_FIRST), presentationType, modelType))
                .isEqualTo(expected);
    }

    private static ConverterFactory factory(ConversionService conversionService, ConversionOrder order) {
        return new SpringConverterFactory(conversionService, DefaultConverterFactory.INSTANCE, order);
    }

    /**
     * Identifies the provider by comparing the converter with the one Vaadin's factory would return
     * for the same pair.
     */
    private static Provider providerOf(ConverterFactory factory, Class<?> presentationType, Class<?> modelType) {
        Optional<? extends Converter<?, ?>> actual = newInstance(factory, presentationType, modelType);
        if (actual.isEmpty()) {
            return Provider.NONE;
        }
        Optional<? extends Converter<?, ?>> vaadin =
                newInstance(DefaultConverterFactory.INSTANCE, presentationType, modelType);
        return vaadin.isPresent() && vaadin.get().getClass().equals(actual.get().getClass())
                ? Provider.VAADIN
                : Provider.SPRING;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<? extends Converter<?, ?>> newInstance(
            ConverterFactory factory, Class<?> presentationType, Class<?> modelType) {
        return factory.newInstance((Class) presentationType, (Class) modelType);
    }
}
