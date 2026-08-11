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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the add-on through a real Vaadin UI: the view gets its {@link
 * com.github.mcollovati.springbinder.SpringBinder} injected by the auto-configuration, and the
 * Spring {@code ConversionService} has to provide the {@code String <-> Duration} converter that
 * {@code bindInstanceFields} needs.
 */
@SpringBootTest(classes = TestApplication.class)
@ViewPackages(classes = SpringBinderView.class)
class SpringBinderViewTest extends SpringBrowserlessTest {

    @Test
    void injectedBinder_springConversionApplied_beanWritten() {
        navigate(SpringBinderView.class);

        LocalDate raceDate = LocalDate.of(2026, 8, 11);
        test($view(DatePicker.class).withLabel("Date").single()).setValue(raceDate);
        test($view(TextField.class).withLabel("Team").single()).setValue("TEAM1");
        test($view(IntegerField.class).withLabel("Place").single()).setValue(3);
        test($view(TextField.class).withLabel("Duration").single()).setValue("120M");

        test($view(Button.class).withId(SpringBinderView.SAVE_ID).single()).click();

        Date expectedDate =
                Date.from(raceDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        assertThat($view(Div.class).id(SpringBinderView.LOG_ID).getText())
                .isEqualTo("RaceResult[date=" + expectedDate + ", team=TEAM1, place=3, duration=120M]");
    }

    @Test
    void injectedBinder_springConversionFails_beanNotWritten() {
        navigate(SpringBinderView.class);

        test($view(TextField.class).withLabel("Team").single()).setValue("TEAM1");
        test($view(TextField.class).withLabel("Duration").single()).setValue("not-a-duration");

        test($view(Button.class).withId(SpringBinderView.SAVE_ID).single()).click();

        assertThat($view(Div.class).id(SpringBinderView.LOG_ID).getText()).isEmpty();
    }
}
