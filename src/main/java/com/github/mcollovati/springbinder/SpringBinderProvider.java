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

/**
 * Creates binders for a bean type fixed at the injection point, for components that need more than
 * one.
 *
 * <p>Where injecting a binder gives exactly one instance, injecting a provider gives as many as
 * asked for, without repeating the bean type at every call:
 *
 * <pre>{@code
 * @Route("admin")
 * class AdminView extends VerticalLayout {
 *
 *     private final SpringBinderProvider<Category> binders;
 *
 *     AdminView(SpringBinderProvider<Category> binders) {
 *         this.binders = binders;
 *     }
 *
 *     private Component createCategoryEditor(Category category) {
 *         SpringBeanValidationBinder<Category> binder = binders.getBeanValidation();
 *         ...
 *     }
 * }
 * }</pre>
 *
 * <p>Reusing a single injected binder for several rows would compile and start, but silently bind
 * every row to the same bean. Use a provider whenever the number of forms is not known upfront, and
 * {@link SpringBinderFactory} when the component is not managed by Spring at all or needs binders for
 * more than one bean type.
 *
 * <p><strong>Not serializable.</strong> A provider reaches into the Spring context, so it cannot be
 * part of a serialized HTTP session. Hold it in a {@code transient} field of a Vaadin component. See
 * {@link AbstractSpringBinder} for why this cannot be worked around.
 *
 * @param <BEAN> the bean type the binders bind, taken from the injection point.
 * @see SpringBinderFactory
 */
public interface SpringBinderProvider<BEAN> {

    /**
     * Creates a binder for the bean type of this provider.
     *
     * @return a new binder, never {@literal null}.
     */
    SpringBinder<BEAN> get();

    /**
     * Creates a binder for the bean type of this provider that also applies JSR-303 constraints.
     *
     * @return a new binder, never {@literal null}.
     * @throws IllegalStateException if no JSR-303 provider is available.
     */
    SpringBeanValidationBinder<BEAN> getBeanValidation();
}
