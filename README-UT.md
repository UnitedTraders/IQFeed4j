# United Traders Build and Publish Workflow

This project uses Gradle as its primary build system but includes a Maven `pom.xml` adapter to integrate with standard company CI/CD pipelines.

## Versioning
The current version is set to `6.2-2.0.1-UT` in both `build.gradle` and `pom.xml`.

## Local Development

### Build and Publish to Private Nexus
To publish the JAR (including sources and javadoc) to the UT Nexus repository manually:

```bash
./gradlew clean build publish -Pnexus.username=YOUR_USER -Pnexus.password=YOUR_PASS -Pnexus.url=YOUR_URL
```

Url format: "https://company_private_domain.team/nexus/content/repositories/releases/"

## CI/CD Workflow
The project is configured for Jenkins using the `jenkins-helper` library.

1. **Jenkinsfile**: Uses `maven-builder2-jdk21` to ensure compatibility with the Java 17 target.
2. **Maven Adapter**: The `pom.xml` delegates all lifecycle phases to Gradle:
   - `mvn clean` -> `./gradlew clean`
   - `mvn verify` -> `./gradlew build`
   - `mvn deploy` -> `./gradlew publish` (using `robot_maven` credentials from Vault)

## Requirements
- Java 17 or higher.
- Access to `https://nexus.unitedtraders.team/nexus/content/repositories/releases/`.
