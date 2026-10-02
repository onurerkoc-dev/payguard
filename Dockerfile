# İlk aşama: kaynak koddan çalıştırılabilir JAR oluşturur.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/main/ src/main/
# Testler CI'da çalışır; Docker paketlemesi test veritabanı başlatmaz.
RUN ./mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

# İkinci aşama: yalnızca Java ve uygulamanın JAR dosyası bulunur.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system payguard && useradd --system --gid payguard payguard
COPY --from=build /app/target/payguard-*.jar app.jar
USER payguard
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
