FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package dependency:copy-dependencies -DoutputDirectory=target/dependency

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/classes ./bin
COPY --from=build /app/target/dependency ./dependency
COPY web ./web
EXPOSE 10000
CMD ["sh", "-c", "java ${JAVA_OPTS:-} -cp 'bin:dependency/*' Main"]
