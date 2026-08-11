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
import java.util.Date;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.data.converter.StringToDateConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.github.mcollovati.springbinder.data.Duration;
import com.github.mcollovati.springbinder.data.Person;
import com.github.mcollovati.springbinder.data.RaceResult;
import com.github.mcollovati.springbinder.fields.TestField;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers how the auto-configuration resolves binders and the conversion service they use. */
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
        contextRunner
                .withBean(BaseBinderConsumer.class)
                .run(context -> assertThat(context.getBean(BaseBinderConsumer.class).binder)
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

    @Test
    void noConversionServiceBean_oneIsRegistered() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ConversionService.class));
    }

    @Test
    void qualifiedConversionServiceIsPreferred() {
        contextRunner.withUserConfiguration(QualifiedConversions.class).run(context -> {
            assertThat(durationPresentation(context.getBean(BinderHolder.class).binder))
                    .isEqualTo("qualified-120M");
        });
    }

    /**
     * Several conversion service beans without a primary one used to break the binder injection point
     * with a {@code NoUniqueBeanDefinitionException}. The binder now falls back to the shared
     * conversion service instead.
     */
    @Test
    void ambiguousConversionServices_binderStillWorks() {
        contextRunner.withUserConfiguration(TwoConversions.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(durationPresentation(context.getBean(BinderHolder.class).binder))
                    .isEqualTo("120M");
        });
    }

    /**
     * A Spring Boot web application already provides a {@code ConversionService}, the format aware
     * {@code mvcConversionService}. The add-on must not register a second one, so that the binder and
     * the rest of the application convert values the same way, honouring {@code spring.mvc.format.*}.
     */
    @Test
    void webApplication_applicationConversionServiceIsUsed() {
        new WebApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(WebMvcAutoConfiguration.class, SpringBinderConfiguration.class))
                .withUserConfiguration(BinderHolder.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("mvcConversionService");
                    assertThat(context).doesNotHaveBean("conversionServiceFactoryBean");
                    assertThat(context).hasSingleBean(ConversionService.class);
                    assertThat(durationPresentation(context.getBean(BinderHolder.class).binder))
                            .isEqualTo("120M");
                });
    }

    @Test
    void conversionOrderProperty_isApplied() {
        contextRunner
                .withUserConfiguration(BinderHolder.class)
                .run(context -> assertThat(datePresentation(context.getBean(BinderHolder.class).binder))
                        .isEqualTo(new StringToDateConverter().convertToPresentation(new Date(0), new ValueContext())));

        contextRunner
                .withUserConfiguration(BinderHolder.class)
                .withPropertyValues("vaadin-spring-binder.conversion.order=spring-first")
                .run(context -> assertThat(datePresentation(context.getBean(BinderHolder.class).binder))
                        .isEqualTo(new Date(0).toString()));
    }

    @Configuration(proxyBeanMethods = false)
    static class QualifiedConversions {
        @Bean
        ConversionService applicationConversionService() {
            return new DefaultFormattingConversionService();
        }

        @Bean
        @BinderConversionService
        ConversionService binderConversionService() {
            GenericConversionService conversions = new GenericConversionService();
            conversions.addConverter(String.class, Duration.class, Duration::valueOf);
            conversions.addConverter(
                    Duration.class, String.class, (Converter<Duration, String>) duration -> "qualified-" + duration);
            return conversions;
        }

        @Bean
        BinderHolder binderHolder(SpringBinder<RaceResult> binder) {
            return new BinderHolder(binder);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoConversions {
        @Bean
        ConversionService first() {
            return new DefaultFormattingConversionService();
        }

        @Bean
        ConversionService second() {
            return new DefaultFormattingConversionService();
        }

        @Bean
        BinderHolder binderHolder(SpringBinder<RaceResult> binder) {
            return new BinderHolder(binder);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BinderHolder {
        final SpringBinder<RaceResult> binder;

        BinderHolder(SpringBinder<RaceResult> binder) {
            this.binder = binder;
        }
    }

    static class DurationForm {
        TestField<String> duration = new TestField<>(String.class, null);
    }

    static class DateForm {
        TestField<String> date = new TestField<>(String.class, null);
    }

    private static String durationPresentation(SpringBinder<RaceResult> binder) {
        DurationForm form = new DurationForm();
        binder.bindInstanceFields(form);
        binder.setBean(new RaceResult("TEAM1", 3, new Duration(120, "M")));
        return form.duration.getValue();
    }

    private static String datePresentation(SpringBinder<RaceResult> binder) {
        DateForm form = new DateForm();
        binder.bindInstanceFields(form);
        RaceResult bean = new RaceResult("TEAM1", 3, new Duration(120, "M"));
        bean.setDate(new Date(0));
        binder.setBean(bean);
        return form.date.getValue();
    }
}
