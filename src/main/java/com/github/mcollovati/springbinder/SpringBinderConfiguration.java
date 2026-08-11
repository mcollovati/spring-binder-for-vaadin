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
import jakarta.validation.executable.ExecutableValidator;
import java.util.Objects;

import com.vaadin.flow.data.binder.Binder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.core.convert.ConversionService;

@AutoConfiguration
@ConditionalOnClass(Binder.class)
@EnableConfigurationProperties(SpringBinderProperties.class)
public class SpringBinderConfiguration {

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    @ConditionalOnMissingBean
    <BEAN> SpringBinder<BEAN> createBinder(
            DependencyDescriptor descriptor,
            ConversionServiceResolver conversionService,
            SpringBinderProperties properties) {
        return new SpringBinder<>(
                beanType(descriptor),
                conversionService.get(),
                properties.getConversion().getOrder());
    }

    /**
     * A component that builds one form per row, or that Spring does not manage at all, cannot get its
     * binders from an injection point. The factory covers both: it is a singleton, so it can be
     * passed to hand constructed components, and it creates as many binders as needed, for any bean
     * type, with the wiring injected binders get.
     */
    @Bean
    @ConditionalOnMissingBean
    SpringBinderFactory springBinderFactory(
            ConversionServiceResolver conversionService,
            ObjectProvider<BinderValidatorFactory> validatorFactory,
            SpringBinderProperties properties) {
        return new DefaultSpringBinderFactory(conversionService::get, validatorFactory, properties);
    }

    /**
     * Resolves the conversion service the binders use. It is a bean of its own so that the service it
     * may have to build is built once and shared by every binder, and so that an application can
     * replace the resolution altogether.
     */
    @Bean
    @ConditionalOnMissingBean
    ConversionServiceResolver binderConversionServiceResolver(
            @BinderConversionService ObjectProvider<ConversionService> binderConversionService,
            ObjectProvider<ConversionService> conversionService,
            ApplicationContext applicationContext) {
        return new ConversionServiceResolver(binderConversionService, conversionService, applicationContext);
    }

    /**
     * The typed counterpart of {@link SpringBinderFactory}, for components that need several binders
     * but only ever for the bean type named at their injection point.
     */
    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    @ConditionalOnMissingBean
    <BEAN> SpringBinderProvider<BEAN> createBinderProvider(
            DependencyDescriptor descriptor, SpringBinderFactory factory) {
        return new DefaultSpringBinderProvider<>(beanType(descriptor), factory);
    }

    /**
     * Registers the bean validation binder whenever JSR-303 validation can actually be performed,
     * which is what Vaadin's own {@code BeanValidationBinder} requires as well: the API and a
     * provider on the classpath.
     *
     * <p>These are the conditions Spring Boot's own validation auto-configuration uses. Gating on a
     * {@link ValidatorFactory} <em>bean</em> instead would be too strict: an application can have
     * Hibernate Validator transitively — through {@code vaadin-spring} — without depending on {@code
     * spring-boot-starter-validation}, and would then silently get no validation binder at all, with
     * the injection point failing only once a view is instantiated.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ExecutableValidator.class)
    @ConditionalOnResource(resources = "classpath:META-INF/services/jakarta.validation.spi.ValidationProvider")
    static class BeanValidationBinderConfiguration {

        /**
         * The validation capable binder is primary so that injecting the base {@link Binder} type
         * resolves to it instead of being ambiguous. Both concrete types remain injectable.
         */
        @Bean
        @Primary
        @Scope(BeanDefinition.SCOPE_PROTOTYPE)
        @ConditionalOnMissingBean
        <BEAN> SpringBeanValidationBinder<BEAN> createBeanValidationBinder(
                DependencyDescriptor descriptor,
                ConversionServiceResolver conversionService,
                BinderValidatorFactory validatorFactory,
                SpringBinderProperties properties) {
            return new SpringBeanValidationBinder<>(
                    beanType(descriptor),
                    conversionService.get(),
                    validatorFactory.get(),
                    properties.getConversion().getOrder());
        }

        @Bean
        @ConditionalOnMissingBean
        BinderValidatorFactory binderValidatorFactory(
                ObjectProvider<ValidatorFactory> validatorFactory, ApplicationContext applicationContext) {
            return new BinderValidatorFactory(validatorFactory, applicationContext);
        }
    }

    @SuppressWarnings("unchecked")
    private static <BEAN> Class<BEAN> beanType(DependencyDescriptor descriptor) {
        Class<BEAN> beanType =
                (Class<BEAN>) descriptor.getResolvableType().getGeneric(0).resolve();
        Objects.requireNonNull(beanType, "Unable to resolve bean type from " + descriptor.getResolvableType());
        return beanType;
    }
}
