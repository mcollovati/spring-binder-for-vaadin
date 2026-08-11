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
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * Base class for {@link Binder} implementations integrated with Spring {@link ConversionService}.
 *
 * <h2>Not serializable</h2>
 *
 * <p>Vaadin's {@link Binder} is serializable, but these binders hold a Spring {@link
 * ConversionService} — and the validating subclass a {@code ValidatorFactory} — which are not. A
 * binder therefore cannot be written to a serialized HTTP session, and trying throws {@link
 * java.io.NotSerializableException} naming the service or the factory.
 *
 * <p>That is deliberate. Marking the Spring collaborators {@code transient} would let the session be
 * written and then restore a binder with no conversion service, which fails later and further from
 * the cause. Nor can they be replaced by something that resolves them again after the session moves:
 * Spring's own serialization support for a bean factory resolves through a registry private to one
 * JVM, and falls back to an empty bean factory when the entry is missing, so a session restored in
 * another JVM would silently convert through the wrong service.
 *
 * <p>Applications that rely on session serialization — a clustered deployment, or a container that
 * passivates sessions — must therefore keep a binder out of the serialized state:
 *
 * <pre>{@code
 * @Route("product")
 * public class ProductView extends VerticalLayout {
 *
 *     private final transient SpringBeanValidationBinder<Product> binder;
 *     ...
 * }
 * }</pre>
 *
 * <p>A {@code transient} field is {@literal null} after the session is restored, and its bindings are
 * gone with it, so the view has to build the form again — through {@link SpringBinderFactory} or a
 * fresh injection. There is no way to restore the bindings themselves.
 *
 * @param <BEAN> the type of the bean.
 */
public abstract class AbstractSpringBinder<BEAN> extends Binder<BEAN> {

    /**
     * Deliberately not {@code transient}, so that serializing a binder fails at once instead of
     * restoring one that cannot convert. See the class javadoc.
     */
    private final SpringConverterFactory converterFactory;

    private final Class<BEAN> beanType;

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
