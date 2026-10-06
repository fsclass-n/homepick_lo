# AI 맞춤 매물 추천: 크롤링 → 전처리 → 머신러닝 파이프라인

- 작업일: 2026-10-06
- 데이터: 국토교통부_아파트 매매 실거래가 자료 (`https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade`)
- 원칙: **DB 미사용(파일 기반)**, **자바 코드와 분리(Python 전용 폴더)**, **Render 에서는 결과 JSON 만 제공**

## 전체 구조

```
[로컬 PC] ml-pipeline (Python 3.11.9)
  01_crawl.py      공공데이터 API(XML) → data/raw/{지역코드}_{년월}.json
  02_preprocess.py 정제·파생변수 → data/processed/apartments.csv
  03_eda.py        Seaborn/Matplotlib/Plotly 리포트 → data/reports/
  04_train.py      scikit-learn 학습 → src/main/resources/static/data/apartments.json
        │
        └─ git commit/push (apartments.json 만)
                 │
[Render] Spring Boot jar 에 포함 → GET /data/apartments.json
                 │
[브라우저] ai-recommend.js 가 JSON 을 받아 조건별 추천 계산
```

- `ml-pipeline/data/` (원본·중간 산출물)는 `.gitignore`, `ml-pipeline/` 전체는 `.dockerignore` → 배포 이미지에 Python 이 들어가지 않음
- Render 는 실행 중 파일을 쓰지 않으므로 임시 디스크 문제 없음

## 실행 방법

```powershell
cd ml-pipeline
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
# 프로젝트 루트 .env 에 DATA_GO_KR_API_KEY(일반 인증키 Decoding), CRAWL_MONTHS 추가 (루트 .env.example 참고)
.\.venv\Scripts\python.exe run_all.py
```

- 수집 범위: 서울 25개 구 × 최근 `CRAWL_MONTHS`(기본 6)개월 = 150회 호출 (개발계정 일 10,000회 이내)
- 이미 받은 `data/raw/*.json` 은 건너뛰므로 재실행 시 호출량이 늘지 않음
- 실행 후 `src/main/resources/static/data/apartments.json` 을 커밋·푸시하면 Render 자동 배포

## 단계별 처리 내용

### 1) 크롤링 (`01_crawl.py`)
- `LAWD_CD`(법정동코드 5자리) × `DEAL_YMD`(YYYYMM) 로 호출, `numOfRows=1000` 페이지 반복
- XML 응답을 그대로 JSON 으로 저장 (전처리 수정 시 재크롤링 불필요)
- 이번 달은 실거래 신고 지연(최대 30일) 때문에 제외

### 2) 전처리 (`02_preprocess.py`)
- 해제(취소) 거래 제외, `dealAmount` 쉼표 제거 → 숫자(만원), 거래일 생성
- 파생변수: `rooms`(전용면적으로 방 수 추정), `pyeong`, `pricePerPyeong`, `age`, `sido/sgg`
- 시군구별 평당가 상·하위 1% 이상치 제거, 중복 제거

### 3) EDA (`03_eda.py`)
- 시군구별 평당가 박스플롯, 변수 상관관계 히트맵 (PNG)
- 전용면적 vs 거래가 인터랙티브 산점도 (Plotly HTML)

### 4) 머신러닝 (`04_train.py`)
| 모델 | 용도 | 결과 필드 |
|---|---|---|
| `RandomForestRegressor` | 면적·층·연식·시군구로 적정 거래가 예측 (log 변환, 8:2 검증 R²/MAE 출력) | `predictedPrice`, `valueScore`(= 예측가/실제가, 1보다 크면 저평가) |
| `KMeans` | 가격·면적·연식 기준 유사 단지 군집 | `cluster` |
| `StandardScaler` | 브라우저 유사도 계산용 기준값 | `scaler.mean`, `scaler.scale` |

- 거래 단위 → **단지·방 수 단위로 집계**(중위 거래가, 최근 거래가·거래일, 거래 건수)해서 JSON 크기 최소화

## 브라우저 추천 로직 (`ai-recommend.js`)

1. `/data/apartments.json` 을 첫 요청 때 한 번만 받아 재사용
2. 필터: 지역(‘서울’, ‘강남구’, ‘서울 마포구’, ‘역삼동’ 모두 가능) + 예산 이하 + 방 수(4방은 4방 이상)
3. 점수 = 조건 유사도 50% (예산·면적 거리, StandardScaler 기준) + 저평가 35% (`valueScore`) + 최근 거래 15%
4. 상위 6개 카드 표시: 단지명, 위치, 거래가, 방 수(추정)·면적·준공·최근 거래일, AI 적정가·매칭 점수, `AI 저평가` 배지
5. 결과 없음: 지역 데이터 없음 / 예산 부족(해당 지역 최저 거래가 안내) / JSON 미생성 안내

## 주의 사항

- **방 수 정보 없음**: 실거래가 자료에는 방 수가 없어 전용면적으로 추정 (≤40㎡ 1방, ≤60㎡ 2방, ≤85㎡ 3방, 초과 4방+). 화면에 "(추정)" 표시
- **매물이 아닌 실거래 기록**: 현재 매물이 아니라 최근 거래된 단지 기준 추천
- **예산 기본값 1000(만원)**: 서울 아파트 매매가보다 매우 낮아 결과가 없을 수 있음 (이때 최저 거래가를 안내)
- **API 키**: 프로젝트 루트 `.env` 하나로 통합 관리 (Spring 과 공용, `.gitignore` 대상). GitHub Actions 에서는 저장소 Secrets 로 주입. 결과 JSON 에는 키가 포함되지 않음
- 검증: 실제 키가 없어 API 형식과 동일한 가짜 데이터로 2~4단계와 브라우저 추천 로직을 검증했으며, 가짜 결과물은 삭제함
