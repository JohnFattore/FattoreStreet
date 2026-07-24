# Stage 1: Build
# maven:3.9.9 matches .mvn/wrapper/maven-wrapper.properties and the enforcer
# requireMavenVersion floor; temurin-17 matches <java.version>. The previous
# maven:3.8-openjdk-18 was EOL and ran a JDK the project never targets.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# -Dquality.skip=true: this stage copies only pom.xml + src, so config/, .git
# and .mvn are absent and any gate resolving them would fail here. Spotless,
# Checkstyle, SpotBugs and the enforcer run in CI (.github/workflows/ci.yml).
RUN mvn -B clean package -DskipTests -Dquality.skip=true

# Stage 2: Runtime
FROM eclipse-temurin:19-jre-focal
WORKDIR /app

# AWS CLI v2 + jq so the entrypoint can pull config from Secrets Manager at start.
# uname -m -> aarch64 | x86_64, which matches the AWS CLI download names exactly.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip jq ca-certificates \
    && curl -sSL "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o /tmp/awscliv2.zip \
    && unzip -q /tmp/awscliv2.zip -d /tmp \
    && /tmp/aws/install \
    && rm -rf /tmp/aws /tmp/awscliv2.zip /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Expose the application port
EXPOSE 8080

# Entrypoint loads SECRETS_ARN from Secrets Manager (if set), then runs the CMD.
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]