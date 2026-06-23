# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

The Besu-Shomei Plugin is a Besu plugin that supports the Shomei state management service for Linea. It adds RPC endpoints for trielog queries, a custom trielog serializer/deserializer, and a trielog observer that ships new block data to Shomei in real time.

## Build Commands

```bash
./gradlew build          # Build, test, check licenses, generate javadoc, and package
./gradlew test           # Run tests only (JUnit 5)
./gradlew spotlessApply  # Auto-fix code formatting
./gradlew spotlessCheck  # Check formatting without fixing
./gradlew assemble       # Compile and package without running tests
./gradlew plugin         # Alias for jar task
```

Run a single test class:
```bash
./gradlew test --tests "net.consensys.shomei.trielog.ZkTrieLogFactoryTests"
```

## Java Version

Java 21 is required. The build uses `-Werror` so all warnings are treated as errors.

## Code Formatting

Google Java Format 1.17.0 is enforced via Spotless. Import order: `net.consensys`, `java`, everything else. All source files require an Apache 2.0 license header (template at `gradle/spotless.java.license`). Run `./gradlew spotlessApply` before committing.

## Architecture

**Plugin system:** Two Besu plugins discovered via `@AutoService(BesuPlugin.class)`:
- `BesuShomeiRpcPlugin` — registers `shomei_*` RPC methods
- `ZkTrieLogPlugin` — registers trielog factory, observer, and block import tracer

**Shared state:** `ShomeiContext` (Bill Pugh singleton) holds all services and CLI options. Tests use `TestShomeiContext` from the `testSupport` source set.

**Key flows:**
- **Trielog serialization** — `ZkTrieLogFactory` implements `TrieLogFactory` for Shomei-compatible (de)serialization. Besu must be synced fresh with this plugin since the default serializer lacks required data.
- **Real-time sync** — `ZkTrieLogObserver` listens for `TrieLogEvent` and ships trielogs to Shomei over HTTP via Vert.x `WebClient`.
- **Block tracing** — `ZkBlockImportTracerProvider` provides `ZkTracer` instances for block imports, maintaining a FIFO history of max 3 tracers.
- **RPC methods** — `ShomeiGetTrieLog`, `ShomeiGetTrieLogsByRange`, `ShomeiGetTrieLogMetadata` extend `PluginRpcMethod`.

**CLI options** (defined in `ShomeiCliOptions`):
- `--plugin-shomei-http-host` / `--plugin-shomei-http-port` — Shomei endpoint
- `--plugin-shomei-enable-zktracer` — enable ZkTracer
- `--plugin-shomei-zktrace-comparison-mode` — bitmask for comparison mode
- `--plugin-shomei-skip-zktracer-until-block` — skip tracing before a block number

## Source Layout

- `src/main/java/net/consensys/shomei/` — plugin source
- `src/test/java/` — JUnit 5 tests (Mockito + AssertJ + Vert.x JUnit5)
- `src/testSupport/java/` — shared test utilities (packaged as separate JAR)
- `src/test/resources/` — test fixtures

## Key Dependencies

- **Besu BOM** (`org.hyperledger.besu:bom`) — plugin APIs and interfaces
- **Linea arithmetization** (`net.consensys.linea.zktracer:arithmetization`) — ZkTracer for proof generation (compileOnly + testImplementation)
- **Vert.x** (`io.vertx:vertx-web-client`) — async HTTP client for shipping trielogs
- Versions managed in `gradle.properties` and `gradle/versions.gradle`

## Static Analysis

Error-prone is enabled by default. Disable with `-Davt.disableErrorProne=true`. Key enforced checks include `MissingBraces`, `InsecureCryptoUsage`, `WildcardImport`, and `RedundantThrows`.
