# Etapa de compilación
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copiar archivos del proyecto
COPY pom.xml .
COPY src ./src

# Compilar la aplicación
RUN mvn clean package -DskipTests

# Etapa de ejecución - usando una imagen compatible con ARM64
FROM arm64v8/openjdk:17-slim

# Crear un usuario no root para ejecutar la aplicación
RUN useradd -ms /bin/bash spring

# Establecer directorio de trabajo y propietario
WORKDIR /app
USER spring:spring

# Copiar el archivo JAR desde la etapa de compilación
COPY --from=build --chown=spring:spring /app/target/rubberduckie-1.0-SNAPSHOT.jar app.jar

# Exponer el puerto que usa Spring Boot por defecto
EXPOSE 8080

# Configurar punto de entrada para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]