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

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot configuration for the browserless UI tests.
 *
 * <p>Auto-configuration is enabled so that {@link
 * com.github.mcollovati.springbinder.SpringBinderConfiguration} is picked up exactly as it would be
 * in a real application. There is no {@code main} method on purpose: the tests run the Vaadin
 * session in-process, without an HTTP server.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
public class TestApplication {}
