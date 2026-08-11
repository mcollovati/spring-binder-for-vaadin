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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;

/**
 * Marks a {@link ConversionService} bean as the one the injected binders should use, in preference
 * to the application's regular {@link ConversionService}.
 *
 * <p>Use it when the conversions that suit a form are not the conversions that suit the rest of the
 * application. A registry built without Spring's default converters, for example, contains only
 * conversions that were explicitly registered, which makes {@link ConversionOrder#SPRING_FIRST}
 * predictable:
 *
 * <pre>{@code
 * @Bean
 * @BinderConversionService
 * ConversionService binderConversions(Converter<String, Duration> toDuration,
 *         Converter<Duration, String> fromDuration) {
 *     GenericConversionService conversions = new GenericConversionService();
 *     conversions.addConverter(toDuration);
 *     conversions.addConverter(fromDuration);
 *     return conversions;
 * }
 * }</pre>
 *
 * <p>When no such bean exists the binders use the application's {@link ConversionService}, and when
 * that one is ambiguous or missing they fall back to {@link
 * org.springframework.core.convert.support.DefaultConversionService#getSharedInstance()}.
 */
@Qualifier @Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface BinderConversionService {}
