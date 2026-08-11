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
 * Binds a {@link SpringBinderFactory} to the bean type resolved from an injection point.
 *
 * @param beanType the bean type the created binders bind.
 * @param factory the factory doing the actual work.
 * @param <BEAN> the bean type.
 */
record DefaultSpringBinderProvider<BEAN>(Class<BEAN> beanType, SpringBinderFactory factory)
        implements SpringBinderProvider<BEAN> {

    @Override
    public SpringBinder<BEAN> get() {
        return factory.create(beanType);
    }

    @Override
    public SpringBeanValidationBinder<BEAN> getBeanValidation() {
        return factory.createBeanValidation(beanType);
    }
}
