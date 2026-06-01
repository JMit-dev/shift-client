# shift-client

A lightweight Java 11 REST client library for the shift service API. Used by `shift-app` for the UI and by `phoebus-olog-shift-module` (indirectly) for the olog server-side integration.

## Requirements

- Java 11+
- Maven 3.6+

## Building

```bash
mvn clean install
```

This installs the JAR to your local Maven repository so other projects can depend on it.

## Usage

```java
ShiftClient client = ShiftClient.builder()
    .baseUrl("http://shift-service/Shift/resources")
    .username("user")       // optional Basic Auth
    .password("pass")
    .connectTimeout(Duration.ofSeconds(3))
    .readTimeout(Duration.ofSeconds(5))
    .build();

// Get the currently active shift for a given type
Shift shift = client.getShift("Operations");

// List all shifts
List<Shift> all = client.listShifts();

// List available shift types
List<ShiftType> types = client.listTypes();
```

## API

| Method | Endpoint | Returns |
|---|---|---|
| `getShift(String type)` | `GET /shift/{type}` | Active `Shift` or `null` if none / 404 |
| `listShifts()` | `GET /shifts` | All `Shift` objects, empty list on 404 |
| `listTypes()` | `GET /shiftTypes` | All `ShiftType` objects, empty list on 404 |

All methods throw `ShiftClientException` on HTTP 5xx responses or network failures.

## Maven dependency

After running `mvn install`, add to your project's `pom.xml`:

```xml
<dependency>
    <groupId>org.phoebus</groupId>
    <artifactId>shift-client</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Running tests

```bash
mvn test
```

Tests use WireMock to drive real HTTP interactions — no external services needed.
