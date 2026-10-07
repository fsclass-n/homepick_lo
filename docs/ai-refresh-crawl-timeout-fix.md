# AI 재학습 실패("크롤링 또는 학습 중 오류") 원인 출력 및 크롤링 타임아웃 수정

- 작업일: 2026-10-07
- 대상 파일
  - `ml-pipeline/01_crawl.py`
  - `src/main/java/com/onrender/homepick/service/AiDataRefreshService.java`
  - `src/main/resources/static/js/ai-recommend.js`
  - `src/main/resources/static/css/ai-recommend.css`

## 증상

'최신 데이터로 AI 재학습' 실행 약 2분 후 진행 패널에
"크롤링 또는 학습 중 오류가 발생했습니다."만 표시되고, 원인을 알 수 없었음.

## 원인 (GitHub Actions 실행 기록·로그 확인)

- 실행: `actions/runs/37576679102` (2026-10-07 05:30 UTC) → **공공데이터 크롤링** 단계 실패
- 로그 마지막 오류:

```
requests.exceptions.ReadTimeout: HTTPSConnectionPool(host='apis.data.go.kr', port=443): Read timed out. (read timeout=20)
```

- 서울 25개 구 × 6개월 = 150회 요청 중, 진행 도중 **1건이 20초 안에 응답하지 않음**
- `01_crawl.py` 에 재시도가 없어서 단 1건의 일시적 지연으로 **전체 작업이 중단**
- 화면에는 GitHub 의 실패 여부만 전달되어 원인 문구가 없었음

## 해결

### 1. 크롤링 재시도·부분 실패 허용 (`01_crawl.py`)

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| 타임아웃 | 응답 20초 | 연결 10초 / 응답 40초 |
| 재시도 | 없음 | 최대 4회, 지수 백오프(2·4·8·16초), 429/5xx 포함 |
| 1건 최종 실패 | 전체 중단 | `[경고]` 출력 후 건너뛰고 계속 |
| 중단 기준 | - | 실패가 전체의 10% 초과 시 `CrawlError` 로 중단 (데이터 부족 방지) |

- 마지막에 `수집 완료: 성공/전체` 출력

### 2. 실패 원인 터미널 출력 (`AiDataRefreshService`)

Actions 실패를 감지하면 실패한 단계와 **로그의 마지막 오류 문장**을 찾아 서버 터미널에 한 번 출력:

```
>> [AI 재학습 실패] 단계: 공공데이터 크롤링
>>   원인: requests.exceptions.ReadTimeout: HTTPSConnectionPool(host='apis.data.go.kr', port=443): Read timed out. (read timeout=20)
>>   상세 로그: https://github.com/fsclass-n/homepick_lo/actions/runs/37576679102
```

- 그 밖의 터미널 출력
  - `>> [AI 재학습] GitHub Actions(ai-data-refresh.yml) 실행 요청`
  - `>> [AI 재학습 완료] 새 학습 데이터 반영 (generatedAtEpoch=...)`
  - 40분 초과 진행, 학습 후 데이터 반영 실패 시에도 사유 출력
- 로그는 GitHub API(`/actions/jobs/{id}/logs`)로 받음 → 기존 `GITHUB_ACTIONS_TOKEN`(Actions 읽기 권한)으로 충분
- 같은 실행은 한 번만 분석·출력 (실행 ID 기준 캐시)

### 3. 화면에 실패 단계·요약 원인 표시

- `/api/ai-data/status` 실패 응답에 `step`, `message` 포함

```json
{"state":"failed","step":"공공데이터 크롤링","message":"공공데이터 크롤링 단계 실패: 공공데이터 API 응답 시간 초과"}
```

- 진행 패널: 실패한 단계 칩을 주황색으로 강조, 이전 단계는 완료(✓) 표시, 제목에 요약 원인 표시
- 원인 요약 규칙: 응답 시간 초과 / 연결 실패 / `CrawlError` 내용 / `API 오류 [코드]` / 그 외 오류 문장 앞 120자

## 검증

- 실제 실패 실행(37576679102)으로 18080 포트 서버 확인
  - 터미널에 위 3줄(단계·원인·로그 링크) 출력
  - 상태 API: `state=failed`, `step=공공데이터 크롤링`, `message=...응답 시간 초과`
- 수정한 크롤러 로컬 실행(1개월, 25회 호출): `수집 완료: 25/25건 성공`
- Java 컴파일, JS 문법 검사 통과

## 적용

- 크롤러 수정(`01_crawl.py`)은 **main 에 푸시해야** GitHub Actions 에 반영됨
- 푸시 후 다시 '최신 데이터로 AI 재학습' 실행
