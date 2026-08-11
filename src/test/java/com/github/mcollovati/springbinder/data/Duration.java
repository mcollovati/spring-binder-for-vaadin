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
package com.github.mcollovati.springbinder.data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Duration {

    @Min(0) private long amount;

    @NotEmpty private String timeUnit;

    public Duration(long amount, String timeUnit) {
        this.amount = amount;
        this.timeUnit = timeUnit.toUpperCase();
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(String timeUnit) {
        this.timeUnit = timeUnit;
    }

    @Override
    public String toString() {
        return amount + timeUnit.toUpperCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Duration duration = (Duration) o;
        return amount == duration.amount && timeUnit.equals(duration.timeUnit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, timeUnit);
    }

    public static Duration valueOf(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("^(\\d+)([a-zA-Z]+)$").matcher(text);
        if (matcher.matches()) {
            return new Duration(Long.parseLong(matcher.group(1)), matcher.group(2));
        }
        throw new IllegalArgumentException(
                "'" + text + "' is not a valid duration. " + "Syntax should be ([0-9]+)TIMEUNIT, e.g. 123SEC");
    }
}
