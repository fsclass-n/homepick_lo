# AI 맞춤 매물 추천: 'AI 시장 인사이트' (머신러닝 결과 시각화) 추가

- 작업일: 2026-10-07
- 대상 파일
  - `ml-pipeline/04_train.py` (분석 요약 `insights` 생성)
  - `src/main/resources/templates/sub/ai-recommend.html` (인사이트 섹션)
  - `src/main/resources/static/js/ai-insights.js` (신규, 시각화)
  - `src/main/resources/static/js/ai-recommend.js` (데이터·검색 이벤트 전달)
  - `src/main/resources/static/css/ai-recommend.css` (인사이트 스타일)
  - `src/main/resources/static/data/apartments.json` (insights 포함 최신 학습 결과)

## 고객에게 보여줄 수 있는 내용 (검토 결과)

| 콘텐츠 | 머신러닝/분석 출처 | 고객에게 주는 도움 |
|---|---|---|
| 핵심 지표 4개 | 전체 집계 + 모델 성능 | 분석 규모·서울 시세·저평가 비율·AI 신뢰도를 한눈에 |
| 월별 거래량·중위가 | pandas 월별 집계 | 시장이 오르는지/거래가 활발한지 파악 |
| AI가 본 가격 결정 요인 | RandomForest `feature_importances_` | 집을 고를 때 무엇이 가격을 좌우하는지 이해 |
| 구별 평당가 순위 | 구별 집계 + RandomForest 저평가 비율 | 예산에 맞는 지역 비교, 내 지역 순위 |
| AI가 분류한 단지 유형 | KMeans 군집 + 규칙 기반 이름·설명 | 나에게 맞는 단지 유형(신축/재건축 기대/실속 등) 탐색 |
| AI 저평가 단지 TOP 5 | RandomForest 적정가 대비 할인율 | 적정가보다 싸게 거래된 단지 발견 |

## 1. ml-pipeline (`04_train.py`)

JSON 에 `insights` 추가 (거래 단위 데이터·모델 내부값은 브라우저에서 계산할 수 없으므로 학습 단계에서 미리 요약)

```json
"insights": {
  "summary":    {"complexCount", "medianPrice", "medianPricePerPyeong", "valueRatio"},
  "monthly":    [{"month", "count", "medianPrice", "medianPricePerPyeong"}],
  "bySgg":      [{"sgg", "count", "medianPrice", "medianPricePerPyeong", "valueRatio"}],
  "importance": [{"feature": "전용면적|지역(구)|건물 연식|층", "weight"}],
  "clusters":   [{"id", "label", "desc", "count", "medianPrice", "medianArea", "medianAge", "topSgg"}]
}
```

- 가격 결정 요인: 지역 One-Hot 25개 컬럼의 중요도는 `지역(구)` 하나로 합산
- 단지 유형 이름: 군집 중앙값으로 `연식(신축/준신축/구축/노후)·면적(소형/중소형/중형/대형)·가격(실속/중간가/고가)` 조합
  - 설명 예: 노후+고가 → "재건축 기대감이 반영된 유형", 실속 → "첫 내 집 마련·1~2인 가구에 적합"

## 2. 화면 (`ai-insights.js`, Vanilla JS + SVG/Flexbox, 차트 라이브러리 없음)

- 학습 데이터를 받으면(`ai:data` 이벤트) 전체를 그림
- **'AI 추천 받기'를 누르면(`ai:search` 이벤트) 내 조건으로 다시 강조**
  - 구별 순위: 검색한 구를 주황색으로 강조 + "선택하신 마포구는 25개 구 중 평당가 7위"
  - 단지 유형: 내 예산 이하 유형에 `내 예산 OK` 배지
  - 저평가 TOP 5: 검색 지역으로 필터 (없으면 서울 전체)
- 각 차트 아래 **💡 한 줄 해석 문장**을 데이터로 자동 생성 (조사 '과/와·을/를·은/는' 자동 선택)

## 3. 해석의 정확성을 위한 처리

- **신고 지연 반영**: 실거래 신고 기한(30일) 때문에 최근 2개월은 거래량이 적게 잡힘
  → 막대를 점선(신고 집계 중)으로 표시, 가격 추세는 신고가 끝난 달까지만 비교
- **특수 거래 제외**: 적정가 대비 40% 넘게 싼 거래(지분·증여성 거래 가능성)는 저평가 TOP 에서 제외
- 하단 안내: "실거래 신고 자료를 AI가 분석한 참고 정보이며, 실제 매물 가격·투자 판단과 다를 수 있습니다."

## 실제 결과 (2026-10-07 14:58 학습, 실거래 33,349건 · 단지 7,138개)

- 서울 중위 거래가 9억 4,500만 / 평당 4,378만, AI 저평가 단지 비율 39%, R² 0.912 (평균 오차 ±1.4억)
- 가격 결정 요인: 전용면적 46% > 지역(구) 38% > 건물 연식 14% > 층 2%
- 평당가 1위 강남구(1억 2,245만) ~ 25위 도봉구(2,568만), 약 4.8배 차이
- 단지 유형 8개: 구축·대형·고가(중위 40억) ~ 신축·소형·실속(중위 4억 5,212만)

## 검증

- 로컬에서 실제 API로 크롤링(150/150 성공) → 전처리 → 학습 후 insights 값 확인
- 18080 포트 서버 + 헤드리스 Chrome 으로 데스크톱(1280px)·모바일(520px) 화면 확인
- 검색(서울 마포구·8억·2방) 시 구 강조 1개, 예산 OK 유형 3개, 마포구 저평가 TOP 5 표시 확인
- JS 문법 검사 통과
