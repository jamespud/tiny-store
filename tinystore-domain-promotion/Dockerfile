# Promotion Service Dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built jar file
COPY target/tinystore-domain-promotion-1.0-SNAPSHOT.jar app.jar

# Expose service port
EXPOSE 1200

# Environment variables for configuration override
ENV SPRING_PROFILES_ACTIVE=default \
    DB_HOST=postgres \
    DB_PORT=5432 \
    REDIS_HOST=redis \
    REDIS_PORT=6379 \
    KAFKA_BOOTSTRAP=kafka:9092 \
    NACOS_SERVER_ADDR=nacos:8848

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
