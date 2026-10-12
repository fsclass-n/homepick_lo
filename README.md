# HOMEPICK (홈픽)

> 공간을 공간답게 — 국토교통부 아파트 실거래 데이터를 AI가 학습해, 내 예산과 조건에 맞는 단지를 거래 기록으로 찾아 주는 부동산 플랫폼

- Render: <https://homepick.onrender.com>
- AWS EC2: <https://homepickkr.duckdns.org>

## 프로젝트 산출물

| No | 문서 |
|:-:|---|
| 1 | [플로 차트](docs/project/01-flowchart.md) |
| 2 | [프로젝트 기획서](docs/project/02-project-plan.md) |
| 3 | [요구사항 정의서](docs/project/03-requirements.md) |
| 4 | [프로세스 정의서](docs/project/04-process.md) |
| 5 | [시스템 아키텍처 설계서](docs/project/05-architecture.md) |
| 6 | [화면 설계서](docs/project/06-screen-design.md) |

전체 목차: [docs/project](docs/project/README.md)

## 주요 기능

- 4단계 회원가입 (약관 동의 · 기본 정보 · Firebase 휴대폰 인증 · 계정), BCrypt 비밀번호
- 아이디 로그인, 카카오 · 네이버 · 구글 소셜 로그인 (자동 회원가입)
- AI 실거래 단지 추천 (RandomForest 적정가 · 저평가, KMeans 단지 유형)
- AI 실거래 분석 리포트 (월별 추이 · 가격 결정 요인 · 시·군·구 순위, Plotly)
- 버튼 한 번으로 최신 실거래 크롤링 → 재학습 → 반영 (GitHub Actions)
- Q&A 게시판

## 기술 스택

Java 21 · Spring Boot 4 · Spring Security · JdbcTemplate · Thymeleaf · Bootstrap 5 · TiDB Cloud · Firebase Auth · OAuth2 · Python · pandas · scikit-learn · Plotly · Docker · GitHub Actions · Render · AWS EC2

## 실행

```bash
# 1) 루트에 .env 작성 (.env.example 참고)
# 2) 실행
./gradlew bootRun        # http://localhost:8080

# AI 파이프라인 (선택, 로컬 실행 시)
cd ml-pipeline && pip install -r requirements.txt && python run_all.py
```
