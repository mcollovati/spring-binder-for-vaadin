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

import java.util.Optional;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.PropertySet;
import com.vaadin.flow.data.converter.ConverterFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * Base class for {@link Binder} implementations integrated with Spring {@link ConversionService}.
 *
 * <h2>Session serialization</h2>
 *
 * <p>A binder is serializable, and so is a form it has bound. What does <strong>not</strong> survive
 * is the Spring wiring: the {@link ConversionService}, and the {@code ValidatorFactory} of the
 * validating subclass, are {@code transient}, because neither is serializable.
 *
 * <p>That is not a choice a view could make for itself. Vaadin's {@link Binder} registers a value
 * change listener on every field it binds, and that listener holds the binder, so <em>each bound field
 * reaches it</em>. Declaring the view's own binder field {@code transient} removes one reference out of
 * many and does not keep the binder out of the session: as long as a bound field is in there, so is the
 * binder. Either the binder tolerates being written, or no view using it can ever be in a serialized
 * session.
 *
 * <p>The consequence is that a binder restored from a session can convert and validate nothing. It
 * does not pretend otherwise — the first conversion fails with an {@link IllegalStateException} saying
 * so, surfaced as an error on the field or thrown from {@code readBean} — but it also cannot repair
 * itself. Resolving the beans again on the other side is not sound: Spring's serialization support for
 * a bean factory resolves through a registry private to one JVM, and falls back to an empty bean
 * factory when the entry is missing, so a session restored on another node would quietly convert
 * through the wrong service and lose every {@code Converter} bean the application registered.
 *
 * <p>So an application whose sessions are serialized — a clustered deployment, or a container that
 * passivates sessions — has to rebuild the form after a restore rather than reuse what came back:
 *
 * <pre>{@code
 * @Route("product")
 * public class ProductView extends VerticalLayout {
 *
 *     private final transient SpringBinderFactory binders;
 *
 *     public ProductView(SpringBinderFactory binders) {
 *         this.binders = binders;
 *         buildForm();
 *     }
 *
 *     private void buildForm() {
 *         SpringBeanValidationBinder<Product> binder = binders.createBeanValidation(Product.class);
 *         ...
 *     }
 * }
 * }</pre>
 *
 * <p>{@link SpringBinderFactory} and {@link SpringBinderProvider} are the exception: they reach into
 * the Spring context and are genuinely not serializable, so they do have to live in a {@code
 * transient} field, or be taken as a constructor parameter and not kept at all.
 *
 * <h3>Session replication tooling</h3>
 *
 * <p>Tooling that replicates sessions can reconnect what plain deserialization cannot, from outside and
 * with the real context. <a href="https://vaadin.com/docs/latest/tools/kubernetes/session-replication">
 * Vaadin Kubernetes Kit</a> records which {@code transient} fields held Spring beans and re-injects them
 * on deserialization, by reflection over the session's object graph. Two things follow.
 *
 * <p>A view's binder field is worth declaring {@code transient} after all: with the Kit it comes back
 * as a freshly injected binder rather than {@literal null}, so the form can be rebuilt from the field.
 *
 * <p>And the fields of this add-on can be re-injected too, which repairs a restored binder outright,
 * bindings included — but only if its package is among the classes the Kit inspects, which is normally
 * narrowed to application classes:
 *
 * <pre>{@code
 * vaadin.serialization.transients.include-packages=com.example.myapp,com.github.mcollovati.springbinder
 * }</pre>
 *
 * <p>That works when the resolved {@link ConversionService} is a bean, as the MVC conversion service of
 * a Spring Boot web application is. It cannot when the add-on had to build a service of its own, since
 * there is then no bean to re-inject. Rebuilding the form is the option that always works.
 *
 * @param <BEAN> the type of the bean.
 *
 * @since 1.0
 */
public abstract class AbstractSpringBinder<BEAN> extends Binder<BEAN> {

    /**
     * Not {@code transient}: the factory itself is serializable, and it is reachable from every binding
     * anyway. The conversion service inside it is the part that does not travel. See the class javadoc.
     */
    private final SpringConverterFactory converterFactory;

    private final @Nullable Class<BEAN> beanType;

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param conversionService the conversion service.
     */
    protected AbstractSpringBinder(Class<BEAN> beanType, ConversionService conversionService) {
        this(beanType, conversionService, ConversionOrder.VAADIN_FIRST);
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     */
    protected AbstractSpringBinder(
            Class<BEAN> beanType, ConversionService conversionService, ConversionOrder conversionOrder) {
        super(beanType);
        this.beanType = beanType;
        this.converterFactory = createConverterFactory(conversionService, conversionOrder);
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well
     * @param conversionService the conversion service.
     */
    protected AbstractSpringBinder(
            Class<BEAN> beanType, boolean scanNestedDefinitions, ConversionService conversionService) {
        this(beanType, scanNestedDefinitions, conversionService, ConversionOrder.VAADIN_FIRST);
    }

    /**
     * Creates a new binder for the given bean or record type, using the {@link ConversionService} to
     * provide suitable converters for bindings when presentation and model types are not compatible.
     *
     * @param beanType the bean type to use, not {@literal null}.
     * @param scanNestedDefinitions if true, scan for nested property definitions as well
     * @param conversionService the conversion service.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     */
    protected AbstractSpringBinder(
            Class<BEAN> beanType,
            boolean scanNestedDefinitions,
            ConversionService conversionService,
            ConversionOrder conversionOrder) {
        super(beanType, scanNestedDefinitions);
        this.beanType = beanType;
        this.converterFactory = createConverterFactory(conversionService, conversionOrder);
    }

    /**
     * Creates a new binder using the given property set, for parity with {@link
     * Binder#withPropertySet(PropertySet)}.
     *
     * @param propertySet the property set implementation to use, not {@literal null}.
     * @param conversionService the conversion service.
     * @param conversionOrder whether Vaadin or Spring provides the converter when both can.
     */
    protected AbstractSpringBinder(
            PropertySet<BEAN> propertySet, ConversionService conversionService, ConversionOrder conversionOrder) {
        super(propertySet);
        this.beanType = null;
        this.converterFactory = createConverterFactory(conversionService, conversionOrder);
    }

    private SpringConverterFactory createConverterFactory(
            ConversionService conversionService, ConversionOrder conversionOrder) {
        return new SpringConverterFactory(conversionService, super.getConverterFactory(), conversionOrder);
    }

    /**
     * Returns the bean type this binder binds.
     *
     * <p>Useful to assert that an injected binder was built for the expected type, since the
     * auto-configuration resolves it from the generic parameter of the injection point rather than
     * from an argument the caller passes.
     *
     * <p>Named {@code findBeanType} rather than {@code getBeanType} on purpose. Vaadin's {@link Binder}
     * keeps its bean type in a private field today, and the day it exposes it the natural signature is
     * {@code Class<BEAN> getBeanType()} — which an {@link Optional} returning method of the same name
     * could not override, so this add-on would stop compiling against a new Vaadin.
     *
     * @return the bean type, or an empty optional when the binder was created from a {@link
     *     PropertySet}, which does not carry one.
     * @since 1.0
     */
    public Optional<Class<BEAN>> findBeanType() {
        return Optional.ofNullable(beanType);
    }

    /**
     * Returns the shared {@link DefaultConversionService}, used by the constructors that do not take
     * a conversion service.
     *
     * @return the shared conversion service, never {@literal null}.
     */
    protected static ConversionService sharedConversionService() {
        return DefaultConversionService.getSharedInstance();
    }

    @Override
    protected ConverterFactory getConverterFactory() {
        return converterFactory;
    }
}
