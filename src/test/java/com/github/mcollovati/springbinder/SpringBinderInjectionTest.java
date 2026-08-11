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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.github.mcollovati.springbinder.data.Duration;
import com.github.mcollovati.springbinder.data.Person;
import com.github.mcollovati.springbinder.fields.NumberField;
import com.github.mcollovati.springbinder.fields.TestField;

@ContextConfiguration(classes = SpringBinderInjectionTest.Config.class)
@ExtendWith(SpringExtension.class)
class SpringBinderInjectionTest {

    @Autowired
    SpringBinder<Person> binder;

    @Autowired
    SpringBinder<Person> binder2;

    @Autowired
    SpringBinder<Duration> binder3;

    @Autowired
    Form form;

    @Test
    void injectedBinder_isPrototype() {
        Assertions.assertInstanceOf(AbstractSpringBinder.class, binder);
        Assertions.assertInstanceOf(AbstractSpringBinder.class, binder2);

        Assertions.assertNotSame(binder, binder2);

        Form form1 = new Form(binder);
        Form form2 = new Form(binder2);

        Person bean = testBean();
        form1.binder.setBean(bean);
        Assertions.assertEquals(bean.getName(), form1.name.getValue(), "Name field form 1");
        Assertions.assertEquals("", form2.name.getValue(), "Name field form 2");
    }

    @Test
    void injectBinder_differentBeanType() {
        Assertions.assertInstanceOf(AbstractSpringBinder.class, binder3);

        Assertions.assertNotSame(binder3, binder);
        Assertions.assertNotSame(binder3, binder2);

        NumberField<Long> duration = new NumberField<>(Long.class, 0L);
        TestField<String> timeUnit = new TestField<>(String.class, "SEC");
        binder3.forField(duration).bind(Duration::getAmount, Duration::setAmount);
        binder3.forField(timeUnit).bind("timeUnit");

        Duration bean = new Duration(30, "MIN");
        binder3.setBean(bean);

        Assertions.assertEquals(bean.getAmount(), duration.getValue(), "Duration amount");
        Assertions.assertEquals(bean.getTimeUnit(), timeUnit.getValue(), "Time unit");
    }

    @Test
    void injectedBinder_manualBinding_typeResolvedCorrectly() {
        TestField<String> textField = new TestField<>(String.class, "");
        binder.forField(textField).bind(Person::getName, Person::setName);

        Person bean = testBean();

        binder.setBean(bean);
        Assertions.assertEquals(bean.getName(), textField.getValue(), "Name field");

        binder.setBean(null);
        Assertions.assertEquals("", textField.getValue(), "Expecting name field to have empty value for null bean");
    }

    @Test
    void injectedBinder_automaticBinding_typeResolvedCorrectly() {
        Form form = new Form();
        binder.bindInstanceFields(form);

        Person bean = testBean();

        binder.setBean(bean);
        Assertions.assertEquals(bean.getName(), form.name.getValue(), "Name field");

        binder.setBean(null);
        Assertions.assertEquals("", form.name.getValue(), "Expecting name field to have empty value for null bean");
    }

    @Test
    void componentWithInjectedBinder_automaticBinding() {
        Assertions.assertInstanceOf(AbstractSpringBinder.class, form.binder);
        Assertions.assertNotSame(form.binder, binder);
        Assertions.assertNotSame(form.binder, binder2);

        Person bean = testBean();

        form.binder.setBean(bean);
        Assertions.assertEquals(bean.getName(), form.name.getValue(), "Name field");

        form.binder.setBean(null);
        Assertions.assertEquals("", form.name.getValue(), "Expecting name field to have empty value for null bean");
    }

    private Person testBean() {
        Person item = new Person();
        item.setName("Attilio");
        return item;
    }

    @TestConfiguration
    @Import({Form.class, SpringBinderConfiguration.class})
    public static class Config {}

    @Component
    public static class Form {

        SpringBinder<Person> binder;
        TestField<String> name = new TestField<>(String.class, "");

        public Form() {}

        @Autowired
        public Form(SpringBinder<Person> binder) {
            this.binder = binder;
            this.binder.bindInstanceFields(this);
        }
    }
}
