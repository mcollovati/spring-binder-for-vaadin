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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ValidatorFactory;
import java.util.Locale;

import com.vaadin.flow.data.validator.BeanValidator;

/**
 * A Validator using the JSR-303 (jakarta.validation) annotation-based bean validation mechanism.
 *
 * <p>Values passed to this validator are compared against the constraints, if any, specified by
 * annotations on the corresponding bean property.
 */
public class SpringBeanValidator extends BeanValidator {

    private final ValidatorFactory validatorFactory;

    /**
     * Creates a new JSR-303 {@code BeanValidator} that validates values of the specified property.
     *
     * @param beanType the bean type declaring the property, not null
     * @param propertyName the property to validate, not null
     * @throws IllegalStateException if {@link
     *     com.vaadin.flow.internal.BeanUtil#checkBeanValidationAvailable()} returns false
     */
    public SpringBeanValidator(Class<?> beanType, String propertyName, ValidatorFactory validatorFactory) {
        super(beanType, propertyName);
        this.validatorFactory = validatorFactory;
    }

    public jakarta.validation.Validator getJavaxBeanValidator() {
        return validatorFactory.getValidator();
    }

    /**
     * Returns the interpolated error message for the given constraint violation using the locale
     * specified for this validator.
     *
     * @param violation the constraint violation
     * @param locale the used locale
     * @return the localized error message
     */
    protected String getMessage(ConstraintViolation<?> violation, Locale locale) {
        return validatorFactory
                .getMessageInterpolator()
                .interpolate(violation.getMessageTemplate(), createContext(violation), locale);
    }
}
