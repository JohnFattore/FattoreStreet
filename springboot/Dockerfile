# Stage 1: Build
# maven:3.x clears the enforcer requireMavenVersion floor (>= 3.9.0);
# temurin-25 matches <java.version> and the stage-2 runtime, so the jar is
# built and run on the same JDK. Always take an LTS here: the previous
# eclipse-temurin:19 was non-LTS, went EOL, and left this image stranded on a
# dead tag family that Dependabot could not bump.
FROM maven:3.10.0-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
# Warm the local Maven repository in a layer that only invalidates when pom.xml
# changes; -Dquality.skip=true matches the package step below so unused Error
# Prone artifacts aren't downloaded.
RUN mvn -B dependency:go-offline -Dquality.skip=true
COPY src ./src
# -Dquality.skip=true: this stage copies only pom.xml + src, so config/, .git
# and .mvn are absent and any gate resolving them would fail here. Spotless,
# Checkstyle, SpotBugs and the enforcer run in CI (.github/workflows/ci.yml).
RUN mvn -B clean package -DskipTests -Dquality.skip=true

# Stage 2: Runtime
# noble (Ubuntu 24.04) is deliberate: the AWS CLI v2 install below is a glibc
# binary with no musl build, so alpine is not an option here.
FROM eclipse-temurin:25.0.5.1_2-jre-noble
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