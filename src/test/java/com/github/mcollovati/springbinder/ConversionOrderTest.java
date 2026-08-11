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

import java.util.Date;
import java.util.List;

import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.data.binder.BindingValidationStatus;
import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.data.converter.DefaultConverterFactory;
import com.vaadin.flow.data.converter.StringToDateConverter;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import com.github.mcollovati.springbinder.data.Duration;
import com.github.mcollovati.springbinder.data.RaceResult;
import com.github.mcollovati.springbinder.fields.TestField;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which side converts a value, using a real {@link ConversionService} rather than a mock.
 *
 * <p>A plain {@link DefaultConversionService} reports far more conversions as supported than one
 * would expect: {@code String <-> Date} is supported through the deprecated {@code Date(String)}
 * constructor and {@code Date.toString()}, so without a defined order Spring silently replaces
 * Vaadin's locale aware date converter.
 */
class ConversionOrderTest {

    private final ConversionService conversionService = DefaultConversionService.getSharedInstance();

    static class Form {
        TestField<String> date = new TestField<>(String.class, null);
        TestField<String> duration = new TestField<>(String.class, null);
    }

    @Test
    void vaadinFirst_pairKnownByVaadin_vaadinConverterWins() {
        Form form = bind(new SpringBinder<>(RaceResult.class, conversionService), raceResult());

        assertThat(form.date.getValue())
                .isEqualTo(new StringToDateConverter().convertToPresentation(new Date(0), new ValueContext()));
    }

    @Test
    void springFirst_pairKnownByVaadin_springConverterWins() {
        Form form = bind(
                new SpringBinder<>(RaceResult.class, conversionService, ConversionOrder.SPRING_FIRST), raceResult());

        assertThat(form.date.getValue()).isEqualTo(new Date(0).toString());
    }

    @Test
    void vaadinFirstIsTheDefault() {
        SpringConverterFactory factory =
                new SpringConverterFactory(conversionService, DefaultConverterFactory.INSTANCE);

        assertThat(factory.getConversionOrder()).isEqualTo(ConversionOrder.VAADIN_FIRST);
    }

    @Test
    void typeUnknownToVaadin_springConverterUsedWithBothOrders() {
        for (ConversionOrder order : ConversionOrder.values()) {
            Form form = bind(new SpringBinder<>(RaceResult.class, conversionService, order), raceResult());

            assertThat(form.duration.getValue())
                    .as("duration presentation with %s", order)
                    .isEqualTo("120M");

            form.duration.setValue("45S");
            assertThat(form.duration.getValue())
                    .as("duration round trip with %s", order)
                    .isEqualTo("45S");
        }
    }

    @Test
    void springConversionFails_rootCauseMessageIsReported() {
        SpringBinder<RaceResult> binder = new SpringBinder<>(RaceResult.class, conversionService);
        Form form = bind(binder, raceResult());

        form.duration.setValue("not-a-duration");

        BinderValidationStatus<RaceResult> status = binder.validate();
        List<BindingValidationStatus<?>> errors = status.getFieldValidationErrors();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .as("the message of the original failure, not Spring's type description")
                .hasValueSatisfying(message -> assertThat(message).contains("is not a valid duration"));
    }

    private Form bind(SpringBinder<RaceResult> binder, RaceResult bean) {
        Form form = new Form();
        binder.bindInstanceFields(form);
        binder.setBean(bean);
        return form;
    }

    private RaceResult raceResult() {
        RaceResult result = new RaceResult("TEAM1", 3, new Duration(120, "M"));
        result.setDate(new Date(0));
        return result;
    }
}
