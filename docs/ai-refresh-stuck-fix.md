# AI 재학습 '서버 반영 중' 무한 대기(1,270분) 수정

- 작업일: 2026-10-07
- 대상 파일
  - `src/main/java/com/onrender/homepick/service/AiDataRefreshService.java`
  - `src/main/java/com/onrender/homepick/controller/AiDataController.java`
  - `src/main/resources/static/js/ai-recommend.js`
  - `src/main/resources/templates/sub/ai-recommend.html`
  - `src/main/resources/application.properties`
  - `.github/workflows/ai-data-refresh.yml`

## 증상

어제 '최신 데이터로 AI 재학습'을 누른 뒤, 오늘 다시 페이지를 열어도
"학습 결과를 서버에 반영하는 중..." 상태로 경과 시간만 계속 늘어남 (약 1,270분).
카드 하단에는 "아직 학습된 데이터가 없습니다."가 표시됨.

## 원인

**GitHub Actions 는 정상 완료**되어 있었음.

- 2026-10-06 17:08 실행, `github-actions[bot]` 이 `apartments.json` 을 main 에 커밋 (실거래 33,166건, R² 0.897)

문제는 "서버 반영" 판단 방식:

- 기존 방식: **서버 jar 안에 포함된** `static/data/apartments.json` 의 생성 시각이 Actions 실행 시각 이후가 될 때까지 기다림
- Render 는 main 푸시 → 재배포로 새 파일이 들어오지만, **로컬 서버는 GitHub 의 새 커밋을 받을 방법이 없음** (git pull + 재빌드 전까지 영원히 옛 파일/파일 없음)
- 그래서 상태가 계속 `deploying` 으로 남았고, 대기 시간 제한도 없어 무한 대기

## 수정 내용

### 1. 서버가 GitHub 에서 최신 학습 결과를 직접 받아 제공 (핵심)

- `AiDataRefreshService`
  - GitHub Contents API 로 main 의 `apartments.json` 을 받아 **메모리에 보관** (30분마다 최신본 확인)
  - Actions 성공을 감지하면 즉시 다시 받아 반영 → `done`
  - GitHub 접근 실패 시 기존 캐시 유지, 캐시도 없으면 jar 포함본 사용
- `AiDataController`: `GET /api/ai-data/apartments` 추가 (추천 데이터 제공)
- `ai-recommend.js`: 데이터 주소를 `/data/apartments.json` → `/api/ai-data/apartments` 로 변경

→ **로컬·Render 모두 재배포 없이** 학습 완료 직후 새 데이터로 추천

### 2. Render 재배포 생략

- 워크플로 커밋 메시지에 `[skip render]` 추가 → 데이터 커밋으로 Render 가 재배포하지 않음
- 효과: 서버 재시작으로 **로그인 세션이 끊기던 문제 해소**, 완료 시간 5~8분 → 약 3~5분

### 3. 무한 대기 방지

| 상황 | 처리 |
|---|---|
| Actions 가 40분 넘게 '진행 중' | `failed` ("제한 시간을 넘겨 중단") |
| 학습 완료 후 15분 안에 반영 못 함 | `failed` ("서버에 반영하지 못했습니다") |
| 실패 시 | 진행 패널에 사유 표시, 기존 데이터로 계속 추천 |

- 상태 이름 변경: `deploying` → `syncing`

### 4. 응답 압축

- `server.compression.enabled=true` → 추천 데이터 1.9MB → 전송 약 264KB (gzip)

## 검증

- 실제 GitHub 데이터로 18080 포트에서 확인
  - `GET /api/ai-data/status` → `{"configured":true,"state":"done", ...}` (무한 대기 해소)
  - `GET /api/ai-data/apartments` → 200, 1,932,484B (gzip 263,986B)
  - 데이터 정보: "AI 학습 데이터: 2026-10-06 17:08 기준 · 실거래 33,166건"
  - 추천(서울 마포구·2방·8억 이하) → 상위 6개 단지, AI 분석 문장 정상 출력

## 적용 방법

1. 실행 중인 로컬 서버를 **완전히 재시작**
2. 이번 변경을 커밋·푸시 (워크플로의 `[skip render]` 는 main 에 올라가야 적용됨)
3. 로컬 저장소가 bot 커밋 1개만큼 뒤처져 있으므로 `git pull` 권장 (jar 포함본도 최신화)
