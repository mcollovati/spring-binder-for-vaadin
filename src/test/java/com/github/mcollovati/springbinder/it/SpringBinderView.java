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
package com.github.mcollovati.springbinder.it;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.Route;

import com.github.mcollovati.springbinder.SpringBinder;
import com.github.mcollovati.springbinder.data.RaceResult;

/**
 * View exercising an injected {@link SpringBinder} end to end: Spring provided converters are
 * applied by {@link Binder#bindInstanceFields(Object)} and the bean written on save is echoed into a
 * log element.
 */
@Route("")
public class SpringBinderView extends VerticalLayout {

    static final String LOG_ID = "log";
    static final String SAVE_ID = "save";

    /**
     * {@code transient} because that is what a view should do, even though nothing here serializes a
     * session: session replication tooling re-injects a {@code transient} field that held a Spring bean,
     * so the field comes back as a usable binder instead of the inert one deserialization would give.
     */
    private final transient Binder<RaceResult> binder;

    private final DatePicker date = new DatePicker("Date");
    private final TextField team = new TextField("Team");
    private final IntegerField place = new IntegerField("Place");
    private final TextField duration = new TextField("Duration");

    public SpringBinderView(SpringBinder<RaceResult> binder) {
        this.binder = binder;
        Div log = new Div();
        log.setId(LOG_ID);
        Button save = new Button("Save", e -> {
            log.removeAll();
            RaceResult result = new RaceResult();
            if (binder.writeBeanIfValid(result)) {
                log.setText(result.toString());
            }
        });
        save.setId(SAVE_ID);
        add(date, team, place, duration, save, log);
        binder.setBean(new RaceResult());

        this.binder.bindInstanceFields(this);
    }
}
