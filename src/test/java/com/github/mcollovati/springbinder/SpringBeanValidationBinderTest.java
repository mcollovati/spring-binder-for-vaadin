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

import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.data.binder.BindingValidationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.github.mcollovati.springbinder.data.Duration;
import com.github.mcollovati.springbinder.data.RaceResult;
import com.github.mcollovati.springbinder.fields.TestField;

@ContextConfiguration(classes = {SpringBeanValidationBinderTest.Config.class, SpringBinderConfiguration.class})
@ExtendWith({SpringExtension.class})
public class SpringBeanValidationBinderTest extends SpringBinderTest {

    @TestConfiguration
    static class Config {

        @Bean
        LocalValidatorFactoryBean validatorFactoryBean() {
            return new LocalValidatorFactoryBean();
        }

        @Bean
        Converter<String, Duration> stringToDurationConverter() {
            return Duration::valueOf;
        }

        @Bean
        Converter<Duration, String> durationToStringConverter() {
            return Duration::toString;
        }
    }

    @Autowired
    ValidatorFactory validatorFactory;

    @Autowired
    ConversionService conversionService;

    private Locale defaultLocale;

    /**
     * Constraint messages are interpolated for the locale of the current UI, which falls back to the
     * JVM default when there is no UI, as in these tests. Pinning it keeps the expected messages
     * independent of the machine running the build; {@link SpringBeanValidatorTest} covers the
     * translated messages.
     */
    @BeforeEach
    void pinDefaultLocale() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Override
    protected <BEAN> Binder<BEAN> createBinder(Class<BEAN> type, ConversionService service) {

        return new SpringBeanValidationBinder<>(type, service, validatorFactory, ConversionOrder.SPRING_FIRST);
    }

    @Test
    public void beanBound_setInvalidFieldValue_validationError(
            @Autowired SpringBeanValidationBinder<RaceResult> binder) {
        Duration duration = new Duration(120, "M");
        RaceResult result = new RaceResult("TEAM1", 3, duration);
        binder.setBean(result);

        SpringBinderTest.Form form = new SpringBinderTest.Form();
        binder.bindInstanceFields(form);
        binder.setBean(result);

        form.team.setValue("TE"); // too short

        Assertions.assertEquals("TEAM1", result.getTeam());
        assertInvalid(form.team, binder, "size must be between 3 and 10");
    }

    @Test
    public void beanNotBound_setInvalidFieldValue_validationError(
            @Autowired SpringBeanValidationBinder<RaceResult> binder) {
        Duration duration = new Duration(120, "M");
        RaceResult result = new RaceResult("TEAM1", 3, duration);
        binder.setBean(result);

        SpringBinderTest.Form form = new SpringBinderTest.Form();
        binder.bindInstanceFields(form);

        form.team.setValue("TOO_LONG_TEAM_NAME"); // too long

        assertInvalid(form.team, binder, "size must be between 3 and 10");
    }

    /**
     * Constraints on a nested property belong to the nested type, not to the root bean, so the
     * validator has to be created for the type actually declaring the property.
     */
    @Test
    void nestedProperty_validatorUsesTheDeclaringType() {
        SpringBeanValidationBinder<RaceResult> binder =
                new SpringBeanValidationBinder<>(RaceResult.class, true, conversionService, validatorFactory);
        TestField<String> timeUnit = new TestField<>(String.class, null);
        binder.forField(timeUnit).bind("duration.timeUnit");
        binder.setBean(new RaceResult("TEAM1", 3, new Duration(120, "M")));

        timeUnit.setValue("");

        assertInvalid(timeUnit, binder, "must not be empty");
    }

    /**
     * The required indicator is only shown when the empty value of the field would itself break the
     * constraint, which is why the fields below start empty rather than null.
     */
    static class RequiredForm {
        TestField<String> team = new TestField<>(String.class, "");
        TestField<String> duration = new TestField<>(String.class, "");
    }

    @Test
    void requiredIndicator_setFromConstraints() {
        SpringBeanValidationBinder<RaceResult> binder =
                new SpringBeanValidationBinder<>(RaceResult.class, conversionService, validatorFactory);
        RequiredForm form = new RequiredForm();

        binder.bindInstanceFields(form);

        Assertions.assertTrue(form.team.isRequiredIndicatorVisible(), "team is annotated with @Size(min = 3)");
        Assertions.assertFalse(form.duration.isRequiredIndicatorVisible(), "duration has no constraint of its own");
    }

    @Test
    void requiredIndicator_notConfiguredWhenConfiguratorIsNull() {
        SpringBeanValidationBinder<RaceResult> binder =
                new SpringBeanValidationBinder<>(RaceResult.class, conversionService, validatorFactory);
        binder.setRequiredConfigurator(null);
        RequiredForm form = new RequiredForm();

        binder.bindInstanceFields(form);

        Assertions.assertNull(binder.getRequiredConfigurator());
        Assertions.assertFalse(form.team.isRequiredIndicatorVisible());
    }

    private void assertInvalid(TestField field, Binder<?> binder, String message) {
        BinderValidationStatus<?> status = binder.validate();
        List<BindingValidationStatus<?>> errors = status.getFieldValidationErrors();
        Assertions.assertEquals(1, errors.size());
        Assertions.assertSame(field, errors.get(0).getField());
        Assertions.assertEquals(message, errors.get(0).getMessage().get());
    }
}
