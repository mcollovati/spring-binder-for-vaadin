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
import java.util.Objects;

import com.vaadin.flow.data.binder.Binder;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.core.convert.ConversionService;

@AutoConfiguration
@ConditionalOnClass(Binder.class)
public class SpringBinderConfiguration {

    @SuppressWarnings("unchecked")
    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    @ConditionalOnMissingBean
    <BEAN> SpringBinder<BEAN> createBinder(DependencyDescriptor descriptor, ConversionService conversionService) {
        Class<BEAN> beanType =
                (Class<BEAN>) descriptor.getResolvableType().getGeneric(0).resolve();
        Objects.requireNonNull(beanType, "Unable to resolve bean type from " + descriptor.getResolvableType());
        return new SpringBinder<>(beanType, conversionService);
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    @ConditionalOnMissingBean
    @ConditionalOnBean(ValidatorFactory.class)
    <BEAN> SpringBeanValidationBinder<BEAN> createBeanValidationBinder(
            DependencyDescriptor descriptor, ConversionService conversionService, ValidatorFactory validatorFactory) {
        Class<BEAN> beanType =
                (Class<BEAN>) descriptor.getResolvableType().getGeneric(0).resolve();
        Objects.requireNonNull(beanType, "Unable to resolve bean type from " + descriptor.getResolvableType());
        return new SpringBeanValidationBinder<>(beanType, conversionService, validatorFactory);
    }

    @Bean
    @ConditionalOnMissingBean(ConversionService.class)
    ConversionServiceFactoryBean conversionServiceFactoryBean() {
        return new ConversionServiceFactoryBean();
    }
}
