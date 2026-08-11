# AGENTS.md - Spring Binder Add-on for Vaadin

This document provides guidelines for agentic coding assistants working in this repository.

## Project Overview

- **Java Version**: 21
- **Vaadin Version**: 25.2.5 (25.2+ is required by the browserless UI tests)
- **Spring Boot Version**: 4.0.4
- **Coordinates**: `com.github.mcollovati:spring-binder-for-vaadin`
- **Package**: `com.github.mcollovati.springbinder`

This is a library, not an application: there is nothing to run, and the UI tests
drive Vaadin in process.

## Build Commands

### Basic Build
```bash
mvn clean verify              # Compile, run all tests, check formatting
mvn clean install             # The above, plus install to the local repository
mvn install -DskipTests       # Skip tests
```

### Testing
```bash
mvn test                      # Unit and UI tests (all run under surefire)

# Run a specific test class or method
mvn test -Dtest=SpringBinderTest
mvn test -Dtest=SpringBinderTest#converterExists_applyConverterFromConversionService

# Validation messages and date formats depend on the locale: keep this green too
mvn test -DargLine="-Duser.language=it -Duser.country=IT"
```

### Packaging
```bash
mvn install -Prelease        # Also build the sources and javadoc jars needed by Maven Central
```

Releases go to Maven Central; the Vaadin Directory picks new versions up from
there, so no zip is built.

### Code Formatting
```bash
mvn spotless:apply           # Format code using palantir-java-format
mvn spotless:check           # Verify formatting; also runs as part of `mvn verify`
```

## Project Structure

```
src/main/java/com/github/mcollovati/springbinder/
├── AbstractSpringBinder.java    # Public abstract base class
├── SpringBinder.java           # Main Binder with Spring ConversionService
├── SpringBeanValidationBinder.java  # Binder with JSR-303 validation
├── SpringBeanValidator.java     # Custom validator with localized messages
├── SpringConverterFactory.java  # Bridge between Spring and Vaadin converters
├── ConversionOrder.java         # Whether Vaadin or Spring provides the converter
├── BinderConversionService.java # Qualifier for a ConversionService used only by the binders
├── SpringBinderFactory.java     # Creates binders outside an injection point
├── SpringBinderProvider.java    # Typed binder provider for one bean type
├── SpringBinderProperties.java  # `springbinder.*` configuration
└── SpringBinderConfiguration.java # Spring Boot auto-configuration

src/main/resources/META-INF/
├── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports  # Auto-config registration
└── resources/frontend/          # Frontend static resources

src/test/java/com/github/mcollovati/springbinder/
├── *.java                       # Unit tests (JUnit 6 + AssertJ + Mockito)
├── it/                          # UI tests (Vaadin Browserless Test + @SpringBootTest)
├── data/                        # Test beans and records
└── fields/                      # Test helper components
```

## Code Style Guidelines

### General Conventions

- **Indentation**: 4 spaces (standard Java convention)
- **Line Length**: No hard limit, but prefer readability
- **Braces**: K&R style (opening brace on same line)
- **Package Names**: `com.github.mcollovati.springbinder`
- **Class Names**: PascalCase (e.g., `SpringBinder`, `AbstractSpringBinder`)
- **Method Names**: camelCase (e.g., `createBinder`, `setRequiredConfigurator`)
- **Constant Names**: UPPER_SNAKE_CASE
- **Type Parameters**: Uppercase single letter or descriptive (e.g., `<BEAN>`, `<P, M>`)

### Imports

- **Group order** (enforced by spotless): `jakarta.*`/`java.*`/`javax.*`, then everything
  else, then `com.github.mcollovati.*`, then static imports in the same order
- **Blank line between major groups**
- **No wildcard imports** unless the file uses >10 types from the same package
- **Static imports** for test assertions: `import static org.assertj.core.api.Assertions.assertThat;`

### Javadoc

- **Required** for all public classes and public/protected methods
- **Format**: First sentence ends with period, then blank line before description
- **@param**, **@return**, **@throws** tags for all parameters and return values
- Use `{@link ClassName}` for class references
- Use `{@literal null}` for null references in parameter descriptions

Example:
```java
/**
 * Creates a new binder for the given bean or record type.
 * <p>
 * Spring {@link ConversionService} is used to provide suitable converters.
 *
 * @param beanType
 *            the bean type to use, not {@literal null}.
 * @param conversionService
 *            the conversion service.
 */
public SpringBinder(Class<BEAN> beanType, ConversionService conversionService) {
```

### Types

- Use **generics** appropriately; avoid raw types except for legacy APIs
- Use **`Optional`** for nullable return values that represent absence
- Mark **transient** fields that shouldn't be serialized (e.g., `ConversionService`)
- Use **`var`** sparingly in test code for readability

### Error Handling

- Use **`Objects.requireNonNull()`** with descriptive message for null checks in constructors
- Use **`assert`** for internal invariants (not for public API validation)
- Wrap checked exceptions in unchecked where appropriate (e.g., in `Converter`)

### Assertions in Tests

- **Unit tests**: Use JUnit 5 `Assertions` class and AssertJ fluent assertions
```java
import static org.assertj.core.api.Assertions.assertThat;
assertThat(result.getDate()).isEqualToIgnoringMillis(newDate);
```
- **Mockito**: Use `mock()` for creating mocks, `when()` for stubbing
- **UI tests**: Use Vaadin Browserless Test (`SpringBrowserlessTest`), which runs the
  Vaadin session in process. Locate components with `findInView(...)`, not the
  deprecated `$view(...)`. They are named `*Test` and run with the unit tests.

### Annotations

- **@Override**: Always use when overriding methods
- **@SuppressWarnings**: Use sparingly with specific warning types
```java
@SuppressWarnings({ "rawtypes" })
```
- **@FunctionalInterface**: Apply to single-method interfaces

### Access Modifiers

- **Public**: everything users are meant to touch, which includes the binders,
  `AbstractSpringBinder` (to subclass) and `SpringConverterFactory` (to reuse the
  bridge to Spring converters on its own)
- **Protected**: Methods designed for subclass override, and the constructors taking
  a `PropertySet`
- **Package-private (default)**: the `@Bean` methods of the auto-configuration
- **Private**: Implementation details within classes

### Records and Classes

- Use **records** for simple immutable data carriers (if targeting Java 16+)
- Use **classes** for complex objects with behavior
- Prefer **composition over inheritance**

### Vaadin-Specific Guidelines

- Extend Vaadin's `Binder<BEAN>` for binder implementations
- Use `HasValue` interface for field components
- Implement `ConverterFactory` to bridge between conversion systems
- Use `BeanPropertySet` and `PropertyDefinition` for property introspection

### Spring Integration

- Use Spring's `ConversionService` for type conversions
- Use Spring's `ValidatorFactory` for JSR-303 validation
- Follow Spring Boot auto-configuration patterns with `@AutoConfiguration`

## Git Commit Messages

Format: `type: subject` where type is `feat:`, `fix:`, `refactor:`, `test:`,
`docs:`, `chore:` or `build:`

- Subject: 50 chars or less, no trailing period
- Body: one sentence on the actual problem being fixed, when there is one, then a
  few sentences on what the change does. Plain language, no jargon and no technical
  detail unless it genuinely matters
- **Never** add a `Co-Authored-By` trailer
- Author is `Marco Collovati <mcollovati@gmail.com>`
- Prefer several small focused commits over one large one
- End with an issue reference when there is one: `Fixes #1234`
