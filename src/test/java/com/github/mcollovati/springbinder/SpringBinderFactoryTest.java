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

import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.data.converter.StringToDateConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import com.github.mcollovati.springbinder.data.Duration;
import com.github.mcollovati.springbinder.data.Person;
import com.github.mcollovati.springbinder.data.RaceResult;
import com.github.mcollovati.springbinder.fields.TestField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Covers the two ways of getting binders when one injected binder is not enough: the untyped {@link
 * SpringBinderFactory} and the typed {@link SpringBinderProvider}.
 */
class SpringBinderFactoryTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SpringBinderConfiguration.class));

    static class ProviderConsumer {
        @Autowired
        SpringBinderProvider<Person> people;

        @Autowired
        SpringBinderProvider<RaceResult> raceResults;
    }

    static class Form {
        TestField<String> name = new TestField<>(String.class, "");
    }

    @Test
    void factory_createsADistinctBinderPerCall() {
        contextRunner.run(context -> {
            SpringBinderFactory factory = context.getBean(SpringBinderFactory.class);
            SpringBinder<Person> first = factory.create(Person.class);
            SpringBinder<Person> second = factory.create(Person.class);
            assertThat(first).isNotSameAs(second);
        });
    }

    /** The point of the factory: one instance serves any bean type, so it can be passed down once. */
    @Test
    void factory_isASingletonServingEveryBeanType() {
        contextRunner.withBean(ProviderConsumer.class).run(context -> {
            assertThat(context).hasSingleBean(SpringBinderFactory.class);
            SpringBinderFactory factory = context.getBean(SpringBinderFactory.class);
            assertThat(factory.create(Person.class).getBean()).isNull();
            assertThat(factory.createBeanValidation(RaceResult.class)).isNotNull();
            assertThat(factory.create(Duration.class)).isNotNull();
        });
    }

    /** Binders from the factory must convert like injected ones, otherwise tests would not match production. */
    @Test
    void factory_appliesTheConfiguredConversionServiceAndOrder() {
        contextRunner.run(context -> assertThat(durationPresentation(
                        context.getBean(SpringBinderFactory.class).create(RaceResult.class)))
                .isEqualTo("120M"));

        // Vaadin has a String/Date converter and so wins by default; spring-first hands the pair to
        // Spring, whose conversion falls back to toString().
        contextRunner.run(context -> assertThat(datePresentation(
                        context.getBean(SpringBinderFactory.class).create(RaceResult.class)))
                .isEqualTo(new StringToDateConverter().convertToPresentation(new Date(0), new ValueContext())));

        contextRunner
                .withPropertyValues("vaadin-spring-binder.conversion.order=spring-first")
                .run(context -> {
                    SpringBinderFactory factory = context.getBean(SpringBinderFactory.class);
                    assertThat(datePresentation(factory.create(RaceResult.class)))
                            .isEqualTo(new Date(0).toString());
                    assertThat(datePresentation(factory.createBeanValidation(RaceResult.class)))
                            .isEqualTo(new Date(0).toString());
                });
    }

    /** {@code RaceResult.team} is {@code @Size(min = 3, max = 10)}. */
    @Test
    void factory_beanValidationBinderAppliesConstraints() {
        contextRunner.run(context -> {
            SpringBeanValidationBinder<RaceResult> binder =
                    context.getBean(SpringBinderFactory.class).createBeanValidation(RaceResult.class);
            TeamForm form = new TeamForm();
            binder.bindInstanceFields(form);
            binder.setBean(new RaceResult("TEAM1", 3, new Duration(120, "M")));
            assertThat(binder.isValid()).isTrue();

            form.team.setValue("no");
            assertThat(binder.isValid()).isFalse();
        });
    }

    /** Without a provider the failure must name the cause instead of surfacing a missing bean error. */
    @Test
    void factory_noValidationProvider_beanValidationFailsWithAClearMessage() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(
                        new ClassPathResource("META-INF/services/jakarta.validation.spi.ValidationProvider")))
                .run(context -> {
                    SpringBinderFactory factory = context.getBean(SpringBinderFactory.class);
                    assertThat(factory.create(Person.class)).isNotNull();
                    assertThatIllegalStateException()
                            .isThrownBy(() -> factory.createBeanValidation(Person.class))
                            .withMessageContaining("no JSR-303 provider is available");
                });
    }

    @Test
    void provider_resolvesBeanTypeFromInjectionPointAndCreatesDistinctBinders() {
        contextRunner.withBean(ProviderConsumer.class).run(context -> {
            ProviderConsumer consumer = context.getBean(ProviderConsumer.class);

            SpringBinder<Person> first = consumer.people.get();
            SpringBinder<Person> second = consumer.people.get();
            assertThat(first).isNotSameAs(second);

            Form form = new Form();
            first.bindInstanceFields(form);
            Person person = new Person();
            person.setName("Attilio");
            first.setBean(person);
            assertThat(form.name.getValue()).isEqualTo("Attilio");

            assertThat(consumer.raceResults.getBeanValidation()).isNotNull();
            assertThat(durationPresentation(consumer.raceResults.get())).isEqualTo("120M");
        });
    }

    /** Two injection points for different bean types must not share a provider instance. */
    @Test
    void provider_isPrototypeScoped() {
        contextRunner.withBean(ProviderConsumer.class).run(context -> {
            ProviderConsumer consumer = context.getBean(ProviderConsumer.class);
            assertThat(consumer.people).isNotSameAs(consumer.raceResults);
        });
    }

    private static String durationPresentation(SpringBinder<RaceResult> binder) {
        DurationForm form = new DurationForm();
        binder.bindInstanceFields(form);
        binder.setBean(new RaceResult("TEAM1", 3, new Duration(120, "M")));
        return form.duration.getValue();
    }

    private static String datePresentation(AbstractSpringBinder<RaceResult> binder) {
        DateForm form = new DateForm();
        binder.bindInstanceFields(form);
        RaceResult bean = new RaceResult("TEAM1", 3, new Duration(120, "M"));
        bean.setDate(new Date(0));
        binder.setBean(bean);
        return form.date.getValue();
    }

    static class DurationForm {
        TestField<String> duration = new TestField<>(String.class, null);
    }

    static class DateForm {
        TestField<String> date = new TestField<>(String.class, null);
    }

    static class TeamForm {
        TestField<String> team = new TestField<>(String.class, "");
    }
}
