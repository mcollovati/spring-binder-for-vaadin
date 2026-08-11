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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.github.mcollovati.springbinder.data.Duration;
import com.github.mcollovati.springbinder.data.RaceResult;
import com.github.mcollovati.springbinder.fields.TestField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Pins down what happens when the add-on's types end up in a serialized HTTP session.
 *
 * <p>A binder has to survive being written, whether or not the application wants it to: Vaadin's
 * {@code Binder} registers a value change listener on every field it binds, and that listener holds
 * the binder, so each bound field reaches it. Keeping the binder out of the session is therefore not
 * something a view can decide — a {@code transient} field removes one reference out of many, and the
 * fields drag the binder in regardless.
 *
 * <p>The Spring collaborators are consequently {@code transient}, and a binder restored from a session
 * can convert and validate nothing. What these tests guard is that the write succeeds, and that using
 * a restored binder says exactly what is wrong instead of failing obscurely.
 */
class SerializationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SpringBinderConfiguration.class));

    /**
     * A form whose {@code duration} field only binds if Spring supplies the converter. It uses a real
     * {@link TextField} rather than the test double, because the assertions below read the error the
     * binding puts on the field, which needs {@code HasValidation}.
     */
    static class DurationForm implements Serializable {
        TextField duration = new TextField();
    }

    static class ViewWithBinder implements Serializable {
        final AbstractSpringBinder<RaceResult> binder;
        final TestField<String> team = new TestField<>(String.class, "");

        ViewWithBinder(AbstractSpringBinder<RaceResult> binder) {
            this.binder = binder;
        }
    }

    private static void serialize(Object value) throws Exception {
        try (ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream())) {
            out.writeObject(value);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }

    private static DurationForm boundForm(AbstractSpringBinder<RaceResult> binder) {
        DurationForm form = new DurationForm();
        binder.bindInstanceFields(form);
        binder.setBean(new RaceResult("TEAM1", 3, new Duration(120, "M")));
        return form;
    }

    @Test
    void springBinder_canBeSerialized() {
        contextRunner.run(context -> {
            SpringBinder<RaceResult> binder =
                    context.getBean(SpringBinderFactory.class).create(RaceResult.class);
            assertThatNoException().isThrownBy(() -> serialize(binder));
        });
    }

    @Test
    void springBeanValidationBinder_canBeSerialized() {
        contextRunner.run(context -> {
            SpringBeanValidationBinder<RaceResult> binder =
                    context.getBean(SpringBinderFactory.class).createBeanValidation(RaceResult.class);
            assertThatNoException().isThrownBy(() -> serialize(binder));
        });
    }

    /** A validator is stored in the binding it validates, so it travels with a bound field. */
    @Test
    void springBeanValidator_canBeSerialized() {
        contextRunner.run(context -> {
            ValidatorFactory validatorFactory =
                    context.getBean(BinderValidatorFactory.class).get();
            SpringBeanValidator validator = new SpringBeanValidator(RaceResult.class, "team", validatorFactory);
            assertThatNoException().isThrownBy(() -> serialize(validator));
        });
    }

    /**
     * The case that decides whether the add-on works in a session at all. Nobody holds the binder here:
     * the field alone reaches it, through the value change listener the binding registered.
     */
    @Test
    void aFieldBoundThroughSpringConversion_canBeSerialized() {
        contextRunner.run(context -> {
            DurationForm form =
                    boundForm(context.getBean(SpringBinderFactory.class).create(RaceResult.class));
            assertThat(form.duration.getValue()).isEqualTo("120M");
            assertThatNoException().isThrownBy(() -> serialize(form));
        });
    }

    /** Holding the binder in an ordinary field is no longer a way to break the session. */
    @Test
    void aViewHoldingABinderInAPlainField_canBeSerialized() {
        contextRunner.run(context -> {
            ViewWithBinder view = new ViewWithBinder(
                    context.getBean(SpringBinderFactory.class).createBeanValidation(RaceResult.class));
            assertThatNoException().isThrownBy(() -> serialize(view));
        });
    }

    /**
     * A restored form keeps the values it was showing, so nothing looks wrong until something has to be
     * converted. That is the price of being serializable at all, and the reason the message has to name
     * the cause: the session may have been restored on another node entirely.
     */
    @Test
    void aRestoredBinder_cannotConvert_andSaysWhy() {
        contextRunner.run(context -> {
            DurationForm form =
                    boundForm(context.getBean(SpringBinderFactory.class).create(RaceResult.class));

            DurationForm restored = roundTrip(form);
            assertThat(restored.duration.getValue()).isEqualTo("120M");

            restored.duration.setValue("90S");
            assertThat(restored.duration.isInvalid()).isTrue();
            assertThat(restored.duration.getErrorMessage())
                    .contains("restored from a serialized session")
                    .contains("SpringBinderFactory");
        });
    }

    @Test
    void aRestoredValidator_cannotValidate_andSaysWhy() {
        contextRunner.run(context -> {
            ValidatorFactory validatorFactory =
                    context.getBean(BinderValidatorFactory.class).get();
            SpringBeanValidator restored =
                    roundTrip(new SpringBeanValidator(RaceResult.class, "team", validatorFactory));

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(restored::getJavaxBeanValidator)
                    .withMessageContaining("restored from a serialized session");
        });
    }

    /**
     * The factory and the provider reach into the Spring context, so unlike the binders they cannot
     * travel. They are the one thing a view still has to keep in a {@code transient} field.
     */
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
}
