# ----------------------------------------------------
# 1. Build Stage: JDK 21 기반 빌드
# ----------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Gradle 래퍼 및 설정 파일 복사 (Docker 캐시 최적화)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Windows 환경 호환성을 위한 실행 권한 부여
RUN chmod +x ./gradlew

# 소스 코드 복사 (Thymeleaf, Vanilla JS, Bootstrap 정적 리소스 포함)
COPY src src

# 애플리케이션 빌드 (-x test로 빌드 시간 단축)
RUN ./gradlew build -x test --no-daemon

# ----------------------------------------------------
# 2. Run Stage: 경량화된 JRE 21 환경에서 컨테이너 실행
# ----------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 빌드 레이어에서 생성된 executable jar 파일만 추출 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# Render의 동적 동기화 포트 매핑 (기본값 8080)
ENV PORT=8080
EXPOSE ${PORT}

# Spring Boot 4.x 서버 실행 및 포트 바인딩
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]