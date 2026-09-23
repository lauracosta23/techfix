# =====================================================================
#  TechFix Inteligente - imagem de producao
#  Dois estagios: o primeiro compila, o segundo so roda.
#  Assim a imagem final nao carrega o Maven nem o codigo-fonte.
# =====================================================================

# ---------- estagio 1: compilar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# O pom sozinho primeiro: se ele nao mudar, o Docker reaproveita
# as dependencias ja baixadas e o build seguinte fica bem mais rapido.
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -B -DskipTests clean package

# ---------- estagio 2: executar ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# finalName do pom.xml = aula08-avaliacao-kit
COPY --from=build /app/target/aula08-avaliacao-kit.jar app.jar

# O plano gratuito do Render da 512 MB. Limitamos a heap a 70% disso e
# usamos o coletor serial, que gasta menos memoria em container pequeno.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"

# Documental: quem manda mesmo e a variavel PORT, lida pelo application.properties.
EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
