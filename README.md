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
| Vaadin | 25.2+ (built and tested against 25.2.6) |
| Spring Boot | 4.x |

Java 21 applies to the JDK the application is built with, not only to the JVM it
runs on. The add-on is compiled to Java 21 bytecode, so an older JDK cannot read
it:

```
bad class file: .../spring-binder-for-vaadin-1.0.0.jar(/com/github/mcollovati/springbinder/SpringBinder.class)
  class file has wrong version 65.0, should be 61.0
```

Spring Boot 4 itself only requires Java 17, so a project can be on Spring Boot 4
and still be building with a JDK too old for this add-on. Targeting an earlier
release from a JDK 21 toolchain — `maven.compiler.release` 17, say — does compile,
but the JVM running the application still has to be 21 or later.

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

The bean type comes from the generic parameter of the injection point, so nothing
passes it explicitly. `getBeanType()` reads it back, which is the way to assert an
injected binder really was built for the type the view expects:

```java
assertThat(binder.getBeanType()).contains(RaceResult.class);
```

It is empty only for a binder created from a `PropertySet`, where there is no bean
type to report.

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

With a JSR-303 provider on the classpath — Hibernate Validator already arrives
transitively with `vaadin-spring`, so usually there is nothing to add — inject
`SpringBeanValidationBinder` to get JSR-303 constraints applied automatically,
including the required indicator:

```java
public RaceResultView(SpringBeanValidationBinder<RaceResult> binder) { ... }
```

Validation runs through the application's `ValidatorFactory` bean when there is
one, which is what `spring-boot-starter-validation` registers. Without that
starter the add-on builds a factory of its own, backed by the application
context so that constraint validators can still be Spring beans. Either way the
binder validates like Vaadin's own `BeanValidationBinder`, and adopting the
add-on does not force a new starter on the application.

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
`SpringBeanValidationBinder` when a JSR-303 provider is available and to
`SpringBinder` otherwise. Inject a concrete type when you want to be explicit
about which one you get.

## More than one binder, or a component Spring does not manage

An injected binder is one binder. That is the wrong shape in two common cases:
a component that builds a form per row of a grid, and a form component created
with `new`, which Spring never sees.

Do **not** reuse a single injected binder for several rows. Vaadin's `Binder`
lets the same property be bound more than once, so it compiles, starts, and then
silently has every row read and write the same bean.

When the bean type is known at the injection point, inject a
`SpringBinderProvider<T>` and ask it for one binder per form:

```java
@Route("admin")
public class AdminView extends VerticalLayout {

    private final SpringBinderProvider<Category> binders;

    public AdminView(SpringBinderProvider<Category> binders) {
        this.binders = binders;
    }

    private Component createCategoryEditor(Category category) {
        SpringBeanValidationBinder<Category> binder = binders.getBeanValidation();
        ...
    }
}
```

When the component is not a Spring bean, or needs binders for several bean
types, inject the `SpringBinderFactory` singleton and pass it down:

```java
class OrderItemsEditor {

    private final SpringBinderFactory binders;

    OrderItemsEditor(SpringBinderFactory binders) {
        this.binders = binders;
    }

    private OrderItemEditor createEditor() {
        return new OrderItemEditor(binders.createBeanValidation(OrderItem.class));
    }
}
```

Both create binders wired exactly like injected ones — same `ConversionService`,
same conversion order, same `ValidatorFactory` — so the factory is also the way
to build binders in tests, instead of calling a constructor and getting
different conversion behaviour than production.

## Session serialization

**The binders, `SpringBinderFactory` and `SpringBinderProvider` are not
serializable.** Keep them out of a Vaadin component's serialized state by marking
the field `transient`:

```java
@Route("product")
public class ProductView extends VerticalLayout {

    private final transient SpringBeanValidationBinder<Product> binder;

    public ProductView(SpringBeanValidationBinder<Product> binder) {
        this.binder = binder;
        ...
    }
}
```

This only matters if the session is ever serialized — a clustered deployment, or a
container that passivates sessions to disk. Vaadin's own `Binder` is serializable,
but these binders hold a Spring `ConversionService`, and the validating one a
`ValidatorFactory`, neither of which is. Writing a session that reaches one fails
with a `NotSerializableException` naming the service or the factory.

That failure is deliberate, and it is the reason the add-on does not simply mark
those fields `transient` itself. Doing so would let the session be written and then
restore a binder with no conversion service, failing later and far from the cause.
Resolving the beans again on the other side is not sound either: Spring's
serialization support for a bean factory resolves through a registry private to a
single JVM and **falls back to an empty bean factory** when the entry is missing, so
a session restored on another node would quietly convert through the wrong service
and lose every `Converter` bean the application registered.

A `transient` binder is `null` after the session is restored, and its bindings are
gone with it, so the view has to build its form again — from a fresh injection or
through `SpringBinderFactory`. There is no way to bring the bindings back; a form
whose state must survive passivation has to be rebuilt from the bean.

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

The binders use, in this order:

1. the `ConversionService` bean qualified with `@BinderConversionService`, if there
   is one. The qualifier names a single service, so several beans carrying it fail
   the context at startup rather than being quietly ignored.
2. otherwise the application's own `ConversionService` bean. In a Spring Boot web
   application there *is* one already — the format-aware MVC conversion service —
   so `spring.mvc.format.*` settings apply to bindings too.
3. otherwise a conversion service the add-on builds itself, holding Spring's
   default converters plus every `Converter`, `GenericConverter`, `Formatter`,
   `Printer` and `Parser` the application registers as a bean.

Step 3 is what makes converter beans work in an application that has no
`ConversionService` of its own: Spring Boot collects those beans into the MVC
conversion service for a servlet application, and nothing collects them anywhere
else — so a plain context, such as a `@SpringBootTest(webEnvironment = NONE)`,
would otherwise convert through a registry holding none of your converters.

If several `ConversionService` beans exist and none is `@Primary`, the binders
cannot tell which one you meant. They log a warning and use the service from step
3, which at least still has your converter beans in it. Annotate one with
`@BinderConversionService` to choose.

The add-on never publishes a `ConversionService` bean of its own, so it cannot
change what the rest of the application injects.

To give the binders a conversion service of their own, qualify it with
`@BinderConversionService`. Combined with `spring-first`, a registry built without
Spring's defaults gives you exactly the conversions you registered and nothing
else — no `ObjectToObject`, no `toString()` fallback:

```java
@Bean
@BinderConversionService
ConversionService binderConversions() {
    GenericConversionService conversions = new GenericConversionService();
    conversions.addConverter(String.class, Duration.class, Duration::valueOf);
    conversions.addConverter(Duration.class, String.class, Duration::toString);
    return conversions;
}
```

Name the source and target types explicitly. `addConverter(Converter)` reads them
from the converter's own class, which a lambda or a method reference does not
declare, and passing one throws `IllegalArgumentException: Unable to determine
source type <S> and target type <T> for your Converter`.

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
