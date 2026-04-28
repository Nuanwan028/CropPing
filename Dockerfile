FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN ./gradlew build

CMD ["sh", "-c", "java -jar build/libs/*.jar"]