# AGENTS.md - Spring Binder Add-on for Vaadin

This document provides guidelines for agentic coding assistants working in this repository.

## Project Overview

- **Java Version**: 21
- **Vaadin Version**: 25.1.0
- **Spring Boot Version**: 4.0.4
- **Package**: `io.github.mcollovati.springbinder`

## Build Commands

### Basic Build
```bash
mvn install                    # Build and install to local repo (skip tests)
mvn clean install              # Clean build and install
```

### Running the Application
```bash
mvn spring-boot:run           # Start development server at http://localhost:8080
```

### Testing
```bash
mvn test                      # Run unit tests only
mvn verify                    # Run unit tests + integration tests

# Run a specific test class
mvn test -Dtest=SpringBinderTest
mvn test -Dtest=SpringBinderTest#converterExists_applyConverterFromConversionService

# Run integration tests only (Spring Boot auto-starts)
mvn verify -Pit

# Skip tests
mvn install -DskipTests
```

### Packaging
```bash
mvn install -Pdirectory       # Package for Vaadin Directory (creates target/spring-binder-{version}.zip)
```

### Code Formatting
```bash
mvn spotless:apply           # Format code using Google Java Format
mvn spotless:check           # Verify formatting (fails if not formatted)
```

## Project Structure

```
src/main/java/io/github/mcollovati/springbinder/
├── AbstractSpringBinder.java    # Abstract base class (package-private)
├── SpringBinder.java           # Main Binder with Spring ConversionService
├── SpringBeanValidationBinder.java  # Binder with JSR-303 validation
├── SpringBeanValidator.java     # Custom validator with localized messages
├── SpringConverterFactory.java  # Bridge between Spring and Vaadin converters
├── SpringBinderConfiguration.java # Spring Boot auto-configuration
└── it/TestServer.java          # Spring Boot application for testing (main sources)

src/main/resources/META-INF/
├── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports  # Auto-config registration
└── resources/frontend/          # Frontend static resources

src/test/java/io/github/mcollovati/springbinder/
├── *.java                       # Unit tests (JUnit 5 + AssertJ + Mockito)
├── it/                          # Integration tests (Playwright + @SpringBootTest)
└── fields/                      # Test helper components
```

## Code Style Guidelines

### General Conventions

- **Indentation**: 4 spaces (standard Java convention)
- **Line Length**: No hard limit, but prefer readability
- **Braces**: K&R style (opening brace on same line)
- **Package Names**: `io.github.mcollovati.springbinder`
- **Class Names**: PascalCase (e.g., `SpringBinder`, `AbstractSpringBinder`)
- **Method Names**: camelCase (e.g., `createBinder`, `setRequiredConfigurator`)
- **Constant Names**: UPPER_SNAKE_CASE
- **Type Parameters**: Uppercase single letter or descriptive (e.g., `<BEAN>`, `<P, M>`)

### Imports

- **Group order**: java.*, javax.*, org.springframework.*, com.vaadin.*, third-party, project
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
- **Integration tests** (IT suffix): Use Playwright for browser automation

### Annotations

- **@Override**: Always use when overriding methods
- **@SuppressWarnings**: Use sparingly with specific warning types
```java
@SuppressWarnings({ "rawtypes" })
```
- **@FunctionalInterface**: Apply to single-method interfaces

### Access Modifiers

- **Public**: API classes (`SpringBinder`, `SpringBeanValidationBinder`)
- **Protected**: Methods designed for subclass override
- **Package-private (default)**: Internal implementation classes (`AbstractSpringBinder`, `SpringConverterFactory`)
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

Format: `type: subject` where type is `fix:`, `feat:`, `chore:`, or `refactor:`

- Wrap references like `@Component`, `@Injectable` in backticks
- Subject: 50 chars or less
- End with issue reference: `Fixes #1234`
