# 지도 중심 실거래 조회 (카카오맵)

헤더 메뉴 **매물 목록 → 지도 중심 실거래 조회**(`/map`)에서 AI 학습 데이터의 아파트 단지를 카카오맵에 표시합니다.
데이터는 **현재 판매 중인 매물이 아니라 거래가 끝난 실거래 기록**이므로, 메뉴 이름을 「지도 중심 매물 조회」에서 바꾸고 화면에도 이 사실을 표시했습니다.

## 화면 구성

| 영역 | 내용 |
|---|---|
| 제목 바 | 실거래 기간 · AI 학습 시각 · 지도에 표시된 단지 수, 「거래 완료 기록」 안내 |
| 왼쪽 필터 | 시·도 → 시·군·구, 최대 거래가(만원), 방 수(면적 추정), 단지명·동 검색, AI 저평가 단지만, 정렬(저평가·가격·최근 거래) |
| 왼쪽 목록 | **현재 지도에 보이는 영역**의 단지만 표시(최대 80개), 클릭하면 지도 이동 + 정보 팝업 |
| 오른쪽 지도 | 단지 마커(주황 = AI 저평가, 파랑 = 일반), 축소하면 마커를 숫자 원으로 묶어 표시(클러스터) |
| 정보 팝업 | 평형별 전용면적 · 최근 거래가 · AI 적정가 · 저평가 비율, 6개월 거래 건수 · 최근 거래일 |

- 같은 좌표·단지명의 평형(방 수)들은 마커 하나로 묶습니다.
- AI 저평가: RandomForest 적정가 ÷ 실제 거래가(`valueScore`) ≥ 1.05. 단, 할인율 40% 초과(`valueScore` > 1.67)는 지분·증여성 등 특수 거래 가능성이 커서 제외하고 저평가순 맨 뒤로 정렬 (시장 리포트와 같은 기준)
- 로그인 없이 누구나 이용할 수 있습니다. 모바일에서는 지도가 위, 목록이 아래로 배치됩니다.
- `KAKAO_JS_KEY` 가 없으면 지도 대신 안내 문구를 띄웁니다. 좌표 데이터가 아직 없으면 「좌표 데이터 준비 중」을 표시합니다.

## 데이터 흐름 (좌표 확보)

기존 학습 데이터에는 위도·경도가 없어서, AI 재학습 파이프라인에 **좌표 변환 단계**를 추가했습니다.

```
01_crawl → 02_preprocess (지번 보존) → 04_train (단지별 지번 출력) → 05_geocode (좌표 추가) → apartments.json
```

| 파일 | 변경 |
|---|---|
| `ml-pipeline/02_preprocess.py` | 국토부 응답의 `jibun`(지번) 컬럼을 남김 |
| `ml-pipeline/04_train.py` | 단지별 최근 거래의 지번을 `items[].jibun` 으로 출력 |
| `ml-pipeline/05_geocode.py` (신규) | 카카오 로컬 API로 「시도 시군구 동 지번」 → 좌표, `items[].lat/lng` 추가 |
| `ml-pipeline/geocode_cache.json` (자동 생성) | 주소 → 좌표 캐시. Git 에 커밋해 다음 재학습부터는 **새 단지만** 조회 |
| `ml-pipeline/common.py`, `run_all.py` | 캐시 경로, 5단계 실행 추가 |

좌표 변환 규칙
1. 지번 주소 검색 → 결과가 같은 시·도인지 확인
2. 행정구역 개편(인천 제물포구 등, 화성시 4개 구)으로 안 맞으면 구 이름을 빼고 재검색
3. 지번이 없거나 실패하면 「시군구 동 단지명」 키워드 검색
4. 찾지 못한 주소도 캐시에 기록해 매번 다시 조회하지 않음 (네트워크 오류는 기록하지 않고 다음에 재시도)

**안전 장치**: 키가 없거나 카카오맵 사용 설정이 꺼져 있으면(401/403) 경고만 출력하고 학습 결과는 그대로 둔 채 정상 종료합니다. 재학습 자체가 실패하지 않습니다.

## 서버 · 배포 변경

| 파일 | 변경 |
|---|---|
| `HomeController.java` | `GET /map` → `sub/map.html`, `kakaoJsKey` 전달 |
| `application.properties` | `kakao.map.js-key=${KAKAO_JS_KEY:}` |
| `templates/sub/map.html`, `static/css/map.css`, `static/js/map.js` | 지도 페이지 (신규) |
| `templates/common/header.html` | 메뉴 이름 변경 + `/map` 연결 |
| `.github/workflows/ai-data-refresh.yml` | 「단지 좌표 변환」 단계 추가(10분 제한), 캐시 파일도 커밋 |
| `.github/workflows/deploy.yml` | EC2 환경변수에 `KAKAO_JS_KEY` 추가(선택 항목) |
| `static/js/ai-recommend.js` | 재학습 진행 표시에서 「단지 좌표 변환」을 학습 단계로 표시 |
| `.env.example` | `KAKAO_JS_KEY`, `KAKAO_REST_API_KEY` 추가 |

같이 고친 것: `01_crawl.py` 의 수집 실패 로그에 공공데이터 인증키가 URL째 출력되던 문제 → `serviceKey=***` 로 가림.

## 필요한 설정

1. **카카오 디벨로퍼스 → 내 애플리케이션(homepick) → 제품 설정 → 카카오맵 → 사용 설정 ON**
   (꺼져 있으면 지도와 로컬 API 모두 `disabled OPEN_MAP_AND_LOCAL service` 오류)
2. **앱 설정 → 플랫폼 → Web 사이트 도메인** 등록: `http://localhost:8080`, `https://homepick.onrender.com`, `https://homepickkr.duckdns.org`
3. 환경변수 `KAKAO_JS_KEY`(앱 키 → JavaScript 키)
   - 로컬: `.env`
   - Render: 대시보드 Environment
   - EC2: GitHub Secrets `KAKAO_JS_KEY`
4. 좌표 변환용 REST 키: GitHub Actions 는 `KAKAO_REST_API_KEY` Secret, 없으면 기존 `KAKAO_CLIENT_ID`(같은 REST API 키)를 사용

## 검증

- 국토부 API 실제 응답에 `jibun` 필드가 있음을 확인 (예: 압구정동 493)
- 로컬에서 전처리 → 학습 → 좌표 변환 실행: 지번 포함 단지 10,325개 · 주소 7,070개 생성, 카카오맵 사용 설정이 꺼진 상태에서 경고 1회 후 정상 종료 확인
  (공공데이터 API 429 응답으로 이날 크롤링은 일부만 성공 → 확인용 결과는 커밋하지 않고 원래 데이터로 되돌림)
- `/map` 200 응답, 헤더 메뉴 링크, 카카오 SDK(clusterer) 로드 태그, 좌표 없을 때 안내 문구 화면 확인
- 카카오맵 사용 설정 후 GitHub Actions 재학습 실행: 단지 18,159개 · 주소 12,275개 조회 → **좌표 반영 18,156개(99.98%)**, 모든 단계 성공
- 지도 화면: 수도권 마커 클러스터, 지도 영역 목록(서울 기준 약 5,700개 단지), AI 저평가 표시 확인
