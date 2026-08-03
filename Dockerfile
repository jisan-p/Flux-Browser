FROM maven:3.9.9-eclipse-temurin-21

WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
COPY docker ./docker

CMD ["mvn", "--batch-mode", "clean", "verify"]
