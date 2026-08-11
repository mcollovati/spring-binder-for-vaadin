# AGENTS.md - Spring Binder Add-on for Vaadin

This document provides guidelines for agentic coding assistants working in this repository.

## Project Overview

- **Java Version**: 21
- **Vaadin Version**: 25.2.6 (25.2+ is required by the browserless UI tests)
- **Spring Boot Version**: 4.1.0
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

Public API first, then the package-private machinery behind it.

```
src/main/java/com/github/mcollovati/springbinder/
├── package-info.java            # Package overview, and @NullMarked for the whole package
├── AbstractSpringBinder.java    # Public abstract base class; owns the serialization contract
├── SpringBinder.java            # Main Binder with Spring ConversionService
├── SpringBeanValidationBinder.java  # Binder with JSR-303 validation
├── SpringBeanValidator.java     # Custom validator with localized messages
├── SpringConverterFactory.java  # Bridge between Spring and Vaadin converters
├── ConversionOrder.java         # Whether Vaadin or Spring provides the converter
├── BinderConversionService.java # Qualifier for a ConversionService used only by the binders
├── SpringBinderFactory.java     # Creates binders outside an injection point
├── SpringBinderProvider.java    # Typed binder provider for one bean type
├── SpringBinderProperties.java  # `springbinder.*` configuration
├── SpringBinderConfiguration.java   # Spring Boot auto-configuration
├── DefaultSpringBinderFactory.java  # Package-private, the only SpringBinderFactory
├── DefaultSpringBinderProvider.java # Package-private, the only SpringBinderProvider
├── ConversionServiceResolver.java   # Package-private, picks the ConversionService to convert with
└── BinderValidatorFactory.java      # Package-private, picks the ValidatorFactory to validate with

src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports  # Auto-config registration

src/test/java/com/github/mcollovati/springbinder/
├── *.java                       # Unit tests (JUnit 6 + AssertJ + Mockito)
├── it/                          # UI tests (Vaadin Browserless Test + @SpringBootTest)
├── data/                        # Test beans and records
└── fields/                      # Test helper components
```

The add-on ships no frontend resources: it is Java only, with no client-side part.

The two `Default*` classes and the two resolvers are package-private on purpose.
Applications replace them through `@ConditionalOnMissingBean` on the
auto-configuration's `@Bean` methods, not by implementing the types directly.

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

Example, in the layout palantir-java-format produces — tag descriptions run on
from the tag, wrapping with a four space continuation indent, and `<p>` starts a
paragraph rather than sitting on a line of its own:
```java
/**
 * Creates a new binder for the given bean or record type.
 *
 * <p>Spring {@link ConversionService} is used to provide suitable converters for bindings when
 * presentation and model types are not compatible.
 *
 * @param beanType the bean type to use, not {@literal null}.
 * @param conversionService the conversion service.
 * @since 1.0
 */
public SpringBinder(Class<BEAN> beanType, ConversionService conversionService) {
```

Run `mvn spotless:apply` rather than hand-wrapping javadoc; it reflows these.

### Types

- Use **generics** appropriately; avoid raw types except for legacy APIs
- Use **`Optional`** for return values that represent absence. Name such a getter
  `findXxx`, not `getXxx`, when Vaadin's `Binder` could plausibly add a `getXxx`
  of its own later — an `Optional` return cannot override a non-`Optional` one
- Mark **transient** every field whose type is not serializable, and keep the
  enclosing type serializable. A binder has no say in whether it is written to a
  session: Vaadin's `Binder` registers a value-change listener on each field it binds
  and that listener holds the binder, so any bound field drags it in. That is why the
  `ConversionService` in `SpringConverterFactory` and the `ValidatorFactory` in
  `SpringBeanValidationBinder` and `SpringBeanValidator` are `transient`
- When a `transient` field is read back as `null`, **throw an `IllegalStateException`
  naming the cause** rather than letting a `NullPointerException` escape — the cause
  is a session restored somewhere else entirely, nowhere near the failure.
  `SpringConverterFactory.conversionService()` is the pattern
- Prefer holding a **Spring bean** in such a field over a value derived from one.
  Session replication tooling — Vaadin Kubernetes Kit — re-injects `transient` fields
  that referenced a bean, so a field holding one can be repaired after a restore while
  a field holding something built locally cannot
- `AbstractSpringBinder`'s javadoc is the reference for the whole contract and
  `SerializationTest` pins it — read both before touching any of this
- Use **`var`** sparingly in test code for readability

### Nullness

The package is `@NullMarked` (JSpecify), declared in `package-info.java`, so every
type usage is non-null unless annotated. When adding a member that genuinely takes
or returns `null`, annotate it `@Nullable` — do not describe it in prose only, and
do not leave it unannotated.

### Public API additions

Everything public freezes at the next release, so for a new public type or member:

- `@since` with the version it first appears in
- javadoc on the type and on every public or protected member
- prefer a `default` method on an interface over a new abstract one, so that
  implementors outside this repository keep compiling

### Error Handling

- Use **`Objects.requireNonNull()`** with descriptive message for null checks in constructors
- Use **`assert`** for internal invariants (not for public API validation)
- Wrap checked exceptions in unchecked where appropriate (e.g., in `Converter`)

### Assertions in Tests

- **Unit tests**: Use JUnit 6 `Assertions` class and AssertJ fluent assertions
```java
import static org.assertj.core.api.Assertions.assertThat;
assertThat(result.getDate()).isEqualToIgnoringMillis(newDate);
```
- **Mockito**: Use `mock()` for creating mocks, `when()` for stubbing
- **UI tests**: Use Vaadin Browserless Test (`SpringBrowserlessTest`), which runs the
  Vaadin session in process. Locate components with `findInView(...)`, not the
  deprecated `$view(...)`. They are named `*Test` and run with the unit tests.

**Check that a test fails without the thing it tests.** Spring's default converters
make this trap easy to fall into here: a test that registers a `Converter<String, Foo>`
and asserts a conversion happens will pass with that converter deleted, because
`ObjectToObjectConverter` finds `Foo.valueOf(String)` and `toString()` handles the way
back. Two tests in this repository were green for exactly that reason and caught
nothing. Give the fixture a value only the code under test can produce — a prefix, a
marker — or delete the collaborator and watch the test go red before keeping it.

### Annotations

- **@Override**: Always use when overriding methods
- **@SuppressWarnings**: Use sparingly with specific warning types
```java
@SuppressWarnings({ "rawtypes" })
```
- **@FunctionalInterface**: Apply to single-method interfaces

### Access Modifiers

- **Public**: everything users are meant to touch, which includes the binders,
  `AbstractSpringBinder` (to subclass), `SpringConverterFactory` (to reuse the
  bridge to Spring converters on its own) and the `SpringBinderFactory` and
  `SpringBinderProvider` interfaces
- **Protected**: Methods designed for subclass override, and the constructors taking
  a `PropertySet`
- **Package-private (default)**: the `@Bean` methods of the auto-configuration, and
  the implementations behind the two interfaces, so that only the interfaces are API
- **Private**: Implementation details within classes

### Records and Classes

- Use **records** for simple immutable data carriers
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
