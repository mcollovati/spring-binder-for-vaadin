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

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.format.support.FormattingConversionService;

/**
 * Supplies the {@link ConversionService} the binders convert with.
 *
 * <p>Resolution has three steps, and stops at the first that yields a service:
 *
 * <ol>
 *   <li>the bean qualified with {@link BinderConversionService}, the explicit way to choose. Since
 *       the qualifier states an intent, more than one carrying it is a configuration error and fails
 *       the context at startup rather than being quietly ignored;
 *   <li>the application's own {@link ConversionService} bean, so that binders convert like the rest
 *       of the application. In a Spring Boot web application this is the format aware {@code
 *       mvcConversionService}, which is why {@code spring.mvc.format.*} applies to bindings too;
 *   <li>a service owned by this add-on, built once and populated with the application's {@code
 *       Converter}, {@code GenericConverter}, {@code Formatter}, {@code Printer} and {@code Parser}
 *       beans.
 * </ol>
 *
 * <p>That last step is what makes converter beans work in an application that has no {@code
 * ConversionService} of its own. Spring Boot collects them into {@code mvcConversionService} for a
 * servlet application; nothing does so anywhere else, so a plain context — a non-web application, or
 * a {@code @SpringBootTest(webEnvironment = NONE)} — would otherwise convert through a registry that
 * contains none of the conversions the application registered.
 *
 * <p>No {@link ConversionService} bean is published, deliberately. Contributing an unqualified one
 * would change what the rest of the application injects, and would satisfy the {@code
 * ConditionalOnMissingBean} of any auto-configuration that contributes its own.
 */
class ConversionServiceResolver implements SmartInitializingSingleton {

    private static final Log logger = LogFactory.getLog(ConversionServiceResolver.class);

    private final ObjectProvider<ConversionService> binderConversionService;
    private final ObjectProvider<ConversionService> applicationConversionService;
    private final ApplicationContext applicationContext;
    private volatile @Nullable ConversionService fallback;

    ConversionServiceResolver(
            ObjectProvider<ConversionService> binderConversionService,
            ObjectProvider<ConversionService> applicationConversionService,
            ApplicationContext applicationContext) {
        this.binderConversionService = binderConversionService;
        this.applicationConversionService = applicationConversionService;
        this.applicationContext = applicationContext;
    }

    /**
     * Rejects an ambiguous {@link BinderConversionService} at startup instead of when the first view
     * is built. Runs once every singleton exists, so resolving the qualified bean here cannot
     * instantiate it earlier than the application intended.
     */
    @Override
    public void afterSingletonsInstantiated() {
        qualifiedConversionService();
    }

    /**
     * Returns the conversion service to convert with.
     *
     * @return the resolved conversion service, never {@literal null}.
     * @throws IllegalStateException when several beans are qualified with {@link
     *     BinderConversionService}, since the qualifier can only name one.
     */
    ConversionService get() {
        ConversionService qualified = qualifiedConversionService();
        if (qualified != null) {
            return qualified;
        }
        ConversionService application = applicationConversionService.getIfUnique();
        return application != null ? application : fallback();
    }

    /**
     * @return the conversion service qualified with {@link BinderConversionService}, or {@literal
     *     null} when there is none.
     */
    private @Nullable ConversionService qualifiedConversionService() {
        try {
            return binderConversionService.getIfAvailable();
        } catch (NoUniqueBeanDefinitionException ex) {
            throw new IllegalStateException(
                    "Several ConversionService beans are annotated with @BinderConversionService "
                            + ex.getBeanNamesFound()
                            + ". The qualifier names the single conversion service the binders use, so leave it on "
                            + "one bean only.",
                    ex);
        }
    }

    /**
     * Builds, once, a conversion service owned by the add-on: Spring's default converters, plus every
     * conversion the application registered as a bean.
     *
     * <p>Only Spring's default <em>converters</em> are added, not the default formatters, so that the
     * conversions this service reports are the ones {@link ConversionOrder} describes. An application
     * that wants format aware conversion has a {@link ConversionService} of its own, and then this
     * service is never built.
     *
     * @return the conversion service owned by this add-on, never {@literal null}.
     */
    private ConversionService fallback() {
        ConversionService resolved = fallback;
        if (resolved == null) {
            synchronized (this) {
                resolved = fallback;
                if (resolved == null) {
                    warnIfAmbiguous();
                    FormattingConversionService service = new FormattingConversionService();
                    DefaultConversionService.addDefaultConverters(service);
                    ApplicationConversionService.addBeans(service, applicationContext);
                    fallback = service;
                    resolved = service;
                }
            }
        }
        return resolved;
    }

    /**
     * Several {@link ConversionService} beans and no primary one is not an error — the add-on has a
     * service of its own to convert with — but it is not what the application meant either, so it
     * must not pass silently.
     */
    private void warnIfAmbiguous() {
        if (!logger.isWarnEnabled()) {
            return;
        }
        String[] candidates = applicationContext.getBeanNamesForType(ConversionService.class, true, false);
        if (candidates.length > 1) {
            logger.warn("Found " + candidates.length + " ConversionService beans " + Arrays.toString(candidates)
                    + " and none of them is primary, so the binders cannot tell which one the application converts "
                    + "with. They will use a conversion service of their own, built from the Converter and Formatter "
                    + "beans of this application. Annotate one bean with @BinderConversionService, or mark one as "
                    + "@Primary, to choose explicitly.");
        }
    }
}
