# ----------------------------------------------------
# 1. Build Stage: Gradle 빌드 수행
# *.java -> (컴파일) -> .class -> 패키지 -> *.jar
# ----------------------------------------------------
# Alpine Linux 위에 Java 21 JDK가 설치된 Eclipse Temurin Docker 이미지
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Gradle 래퍼 및 설정 파일 복사 (캐시 활용을 위해 먼저 복사)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Windows 환경에서 작성된 gradlew 파일의 실행 권한 처리
RUN chmod +x ./gradlew

# 소스 코드 복사
COPY src src

# 애플리케이션 빌드 (테스트는 CI 단계에서 수행하므로 제외)
RUN ./gradlew build -x test --no-daemon

# ----------------------------------------------------
# 2. Run Stage: 경량화된 JRE 21 환경에서 실행
# ----------------------------------------------------
# 실행 환경도 동일하게 JRE 21 경량화 이미지로 변경
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 빌드 스테이지에서 생성된 jar 파일만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# Render 등 PaaS 환경에서 주입되는 PORT 변수를 기본값 8080으로 설정
ENV PORT=8080
EXPOSE ${PORT}

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]