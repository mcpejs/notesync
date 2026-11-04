# 빌드 단계: 테스트는 CI에서 돌리므로 여기서는 실행 jar만 만든다
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# settings.gradle.kts가 cli 모듈을 포함하므로 빌드 파일만 넣는다 (cli 소스는 서버 jar에 필요 없다)
COPY cli/build.gradle.kts cli/
COPY src src
RUN ./gradlew --no-daemon bootJar

# 실행 단계: JDK와 소스 없이 JRE와 jar만 둔다
FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home notesync
WORKDIR /app
COPY --from=build /workspace/build/libs/notesync-0.0.1-SNAPSHOT.jar app.jar
USER notesync
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
