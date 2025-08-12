# Agent Documentation

## Project Overview
This is a sample Kotlin project used for testing the onyx-ai-agent system. The agent should work within this project directory to complete coding tasks.

## Project Structure
```
sample-test-project/
├── build.gradle.kts        # Gradle build configuration
├── src/
│   ├── main/kotlin/
│   │   └── com/example/
│   │       ├── Util.kt     # Utility functions (SHA256, MD5)
│   │       └── Helpers.kt  # Helper functions (current time)
│   └── test/kotlin/
│       └── com/example/
│           └── UtilTest.kt # Unit tests
├── gradle/
│   └── wrapper/
└── Agent.md               # This file
```

## Build System
- Uses Gradle with Kotlin DSL
- JUnit 5 for testing
- Kotlin 1.9.x

## Common Tasks
1. **Running Tests**: Use `./gradlew test`
2. **Building**: Use `./gradlew build`
3. **Clean**: Use `./gradlew clean`

## Guidelines for Agent
- Always create proper package structure under `com.example`
- Use JUnit 5 for all tests (`@Test` annotation)
- Follow Kotlin coding conventions
- Ensure all tests pass before considering a task complete
- Use proper Gradle dependencies in build.gradle.kts

## Expected Functionality
The project should contain:
- Util object with cryptographic hash functions (SHA256, MD5)
- Helper functions for common operations
- Comprehensive unit tests
- Proper Gradle build configuration

## Test Requirements
- All hash functions should be tested with known input/output pairs
- Tests should use JUnit 5 assertions (`assertEquals`, etc.)
- Test files should follow naming convention: `*Test.kt`
