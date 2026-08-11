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

import java.util.Locale;

import com.vaadin.flow.data.binder.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.github.mcollovati.springbinder.data.Duration;
import com.github.mcollovati.springbinder.data.RaceEntry;
import com.github.mcollovati.springbinder.fields.TestField;

import static org.assertj.core.api.Assertions.assertThat;

/** Records are bound like beans, including the conversions provided by Spring. */
class RecordBindingTest {

    static class Form {
        TestField<String> team = new TestField<>(String.class, null);
        TestField<String> duration = new TestField<>(String.class, null);
    }

    private final ConversionService conversionService = DefaultConversionService.getSharedInstance();
    private Locale defaultLocale;

    @BeforeEach
    void pinDefaultLocale() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void record_readAndWritten_withSpringConversion() throws ValidationException {
        SpringBinder<RaceEntry> binder = new SpringBinder<>(RaceEntry.class, conversionService);
        Form form = new Form();
        binder.bindInstanceFields(form);

        binder.readRecord(new RaceEntry("TEAM1", new Duration(120, "M")));
        assertThat(form.team.getValue()).isEqualTo("TEAM1");
        assertThat(form.duration.getValue()).isEqualTo("120M");

        form.duration.setValue("45S");
        RaceEntry written = binder.writeRecord();

        assertThat(written.team()).isEqualTo("TEAM1");
        assertThat(written.duration()).isEqualTo(new Duration(45, "S"));
    }

    @Test
    void record_constraintsAreValidated() {
        LocalValidatorFactoryBean validatorFactory = new LocalValidatorFactoryBean();
        validatorFactory.afterPropertiesSet();
        SpringBeanValidationBinder<RaceEntry> binder =
                new SpringBeanValidationBinder<>(RaceEntry.class, conversionService, validatorFactory);
        Form form = new Form();
        binder.bindInstanceFields(form);
        binder.readRecord(new RaceEntry("TEAM1", new Duration(120, "M")));

        form.team.setValue("TE");

        assertThat(binder.validate().getFieldValidationErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getMessage()).hasValue("size must be between 3 and 10"));
    }
}
