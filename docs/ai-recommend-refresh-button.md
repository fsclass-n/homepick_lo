# AI 맞춤 매물 추천: '최신 데이터로 AI 재학습' 버튼

- 작업일: 2026-10-06
- 목표: 버튼 한 번으로 공공데이터 재크롤링 → 전처리 → 머신러닝 학습 → 서버 반영, 이후 'AI 추천 받기'는 새 데이터로 검색
- 방식: **GitHub Actions 실행** (Render 이미지에 Python 을 넣지 않음, 결과 파일이 사라지지 않음)
- 권한: **로그인 사용자만** 버튼 표시·실행

## 전체 흐름

```
[브라우저] '최신 데이터로 AI 재학습' 클릭 (로그인 사용자)
   │ POST /api/ai-data/refresh
[Spring] 로그인·10분 쿨다운·실행 중 여부 확인 → GitHub API 로 workflow_dispatch
   │
[GitHub Actions] ai-data-refresh.yml
   저장소 준비 → Python 환경 준비 → 라이브러리 설치
   → 공공데이터 크롤링 → 데이터 전처리 → 머신러닝 학습 → 학습 결과 반영(apartments.json 커밋·푸시)
   │
[Render] main 푸시 감지 → 자동 재배포 (새 apartments.json 포함)
   │
[브라우저] 10초마다 GET /api/ai-data/status 로 진행 상태 확인 → 완료 시 새 데이터 다시 로드
```

## 화면 변경 (`ai-recommend.html / .css / .js`)

| 영역 | 내용 |
|---|---|
| 학습 데이터 정보 | 카드 하단에 `AI 학습 데이터: 2026-10-06 15:24 기준 · 실거래 12,345건` 표시 |
| 갱신 버튼 | `최신 데이터로 AI 재학습` (로그인 시), 비로그인 시 로그인 안내 링크 |
| 진행 패널 | 스피너 + 단계 제목 + 경과 시간 + 진행 막대 + 단계 칩(준비 → 공공데이터 크롤링 → 데이터 전처리 → 머신러닝 학습 → 서버 반영) |
| 완료 | 체크 아이콘, "최신 데이터로 AI 학습이 완료되었습니다!" → 데이터 자동 재로드 |
| 실패 | 주황색 경고, 기존 데이터로 계속 추천한다는 안내 |
| AI 결과 안내 문장 | 결과 상단: "이 결과는 AI가 {학습 시각}에 국토교통부 아파트 실거래 {N}건({기간})을 크롤링·학습한 모델(RandomForestRegressor + KMeans, 적정가 예측 정확도 R² x)로 분석해 추천한 것입니다." |
| 단지별 AI 분석 | "AI 분석: 예측 적정가보다 N% 낮게 거래된 저평가 단지입니다." 등 |

- 페이지를 새로고침하거나 다시 들어와도 진행 중이면 진행 패널을 이어서 표시
- 진행 중에도 'AI 추천 받기'는 기존 데이터로 동작, 완료 후에는 새 데이터로 추천

## 백엔드 (`AiDataController`, `AiDataRefreshService`)

| API | 설명 |
|---|---|
| `POST /api/ai-data/refresh` | 로그인 필수(401), 10분 쿨다운·실행 중이면 409, 설정 없으면 503 |
| `GET /api/ai-data/status` | `state`: `idle` / `running`(+`step`) / `deploying` / `done` / `failed`, 10초 캐시 |

- `deploying` 판단: Actions 는 성공했지만, 현재 서버에 배포된 `apartments.json` 의 `generatedAtEpoch` 가 실행 시작 시각보다 이전이면 아직 재배포 전
- GitHub API 호출은 Spring `RestClient` 사용 (추가 의존성 없음)

## ml-pipeline 변경 (`04_train.py`)

- JSON 맨 앞에 `generatedAtEpoch` (서버 반영 확인용), 한국 시간 `generatedAt`
- `tradeCount`, `periodFrom`, `periodTo`, `model`(이름·R²·MAE) 추가 → AI 안내 문장에 사용

## 설정 (직접 등록 필요)

1. **GitHub 저장소 Secrets** (Settings → Secrets and variables → Actions)
   - `DATA_GO_KR_API_KEY`: 공공데이터포털 일반 인증키(Decoding)
2. **GitHub Fine-grained token** 발급
   - Repository access: `fsclass-n/homepick_lo`만, Permissions → Actions: **Read and write**
3. **Render 환경변수** (로컬은 프로젝트 루트 `.env` 하나에 Spring·ml-pipeline 키를 함께 관리)
   - `GITHUB_ACTIONS_TOKEN`: 위 토큰
   - `GITHUB_REPO`: `fsclass-n/homepick_lo` (기본값과 같으면 생략 가능)
4. 이 변경을 main 에 푸시 (워크플로 파일이 main 에 있어야 실행 가능)

## 주의 사항

- 한 번 실행 시 공공데이터 API 약 150회 호출 (개발계정 일 10,000회), 10분 쿨다운 적용
- 완료까지 약 5~8분 (Actions 2~4분 + Render 재배포 3~5분)
- Render 재배포 시 서버가 재시작되어 **세션이 초기화됨** → 완료 후 다시 로그인이 필요할 수 있음 (진행 표시는 계속 동작)
- 쿨다운 시각은 서버 메모리에 저장되므로 재배포 후 초기화됨 (실행 중 여부는 GitHub 기준으로 다시 확인)
- 검증: 실제 키·토큰이 없어 GitHub 연동은 실행하지 못함. Java 컴파일, 가짜 데이터 학습, 화면 로직(진행 단계 전환·재배포 중 연결 끊김·완료 후 재로드·AI 안내 문장)을 시뮬레이션으로 확인함
