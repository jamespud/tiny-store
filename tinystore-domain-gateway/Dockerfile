# Gateway Service Dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built jar file
COPY target/tinystore-domain-gateway-1.0-SNAPSHOT.jar app.jar

# Expose service port
EXPOSE 8080

# Environment variables for configuration override
ENV SPRING_PROFILES_ACTIVE=default \
    REDIS_HOST=redis \
    REDIS_PORT=6379 \
    NACOS_SERVER_ADDR=nacos:8848 \
    JWKS_URI=http://auth:9000/.well-known/jwks.json

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
