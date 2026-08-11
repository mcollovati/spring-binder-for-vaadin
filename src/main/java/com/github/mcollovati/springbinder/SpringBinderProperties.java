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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the injected binders.
 *
 * @see ConversionOrder
 *
 * @since 1.0
 */
@ConfigurationProperties(SpringBinderProperties.PREFIX)
public class SpringBinderProperties {

    /** Prefix of the add-on configuration properties. */
    public static final String PREFIX = "springbinder";

    private final Conversion conversion = new Conversion();

    public Conversion getConversion() {
        return conversion;
    }

    /** Conversion settings, configured under {@code springbinder.conversion}. */
    public static class Conversion {

        /**
         * Whether Vaadin or Spring provides the converter when both are able to convert a given
         * pair of types.
         */
        private ConversionOrder order = ConversionOrder.VAADIN_FIRST;

        public ConversionOrder getOrder() {
            return order;
        }

        public void setOrder(ConversionOrder order) {
            this.order = order;
        }
    }
}
