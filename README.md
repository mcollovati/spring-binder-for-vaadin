# Spring Binder Add-on for Vaadin

An injectable Vaadin Flow `Binder` that uses Spring's `ConversionService` to
convert between field (presentation) and bean (model) types, plus a
`BeanValidationBinder` variant that interpolates JSR-303 messages through
Spring's `ValidatorFactory`.

- Conversions registered as Spring `Converter` beans are applied automatically,
  including by `bindInstanceFields()`; anything Spring cannot convert falls back
  to Vaadin's default converters.
- Binders are injectable, prototype-scoped beans: the bean type is resolved from
  the injection point's generic parameter.
- Zero configuration: the add-on is a Spring Boot auto-configuration.

## Requirements

| | |
|---|---|
| Java | 21+ |
| Vaadin | 25.2+ (built and tested against 25.2.5) |
| Spring Boot | 4.x |

## Installation

```xml
<dependency>
    <groupId>com.github.mcollovati</groupId>
    <artifactId>spring-binder-for-vaadin</artifactId>
    <version>1.0.0</version>
</dependency>
```

No `@Import` or `@Enable...` annotation is needed: `SpringBinderConfiguration`
is registered as a Spring Boot auto-configuration.

## Usage

Inject the binder into a view and let it bind the fields:

```java
@Route("race-result")
public class RaceResultView extends VerticalLayout {

    private final TextField team = new TextField("Team");
    private final IntegerField place = new IntegerField("Place");
    private final TextField duration = new TextField("Duration");

    public RaceResultView(SpringBinder<RaceResult> binder) {
        add(team, place, duration);
        binder.bindInstanceFields(this);
        binder.setBean(new RaceResult());
    }
}
```

`duration` is a `TextField`, while `RaceResult.duration` is a custom `Duration`
type. `bindInstanceFields()` would normally fail on that mismatch; with this
add-on the conversion is taken from Spring:

```java
@Bean
Converter<String, Duration> stringToDuration() {
    return Duration::valueOf;
}

@Bean
Converter<Duration, String> durationToString() {
    return Duration::toString;
}
```

Both directions must be registered — a binding needs to convert in both
directions, so a one-way Spring conversion is ignored in favour of Vaadin's
default converter. See [Conversion precedence](#conversion-precedence) for which
side wins when both can convert a pair.

The same applies to any type, for example a project-wide date format:

```java
@Bean
Converter<String, LocalDate> stringToLocalDate() {
    return text -> LocalDate.parse(text, DateTimeFormatter.ISO_DATE);
}
```

### Bean validation

With `spring-boot-starter-validation` on the classpath (that is: with a
`ValidatorFactory` bean available), inject `SpringBeanValidationBinder` to get
JSR-303 constraints applied automatically, including the required indicator:

```java
public RaceResultView(SpringBeanValidationBinder<RaceResult> binder) { ... }
```

Constraint messages are interpolated by the `ValidatorFactory`'s
`MessageInterpolator` using the locale of the current Vaadin UI, so
`ValidationMessages.properties` (and its `_xx` variants) are honoured:

```properties
# ValidationMessages_it.properties
jakarta.validation.constraints.Size.message = la dimensione deve essere compresa tra {min} e {max}
```

To resolve messages from a Spring `MessageSource` instead, expose a
`LocalValidatorFactoryBean` configured with
`setValidationMessageSource(messageSource)`.

Injecting the base `Binder<T>` also works: it resolves to
`SpringBeanValidationBinder` when a `ValidatorFactory` bean exists and to
`SpringBinder` otherwise. Inject a concrete type when you want to be explicit
about which one you get.

## Conversion precedence

By default Vaadin's own converters are used for the type pairs Vaadin knows, and
Spring is asked for everything else. That is what you want almost always, because
Spring's default converters are more eager than they look: `ObjectToObject`
matches any type with a `valueOf`/`of`/`from`/`String` constructor, and *every*
type converts to `String` through `toString()`. Left to Spring, a text field bound
to a `java.util.Date` would round-trip through the deprecated `new Date(String)`
and `Date.toString()`, and numeric fields would lose Vaadin's locale-aware parsing
and its readable error messages.

Vaadin only covers common form-field pairs — text to numbers, booleans, dates and
UUIDs, plus numeric widening — so your own domain types are still converted by
Spring, which is the point of the add-on.

To let a Spring converter override a pair Vaadin also handles, flip the order:

```properties
vaadin-spring-binder.conversion.order=spring-first
```

or per binder:

```java
new SpringBinder<>(RaceResult.class, conversionService, ConversionOrder.SPRING_FIRST);
```

A single binding can always opt out on its own, whichever order is configured:

```java
binder.forField(dateField)
        .withConverter(new StringToDateConverter())
        .bind(RaceResult::getDate, RaceResult::setDate);
```

### Which `ConversionService` is used

The add-on uses the application's `ConversionService` bean, and registers a
`DefaultConversionService` of its own only when there is none. In a Spring Boot
web application there usually *is* one already — the format-aware MVC conversion
service — so `spring.mvc.format.*` settings apply to bindings too. If several
`ConversionService` beans exist and none is `@Primary`, the binders fall back to
the shared `DefaultConversionService` rather than failing to start.

To give the binders a conversion service of their own, qualify it with
`@BinderConversionService`. Combined with `spring-first`, a registry built without
Spring's defaults gives you exactly the conversions you registered and nothing
else — no `ObjectToObject`, no `toString()` fallback:

```java
@Bean
@BinderConversionService
ConversionService binderConversions(Converter<String, Duration> toDuration,
        Converter<Duration, String> fromDuration) {
    GenericConversionService conversions = new GenericConversionService();
    conversions.addConverter(toDuration);
    conversions.addConverter(fromDuration);
    return conversions;
}
```

## Building

```bash
mvn clean verify      # compile, unit tests and browserless UI tests
mvn spotless:apply    # format sources (palantir-java-format)
mvn install -Prelease # also build the sources and javadoc jars
```

UI tests use [Vaadin Browserless Test](https://vaadin.com/docs), so they run
in-process — no browser or servlet container involved.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
