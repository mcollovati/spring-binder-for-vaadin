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

import com.github.mcollovati.springbinder.data.Person;
import com.vaadin.flow.data.binder.Binder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers how the auto-configuration resolves the binder for an injection point. */
class SpringBinderAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SpringBinderConfiguration.class));

    @Configuration(proxyBeanMethods = false)
    static class WithValidation {
        @Bean
        LocalValidatorFactoryBean validatorFactoryBean() {
            return new LocalValidatorFactoryBean();
        }
    }

    /** Injecting the base {@link Binder} type must keep working when bean validation is available. */
    static class BaseBinderConsumer {
        @Autowired
        Binder<Person> binder;
    }

    static class ConcreteBindersConsumer {
        @Autowired
        SpringBinder<Person> springBinder;

        @Autowired
        SpringBeanValidationBinder<Person> validationBinder;
    }

    @Test
    void validatorFactoryPresent_baseBinderTypeResolvesToValidationBinder() {
        contextRunner
                .withUserConfiguration(WithValidation.class)
                .withBean(BaseBinderConsumer.class)
                .withBean(ConcreteBindersConsumer.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ValidatorFactory.class);
                    assertThat(context.getBean(BaseBinderConsumer.class).binder)
                            .isInstanceOf(SpringBeanValidationBinder.class);
                    ConcreteBindersConsumer consumer = context.getBean(ConcreteBindersConsumer.class);
                    assertThat(consumer.springBinder).isExactlyInstanceOf(SpringBinder.class);
                    assertThat(consumer.validationBinder).isExactlyInstanceOf(SpringBeanValidationBinder.class);
                });
    }

    @Test
    void noValidatorFactory_baseBinderTypeResolvesToPlainBinder() {
        contextRunner.withBean(BaseBinderConsumer.class).run(context -> assertThat(
                        context.getBean(BaseBinderConsumer.class).binder)
                .isExactlyInstanceOf(SpringBinder.class));
    }

    /** The validation binder must not be required for the auto-configuration to load. */
    @Test
    void beanValidationApiMissing_contextStartsWithPlainBinder() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("jakarta.validation"))
                .withBean(BaseBinderConsumer.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(BaseBinderConsumer.class).binder)
                            .isExactlyInstanceOf(SpringBinder.class);
                });
    }
}
