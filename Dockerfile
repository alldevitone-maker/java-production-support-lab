FROM node:22-alpine AS ui
WORKDIR /app
COPY frontend/package*.json frontend/
RUN npm ci --prefix frontend
COPY catalog catalog
COPY frontend frontend
RUN npm run build --prefix frontend

FROM maven:3.9.11-eclipse-temurin-17 AS backend
WORKDIR /app
COPY backend/pom.xml pom.xml
RUN mvn -B dependency:go-offline
COPY backend/src src
COPY --from=ui /app/frontend/dist/ src/main/resources/static/
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S payops && adduser -S payops -G payops
WORKDIR /app
COPY --from=backend /app/target/payops-lab-0.2.0.jar app.jar
USER payops
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65.0","-jar","app.jar"]
