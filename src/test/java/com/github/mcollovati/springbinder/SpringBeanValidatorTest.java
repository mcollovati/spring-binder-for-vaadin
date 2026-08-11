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

import jakarta.validation.ConstraintViolation;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.github.mcollovati.springbinder.data.RaceResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the reason this validator exists: constraint messages are interpolated by the Spring
 * {@code ValidatorFactory}, so they follow the requested locale and can come from a Spring {@code
 * MessageSource}.
 */
class SpringBeanValidatorTest {

    private static final String SIZE_MESSAGE_KEY = "jakarta.validation.constraints.Size.message";

    @Test
    void interpolatesMessageInTheRequestedLocale() {
        LocalValidatorFactoryBean validatorFactory = validatorFactory(null);
        SpringBeanValidator validator = new SpringBeanValidator(RaceResult.class, "team", validatorFactory);
        ConstraintViolation<?> violation = tooShortTeamViolation(validatorFactory);

        String english = validator.getMessage(violation, Locale.ENGLISH);
        String italian = validator.getMessage(violation, Locale.ITALIAN);

        assertThat(english).isEqualTo("size must be between 3 and 10");
        assertThat(italian)
                .as("the same violation must be reported in the requested language")
                .isNotEqualTo(english)
                .contains("3", "10");
    }

    @Test
    void interpolatesMessageFromSpringMessageSource() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage(SIZE_MESSAGE_KEY, Locale.ENGLISH, "between {min} and {max} characters, please");
        LocalValidatorFactoryBean validatorFactory = validatorFactory(messages);
        SpringBeanValidator validator = new SpringBeanValidator(RaceResult.class, "team", validatorFactory);

        String message = validator.getMessage(tooShortTeamViolation(validatorFactory), Locale.ENGLISH);

        assertThat(message).isEqualTo("between 3 and 10 characters, please");
    }

    private static LocalValidatorFactoryBean validatorFactory(StaticMessageSource messageSource) {
        LocalValidatorFactoryBean validatorFactory = new LocalValidatorFactoryBean();
        if (messageSource != null) {
            validatorFactory.setValidationMessageSource(messageSource);
        }
        validatorFactory.afterPropertiesSet();
        return validatorFactory;
    }

    private static ConstraintViolation<?> tooShortTeamViolation(LocalValidatorFactoryBean validatorFactory) {
        return validatorFactory.getValidator().validateValue(RaceResult.class, "team", "TE").stream()
                .findFirst()
                .orElseThrow();
    }
}
