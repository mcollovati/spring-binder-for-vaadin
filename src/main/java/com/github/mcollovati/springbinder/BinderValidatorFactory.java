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

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Supplies the {@link ValidatorFactory} the bean validation binders validate with.
 *
 * <p>The application's own {@link ValidatorFactory} bean is used whenever there is one, so that
 * binders validate exactly like the rest of the application. Applications that have a JSR-303
 * provider on the classpath without exposing it as a bean — the common case when {@code
 * spring-boot-starter-validation} is absent but Hibernate Validator arrives transitively — get a
 * factory built here instead, mirroring what Vaadin's own {@code BeanValidationBinder} does. That
 * fallback is built lazily and only once, and is closed with the application context.
 *
 * <p>The lookup is deliberately deferred to the moment a binder is created rather than evaluated as
 * an auto-configuration condition: binders are prototypes, so by then every {@link ValidatorFactory}
 * bean is visible, including those registered by auto-configurations ordered after this one.
 */
class BinderValidatorFactory implements DisposableBean {

    private final ObjectProvider<ValidatorFactory> applicationValidatorFactory;
    private final ApplicationContext applicationContext;
    private volatile @Nullable LocalValidatorFactoryBean fallback;

    BinderValidatorFactory(
            ObjectProvider<ValidatorFactory> applicationValidatorFactory, ApplicationContext applicationContext) {
        this.applicationValidatorFactory = applicationValidatorFactory;
        this.applicationContext = applicationContext;
    }

    /**
     * Returns the validator factory to validate with.
     *
     * @return the application's validator factory when there is exactly one, otherwise a factory
     *     owned by this add-on, never {@literal null}.
     */
    ValidatorFactory get() {
        ValidatorFactory factory = applicationValidatorFactory.getIfUnique();
        return factory != null ? factory : fallback();
    }

    /**
     * Builds, once, a validator factory backed by the application context, so that constraint
     * validators can be Spring beans just like they are with {@code spring-boot-starter-validation}.
     *
     * @return the factory owned by this add-on, never {@literal null}.
     */
    private ValidatorFactory fallback() {
        LocalValidatorFactoryBean resolved = fallback;
        if (resolved == null) {
            synchronized (this) {
                resolved = fallback;
                if (resolved == null) {
                    resolved = new LocalValidatorFactoryBean();
                    resolved.setApplicationContext(applicationContext);
                    resolved.afterPropertiesSet();
                    fallback = resolved;
                }
            }
        }
        return resolved;
    }

    @Override
    public void destroy() {
        LocalValidatorFactoryBean resolved = fallback;
        if (resolved != null) {
            resolved.destroy();
        }
    }
}
