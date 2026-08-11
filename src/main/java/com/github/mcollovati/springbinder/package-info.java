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

/**
 * A Vaadin Flow {@link com.vaadin.flow.data.binder.Binder} that converts between field and bean types
 * through Spring's {@link org.springframework.core.convert.ConversionService}.
 *
 * <h2>Getting a binder</h2>
 *
 * <p>The add-on is a Spring Boot auto-configuration, so binders are injectable without any {@code
 * @Enable} annotation. There are three ways to get one, in order of preference:
 *
 * <ul>
 *   <li>inject {@link com.github.mcollovati.springbinder.SpringBinder} or {@link
 *       com.github.mcollovati.springbinder.SpringBeanValidationBinder} directly. The bean type comes
 *       from the generic parameter of the injection point, and each injection point gets its own
 *       binder;
 *   <li>inject a {@link com.github.mcollovati.springbinder.SpringBinderProvider} when one component
 *       needs several binders for the same bean type, such as a form per row of a grid. Reusing a
 *       single binder for several rows compiles and starts, and then silently binds every row to the
 *       same bean;
 *   <li>inject the {@link com.github.mcollovati.springbinder.SpringBinderFactory} singleton when the
 *       component is not managed by Spring, or needs binders for more than one bean type.
 * </ul>
 *
 * <p>All three wire binders identically, so a form built through the factory behaves like an injected
 * one. Constructing a binder by hand does not: it takes whatever
 * {@link org.springframework.core.convert.ConversionService} the caller passes, and no validator
 * factory from the context.
 *
 * <h2>Which conversions apply</h2>
 *
 * <p>{@link com.github.mcollovati.springbinder.ConversionOrder} decides whether Vaadin's own converters
 * or Spring's are consulted first; {@link
 * com.github.mcollovati.springbinder.SpringBinderProperties} configures the default under {@code
 * springbinder.conversion.order}, and {@link
 * com.github.mcollovati.springbinder.BinderConversionService} names a conversion service reserved for
 * binders.
 *
 * <h2>Serialization</h2>
 *
 * <p>Binders, providers and the factory are deliberately <strong>not</strong> serializable. See {@link
 * com.github.mcollovati.springbinder.AbstractSpringBinder} for why, and for what a clustered or
 * session-passivating deployment has to do about it.
 *
 * <h2>Nullness</h2>
 *
 * <p>This package is {@link org.jspecify.annotations.NullMarked}: parameters, return values and fields
 * do not accept or produce {@literal null} unless annotated {@link
 * org.jspecify.annotations.Nullable}.
 *
 * @since 1.0
 */
@NullMarked
package com.github.mcollovati.springbinder;

import org.jspecify.annotations.NullMarked;
