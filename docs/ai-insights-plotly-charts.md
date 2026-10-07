# AI 시장 인사이트 그래프를 Python Plotly 로 전환

- 작업일: 2026-10-07
- 대상 파일
  - `ml-pipeline/04_train.py` (Plotly 그래프 생성 → `insights.charts`)
  - `src/main/resources/static/js/ai-insights.js` (plotly.js 로 표시)
  - `src/main/resources/static/js/ai-recommend.js` (탭 전환 시 그래프 크기 재조정 이벤트)
  - `src/main/resources/templates/sub/ai-recommend.html` (plotly.js 로드, 그래프 영역)
  - `src/main/resources/static/css/ai-recommend.css`
  - `src/main/resources/static/data/apartments.json` (그래프 JSON 포함)

## 배경

이전에는 그래프 수치는 Python(pandas·scikit-learn)이 계산했지만, **그림은 브라우저 JavaScript(SVG)** 로 그렸음.
Matplotlib·Seaborn·Plotly 는 `03_eda.py` 의 분석용 파일에만 쓰이고 화면에는 나오지 않았음.
→ 기술 스택(Plotly)에 맞게 **그래프 자체를 Python Plotly 로 생성**하도록 변경.

## 동작 방식

```
04_train.py (Python Plotly 7.1.0)
  go.Figure 로 그래프 생성 → plotly.io.to_json → apartments.json 의 insights.charts 에 저장
        ↓  (GitHub Actions 재학습 때마다 함께 갱신)
브라우저 (plotly.js 4.1.1 기본 번들, jsDelivr CDN)
  Plotly.react(div, figure.data, figure.layout) 로 Python 이 만든 그래프를 그대로 표시
```

| 그래프 | Python Plotly 구성 | 범위 |
|---|---|---|
| 02 월별 거래량 · 중위 거래가 | 막대(거래량) + 선(중위가, 보조축 '억'), 최근 2개월은 연한 막대(신고 집계 중) | 수도권·서울·인천·경기 각각 |
| 03 AI가 본 가격 결정 요인 | 가로 막대, 1위만 진한 파랑 | 모델 공통 1개 |
| 04 시·군·구별 평당 거래가 순위 | 가로 막대(1위가 위), 마우스 오버 시 중위가·거래 건수·저평가 거래 비율 | 수도권(상위 20)·서울·인천·경기 |

- 색·글꼴은 사이트 디자인(메인 블루 `#006AC9`, 포인트 주황 `#FF600D`, NanumSquare)에 맞춰 Python 에서 지정
- 그래프 JSON 크기: 약 81KB (데이터 파일 4.9MB 중)
- plotly.js: Python Plotly 7.1.0 이 사용하는 버전(4.1.1)과 동일, 막대·선만 쓰므로 **기본 번들**(약 1.2MB, 2026-09-14 배포) 사용

## 화면 상호작용 유지

- **시·도 탭 전환**: 범위별로 미리 만든 그래프로 교체
- **내 지역 강조**: 검색한 시·군·구 막대만 주황색으로 바꿔 표시 (Python 그래프 복사 후 색 배열만 변경)
- **마우스 오버**: 막대·점에 정확한 수치 표시 (Plotly 기본 기능)
- 해석 문장(💡)은 기존처럼 수치로 자동 생성

## 크기·반응형 처리

- 그래프 컨테이너 높이를 그래프 높이에 맞춰 고정 → 화면 폭이 바뀌어도 아래 내용과 겹치지 않음
- `ResizeObserver` 로 컨테이너 폭 변화(그리드 배치, 탭 전환, 창 크기 변경) 시 `Plotly.Plots.resize`
- 숨겨진 탭에서 그려진 경우를 위해 '시장 인사이트' 탭이 보일 때 크기 재조정
- 도구 막대(modebar) 숨김, plotly.js 를 못 불러오면 안내 문구 표시

## 이번 데이터 반영

- 재크롤링 없이, 현재 `apartments.json` 의 insights 로 `build_charts()` 를 실행해 그래프 JSON 만 추가
- 다음 'AI 재학습'부터는 `04_train.py` 가 학습과 함께 그래프도 다시 생성

## 검증

- 18080 포트 서버 + 헤드리스 Chrome(DevTools Protocol)
  - 3개 그래프 모두 Plotly SVG 렌더링 확인, 콘솔 오류 없음
  - 분당구 검색 → 경기 탭, 성남시 분당구 막대 1개 주황 강조
  - 데스크톱 1280px / 모바일 430px 에서 잘림·겹침 없음
- JS 문법 검사 통과
