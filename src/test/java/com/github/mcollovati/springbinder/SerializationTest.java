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
import java.io.ByteArrayOutputStream;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.github.mcollovati.springbinder.data.RaceResult;
import com.github.mcollovati.springbinder.fields.TestField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Pins down what happens when the add-on's types end up in a serialized HTTP session.
 *
 * <p>They cannot be serialized: they hold a Spring {@code ConversionService} and {@code
 * ValidatorFactory}, and there is no sound way to resolve those again once a session has been
 * restored, least of all in another JVM. What these tests guard is therefore that the attempt fails
 * <em>immediately and by name</em>, rather than succeeding and leaving a binder that cannot convert or
 * validate. Applications keep these out of the session with a {@code transient} field, which is what
 * the class javadoc and the README tell them to do.
 */
class SerializationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SpringBinderConfiguration.class));

    /** A view holding the binder the way an application must not. */
    static class ViewWithBinder implements Serializable {
        final AbstractSpringBinder<RaceResult> binder;

        ViewWithBinder(AbstractSpringBinder<RaceResult> binder) {
            this.binder = binder;
        }
    }

    /** A view holding the binder the way the documentation prescribes. */
    static class ViewWithTransientBinder implements Serializable {
        final transient AbstractSpringBinder<RaceResult> binder;
        final TestField<String> team = new TestField<>(String.class, "");

        ViewWithTransientBinder(AbstractSpringBinder<RaceResult> binder) {
            this.binder = binder;
        }
    }

    private static void serialize(Object value) throws Exception {
        try (ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream())) {
            out.writeObject(value);
        }
    }

    @Test
    void springBinder_cannotBeSerialized_andSaysWhich() {
        contextRunner.run(context -> {
            SpringBinder<RaceResult> binder =
                    context.getBean(SpringBinderFactory.class).create(RaceResult.class);
            assertThatExceptionOfType(NotSerializableException.class)
                    .isThrownBy(() -> serialize(binder))
                    .withMessageContaining("ConversionService");
        });
    }

    /**
     * The conversion service is reached first, being a superclass field, so that is what the message
     * names. Either way the failure is immediate and points at a Spring collaborator.
     */
    @Test
    void springBeanValidationBinder_cannotBeSerialized_andSaysWhich() {
        contextRunner.run(context -> {
            SpringBeanValidationBinder<RaceResult> binder =
                    context.getBean(SpringBinderFactory.class).createBeanValidation(RaceResult.class);
            assertThatExceptionOfType(NotSerializableException.class)
                    .isThrownBy(() -> serialize(binder))
                    .withMessageStartingWith("org.springframework");
        });
    }

    /**
     * A validator is stored in the binding it validates, so it would travel with a binder even if the
     * conversion service did not. It holds the validator factory and so cannot be serialized either.
     */
    @Test
    void springBeanValidator_cannotBeSerialized_andSaysWhich() {
        contextRunner.run(context -> {
            ValidatorFactory validatorFactory =
                    context.getBean(BinderValidatorFactory.class).get();
            SpringBeanValidator validator = new SpringBeanValidator(RaceResult.class, "team", validatorFactory);
            assertThatExceptionOfType(NotSerializableException.class)
                    .isThrownBy(() -> serialize(validator))
                    .withMessageContaining("ValidatorFactory");
        });
    }

    /** The factory and the provider reach into the context, so they cannot travel either. */
    @Test
    void factoryAndProvider_cannotBeSerialized() {
        contextRunner.withBean(ProviderHolder.class).run(context -> {
            assertThatExceptionOfType(NotSerializableException.class)
                    .isThrownBy(() -> serialize(context.getBean(SpringBinderFactory.class)));
            assertThatExceptionOfType(NotSerializableException.class)
                    .isThrownBy(() -> serialize(context.getBean(ProviderHolder.class).provider));
        });
    }

    static class ProviderHolder {
        final SpringBinderProvider<RaceResult> provider;

        ProviderHolder(SpringBinderProvider<RaceResult> provider) {
            this.provider = provider;
        }
    }

    /** A view keeping a binder in an ordinary field takes the whole session down with it. */
    @Test
    void aViewHoldingABinderInAPlainField_cannotBeSerialized() {
        contextRunner.run(context -> {
            ViewWithBinder view = new ViewWithBinder(
                    context.getBean(SpringBinderFactory.class).create(RaceResult.class));
            assertThatExceptionOfType(NotSerializableException.class).isThrownBy(() -> serialize(view));
        });
    }

    /** The documented way works: the field is skipped, and the rest of the view serializes. */
    @Test
    void aViewHoldingABinderInATransientField_canBeSerialized() {
        contextRunner.run(context -> {
            ViewWithTransientBinder view = new ViewWithTransientBinder(
                    context.getBean(SpringBinderFactory.class).createBeanValidation(RaceResult.class));
            assertThat(view.binder).isNotNull();
            assertThatNoException().isThrownBy(() -> serialize(view));
        });
    }

    /**
     * Failing at write time is the point: nothing about a binder makes it usable again on the other
     * side, so a session must never contain one in the first place.
     */
    @Test
    void theConverterFactoryIsPartOfTheSerializedForm() {
        contextRunner.run(context -> {
            SpringBinder<RaceResult> binder =
                    context.getBean(SpringBinderFactory.class).create(RaceResult.class);
            // Reaching the ConversionService through the converter factory is what triggers the
            // failure; a transient converter factory would hide it and restore a broken binder.
            // The assertion names the conversion service rather than one implementation of it, since
            // which one a binder holds depends on what the application declares.
            assertThatExceptionOfType(NotSerializableException.class)
                    .isThrownBy(() -> serialize(binder))
                    .withMessageContaining("ConversionService");
        });
    }
}
