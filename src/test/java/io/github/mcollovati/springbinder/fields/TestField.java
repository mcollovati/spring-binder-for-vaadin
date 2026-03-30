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
package io.github.mcollovati.springbinder.fields;

import com.vaadin.flow.component.AbstractSinglePropertyField;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.function.SerializableFunction;

import static com.vaadin.flow.function.SerializableFunction.identity;

@Tag("test-field")
public class TestField<V> extends AbstractSinglePropertyField<TestField<V>, V> {

    protected final Class<V> valueType;

    public TestField(Class<V> valueType) {
        this(valueType, null);
    }

    public TestField(Class<V> valueType, V defaultValue) {
        super("value", defaultValue, valueType, identity(), identity());
        this.valueType = valueType;
    }

    public TestField(
            Class<V> valueType,
            V defaultValue,
            SerializableFunction<String, V> parser,
            SerializableFunction<V, String> formatter) {
        super("value", defaultValue, String.class, parser, formatter);
        this.valueType = valueType;
    }
}
