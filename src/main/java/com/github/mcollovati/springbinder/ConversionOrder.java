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

import com.vaadin.flow.data.converter.ConverterFactory;
import org.springframework.core.convert.ConversionService;

/**
 * Defines whether Spring or Vaadin is asked first to provide a converter for a binding.
 *
 * <p>The distinction matters because a Spring {@link ConversionService} usually reports many more
 * conversions as supported than one would expect: the generic {@code ObjectToObjectConverter}
 * matches any type exposing a {@code valueOf}, {@code of}, {@code from} or {@code String}
 * constructor, and every type can be converted <em>to</em> {@link String} through {@code
 * toString()}. Those generic conversions are rarely what a form field wants, as they ignore the
 * locale and produce technical error messages.
 *
 * @since 1.0
 */
public enum ConversionOrder {

    /**
     * Ask Vaadin's {@link ConverterFactory} first and use Spring only for conversions Vaadin does
     * not know, which is the default.
     *
     * <p>Vaadin only provides converters for common form field types (text to numbers, booleans,
     * dates and UUIDs, plus numeric widening), so custom domain types are still converted by
     * Spring. Use this unless a Spring converter has to override one of those built-in conversions.
     */
    VAADIN_FIRST,

    /**
     * Ask Spring first and fall back to Vaadin's {@link ConverterFactory} only for conversions the
     * {@link ConversionService} does not support.
     *
     * <p>This lets a Spring converter override a conversion Vaadin also provides, at the price of
     * Spring's generic converters taking over bindings such as text to date or text to number. It
     * is a sound choice when the binder is given a {@link ConversionService} that contains only
     * explicitly registered converters, see {@link BinderConversionService}.
     */
    SPRING_FIRST
}
