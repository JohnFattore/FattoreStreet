# Stage 1: Build
FROM maven:3.8-openjdk-18 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

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