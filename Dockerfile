FROM node:22-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
# No workstation .env files enter the context; the public bundle uses same-origin URLs.
ENV VITE_API_BASE_URL=""
RUN npm run build

FROM maven:3.9.12-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=frontend /build/frontend/dist/ ./src/main/resources/static/
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --system pawtrack && useradd --system --gid pawtrack pawtrack \
    && mkdir /app/uploads && chown pawtrack:pawtrack /app/uploads
COPY --from=backend /build/backend/target/backend-0.0.1-SNAPSHOT.jar /app/pawtrack.jar
USER pawtrack
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -Djava.awt.headless=true"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/pawtrack.jar"]
