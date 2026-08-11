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

import java.lang.management.ManagementFactory;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.Options;
import com.microsoft.playwright.junit.OptionsFactory;
import com.microsoft.playwright.junit.UsePlaywright;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@UsePlaywright(AddonIT.DebugOptionsFactory.class)
public class AddonIT {

    public static class DebugOptionsFactory implements OptionsFactory {
        @Override
        public Options getOptions() {
            return new Options().setHeadless(!isJavaInDebugMode());
        }

        static boolean isJavaInDebugMode() {
            return ManagementFactory.getRuntimeMXBean()
                    .getInputArguments()
                    .toString()
                    .contains("jdwp");
        }
    }

    @TestConfiguration
    static class TestConfig {}

    @LocalServerPort
    private int serverPort;

    Page page;

    @BeforeEach
    void navigateToPage(Page page) {
        this.page = page;
        page.navigate("http://localhost:" + serverPort + "/");
    }

    @Test
    public void addonTextIsRendered() {
        Locator div = page.getByTestId("theAddon");
        assertThat(div).hasText("Hello");
    }
}
