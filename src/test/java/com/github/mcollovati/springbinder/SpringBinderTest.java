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

import java.util.Date;
import java.util.Optional;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.data.converter.StringToDateConverter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionService;

import com.github.mcollovati.springbinder.data.Duration;
import com.github.mcollovati.springbinder.data.RaceResult;
import com.github.mcollovati.springbinder.fields.TestField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringBinderTest {

    protected <BEAN> Binder<BEAN> createBinder(Class<BEAN> type, ConversionService service) {
        return new SpringBinder<>(type, service);
    }

    @Test
    void converterExists_applyConverterFromConversionService() {
        Date expectedDate = new Date();
        ConversionService service = mock(ConversionService.class);
        when(service.canConvert(String.class, Duration.class)).thenReturn(true);
        when(service.canConvert(Duration.class, String.class)).thenReturn(true);
        when(service.canConvert(String.class, Date.class)).thenReturn(true);
        when(service.canConvert(Date.class, String.class)).thenReturn(true);
        when(service.convert(anyString(), eq(Duration.class)))
                .then(i -> Duration.valueOf(i.getArgument(0, String.class).replaceFirst("^TEST-", "")));
        when(service.convert(any(Duration.class), eq(String.class)))
                .then(i -> "TEST-" + i.getArgument(0).toString());
        when(service.convert(anyString(), eq(Date.class))).then(i -> expectedDate);
        when(service.convert(any(Date.class), eq(String.class)))
                .then(i -> "TEST-" + i.getArgument(0).toString());

        Binder<RaceResult> binder = createBinder(RaceResult.class, service);
        Form form = new Form();
        binder.bindInstanceFields(form);

        Duration duration = new Duration(120, "M");
        RaceResult result = new RaceResult("TEAM1", 3, duration);
        binder.setBean(result);

        Assertions.assertEquals("TEST-120M", form.duration.getValue());
        Assertions.assertNull(form.date.getValue());

        form.duration.setValue("2H");
        Assertions.assertEquals(2, result.getDuration().getAmount());
        Assertions.assertEquals("H", result.getDuration().getTimeUnit());

        form.date.setValue("SOMETHING");
        Assertions.assertEquals(result.getDate(), expectedDate);
        Assertions.assertEquals("TEST-" + expectedDate, form.date.getValue());
    }

    @Test
    void converterDoesNotExist_fallbackToDefaultConversion() {
        Date expectedDate = new Date();
        StringToDateConverter defaultConverter = new StringToDateConverter();

        ConversionService service = mock(ConversionService.class);
        when(service.canConvert(String.class, Duration.class)).thenReturn(true);
        when(service.canConvert(Duration.class, String.class)).thenReturn(true);
        when(service.convert(anyString(), eq(Duration.class)))
                .then(i -> Duration.valueOf(i.getArgument(0, String.class).replaceFirst("^TEST-", "")));
        when(service.convert(any(Duration.class), eq(String.class)))
                .then(i -> "TEST-" + i.getArgument(0).toString());

        Binder<RaceResult> binder = createBinder(RaceResult.class, service);
        Form form = new Form();
        binder.bindInstanceFields(form);

        Duration duration = new Duration(120, "M");
        RaceResult result = new RaceResult("TEAM1", 3, duration);
        result.setDate(expectedDate);
        binder.setBean(result);

        Assertions.assertEquals("TEST-120M", form.duration.getValue());
        Assertions.assertEquals(
                defaultConverter.convertToPresentation(expectedDate, new ValueContext()), form.date.getValue());

        Date newDate = new Date(expectedDate.getTime() + 24 * 3600 * 1000);
        form.date.setValue(defaultConverter.convertToPresentation(newDate, new ValueContext()));
        Assertions.assertTrue(binder.isValid(), "binder validation failed");
        assertThat(result.getDate()).isEqualToIgnoringMillis(newDate);
    }

    static class Form {

        TestField<String> date = new TestField<>(String.class, null);
        TestField<String> team = new TestField<>(String.class, null);
        TestField<String> duration = new TestField<>(String.class, null);
    }

    @Test
    void conversionServiceReturnsNull_handlesGracefully() {
        ConversionService service = mock(ConversionService.class);
        when(service.canConvert(String.class, Duration.class)).thenReturn(true);
        when(service.canConvert(Duration.class, String.class)).thenReturn(true);
        when(service.convert(anyString(), eq(Duration.class))).thenReturn(null);

        Binder<RaceResult> binder = createBinder(RaceResult.class, service);
        Form form = new Form();
        binder.bindInstanceFields(form);

        RaceResult result = new RaceResult("TEAM1", 3, new Duration(120, "M"));
        binder.setBean(result);

        Assertions.assertNull(form.duration.getValue());
    }

    @Test
    void conversionServiceNullPresentationValue_handlesGracefully() {
        ConversionService service = mock(ConversionService.class);
        when(service.canConvert(String.class, Duration.class)).thenReturn(true);
        when(service.canConvert(Duration.class, String.class)).thenReturn(true);
        when(service.convert(any(Duration.class), eq(String.class))).thenReturn(null);

        Binder<RaceResult> binder = createBinder(RaceResult.class, service);
        Form form = new Form();
        binder.bindInstanceFields(form);

        Duration duration = new Duration(120, "M");
        RaceResult result = new RaceResult("TEAM1", 3, duration);
        binder.setBean(result);

        Assertions.assertNull(form.duration.getValue());
    }

    @Test
    void nullConversionService_throwsNullPointerException() {
        Assertions.assertThrows(NullPointerException.class, () -> new SpringBinder<>(RaceResult.class, null));
    }

    @Test
    void nullFallbackConverterFactory_throwsNullPointerException() {
        ConversionService service = mock(ConversionService.class);
        Assertions.assertThrows(NullPointerException.class, () -> new SpringConverterFactory(service, null));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void nullPresentationType_throwsNullPointerException() {
        SpringConverterFactory factory = new SpringConverterFactory(
                mock(ConversionService.class), new com.vaadin.flow.data.converter.ConverterFactory() {
                    @Override
                    public Optional newInstance(Class presentationType, Class modelType) {
                        return Optional.empty();
                    }
                });
        Assertions.assertThrows(NullPointerException.class, () -> factory.newInstance(null, String.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void nullModelType_throwsNullPointerException() {
        SpringConverterFactory factory = new SpringConverterFactory(
                mock(ConversionService.class), new com.vaadin.flow.data.converter.ConverterFactory() {
                    @Override
                    public Optional newInstance(Class presentationType, Class modelType) {
                        return Optional.empty();
                    }
                });
        Assertions.assertThrows(NullPointerException.class, () -> factory.newInstance(String.class, null));
    }
}
