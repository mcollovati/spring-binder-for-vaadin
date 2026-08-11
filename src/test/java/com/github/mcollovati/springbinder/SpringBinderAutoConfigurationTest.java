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
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
import org.springframework.core.io.ClassPathResource;
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

    /**
     * Hibernate Validator reaches most applications transitively through {@code vaadin-spring}, but a
     * {@link ValidatorFactory} bean only exists with {@code spring-boot-starter-validation}. Vaadin's
     * own {@code BeanValidationBinder} validates fine in that setup, so the add-on must too instead of
     * quietly omitting the binder and failing when a view is instantiated.
     */
    @Test
    void noValidatorFactoryBean_validationProviderOnClasspath_validationBinderIsAvailable() {
        contextRunner
                .withBean(BaseBinderConsumer.class)
                .withBean(ConcreteBindersConsumer.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ValidatorFactory.class);
                    assertThat(context).hasSingleBean(BinderValidatorFactory.class);
                    assertThat(context.getBean(BaseBinderConsumer.class).binder)
                            .isInstanceOf(SpringBeanValidationBinder.class);
                    assertThat(context.getBean(ConcreteBindersConsumer.class).validationBinder)
                            .isExactlyInstanceOf(SpringBeanValidationBinder.class);
                });
    }

    /** Without a JSR-303 provider nothing can be validated, so only the plain binder is registered. */
    @Test
    void noValidationProvider_baseBinderTypeResolvesToPlainBinder() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(
                        new ClassPathResource("META-INF/services/jakarta.validation.spi.ValidationProvider")))
                .withBean(BaseBinderConsumer.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(BinderValidatorFactory.class);
                    assertThat(context.getBean(BaseBinderConsumer.class).binder)
                            .isExactlyInstanceOf(SpringBinder.class);
                });
    }

    /** The application's own validator factory must win over the one the add-on falls back to. */
    @Test
    void validatorFactoryPresent_applicationFactoryIsUsed() {
        contextRunner.withUserConfiguration(WithValidation.class).run(context -> {
            ValidatorFactory applicationFactory = context.getBean(ValidatorFactory.class);
            assertThat(context.getBean(BinderValidatorFactory.class).get()).isSameAs(applicationFactory);
        });
    }

    /** Contributes a validator factory only after the binders have been configured. */
    @AutoConfiguration(after = SpringBinderConfiguration.class)
    static class LateValidationAutoConfiguration {
        @Bean
        LocalValidatorFactoryBean lateValidatorFactory() {
            return new LocalValidatorFactoryBean();
        }
    }

    /**
     * A validator factory registered by an auto-configuration ordered after this one — {@code
     * mvcValidator} is the usual case — is invisible while conditions are evaluated. Binders are
     * prototypes, so resolving the factory when a binder is created picks it up regardless of order.
     */
    @Test
    void validatorFactoryRegisteredLate_isStillUsed() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(SpringBinderConfiguration.class, LateValidationAutoConfiguration.class))
                .withBean(BaseBinderConsumer.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(BaseBinderConsumer.class).binder)
                            .isInstanceOf(SpringBeanValidationBinder.class);
                    assertThat(context.getBean(BinderValidatorFactory.class).get())
                            .isSameAs(context.getBean(ValidatorFactory.class));
                });
    }

    /** The fallback factory is owned by the add-on, so it must be built once and then reused. */
    @Test
    void noValidatorFactoryBean_fallbackFactoryIsCreatedOnce() {
        contextRunner.run(context -> {
            BinderValidatorFactory factory = context.getBean(BinderValidatorFactory.class);
            assertThat(factory.get()).isSameAs(factory.get());
        });
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

    /**
     * The add-on must not publish a {@link ConversionService} of its own: an unqualified one changes
     * what the rest of the application injects, and satisfies the {@code ConditionalOnMissingBean} of
     * any auto-configuration that contributes one. It keeps the service it converts with to itself.
     */
    @Test
    void noConversionServiceBean_noneIsPublished() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ConversionService.class);
        });
    }

    /**
     * The headline promise of the add-on. It holds in a servlet application only because Spring Boot
     * collects {@code Converter} beans into {@code mvcConversionService} itself; nothing collects them
     * in a plain context, so the add-on has to.
     */
    @Test
    void converterBeans_areUsedByInjectedBinders() {
        contextRunner
                .withUserConfiguration(ConverterBeans.class, BinderHolder.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertConverterBeansAreUsed(context.getBean(BinderHolder.class).binder);
                });
    }

    @Test
    void webApplication_converterBeansAreUsedByInjectedBinders() {
        new WebApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(WebMvcAutoConfiguration.class, SpringBinderConfiguration.class))
                .withUserConfiguration(ConverterBeans.class, BinderHolder.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("mvcConversionService");
                    assertConverterBeansAreUsed(context.getBean(BinderHolder.class).binder);
                });
    }

    /**
     * Ambiguity must not cost the application its conversions. The binders cannot tell which service
     * was meant, but the service they fall back to still holds every {@code Converter} bean.
     */
    @Test
    void ambiguousConversionServices_converterBeansAreStillUsed() {
        contextRunner
                .withUserConfiguration(ConverterBeans.class, TwoConversions.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertConverterBeansAreUsed(context.getBean(BinderHolder.class).binder);
                });
    }

    /**
     * The qualifier states which service the binders must use, so more than one carrying it is a
     * contradiction. It has to fail at startup: ignoring it quietly would convert through a service
     * the application explicitly did not choose.
     */
    @Test
    void severalQualifiedConversionServices_contextFailsToStart() {
        contextRunner.withUserConfiguration(TwoQualifiedConversions.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("Several ConversionService beans are annotated with "
                            + "@BinderConversionService [first, second]");
        });
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

    /**
     * The prefix is the point: nothing but these two converters produces or understands it, so a round
     * trip through it cannot be satisfied by Spring's {@code valueOf} based {@code
     * ObjectToObjectConverter} or by the {@code toString()} fallback.
     */
    @Configuration(proxyBeanMethods = false)
    static class ConverterBeans {

        private static final String PREFIX = "BEAN-";

        @Bean
        Converter<Duration, String> durationToString() {
            return duration -> PREFIX + duration;
        }

        @Bean
        Converter<String, Duration> stringToDuration() {
            return text -> Duration.valueOf(text.startsWith(PREFIX) ? text.substring(PREFIX.length()) : text);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoQualifiedConversions {
        @Bean
        @BinderConversionService
        ConversionService first() {
            return new DefaultFormattingConversionService();
        }

        @Bean
        @BinderConversionService
        ConversionService second() {
            return new DefaultFormattingConversionService();
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

    private static void assertConverterBeansAreUsed(SpringBinder<RaceResult> binder) {
        DurationForm form = new DurationForm();
        binder.bindInstanceFields(form);
        RaceResult bean = new RaceResult("TEAM1", 3, new Duration(120, "M"));
        binder.setBean(bean);

        assertThat(form.duration.getValue()).isEqualTo("BEAN-120M");

        form.duration.setValue("BEAN-90S");
        assertThat(bean.getDuration()).isEqualTo(new Duration(90, "S"));
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
