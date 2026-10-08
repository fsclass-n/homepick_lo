# 파비콘 추가

- 작업일: 2026-10-08
- 추가 파일 (`src/main/resources/static/`)
  - `favicon.svg` — 원본 벡터 (최신 브라우저, 고해상도 화면에서 선명)
  - `favicon.ico` — 16·32·48px 묶음 (구형 브라우저, `/favicon.ico` 자동 요청 대응)
  - `apple-touch-icon.png` — 180px (iOS 홈 화면 추가 시, 모서리는 iOS 가 자동으로 둥글게 처리)
- 적용 템플릿: `index.html`, `common/layout.html`, `common/footer.html`, `sub/ai-recommend.html`,
  `member/login.html`, `member/register.html`, `member/admin.html`, `qna/list.html`

## 디자인

| 요소 | 의미 |
|---|---|
| 배경 `#006AC9` | 홈픽 메인 블루 (헤더·버튼과 같은 색) |
| 흰색 집 + 굴뚝 | 푸터 로고의 house-chimney 아이콘 모티프 = Home |
| 오렌지 체크 `#FF600D` | 포인트 오렌지 = Pick (골라 주는 서비스) |

## 수정 전 문제

- `index.html` 등 3곳이 `../../static/assets/favicon.png` 를 가리켰지만 **파일이 없고**(운영 404),
  `th:href` 도 없어 실제 서버 경로로 바뀌지 않았음
- 기존 `assets/logo.png` 는 보라색 하트 집 아이콘으로 사이트 색과 맞지 않아 사용하지 않음

## 적용 코드

```html
<link rel="icon" href="/favicon.ico" th:href="@{/favicon.ico}" sizes="any">
<link rel="icon" type="image/svg+xml" href="/favicon.svg" th:href="@{/favicon.svg}">
<link rel="apple-touch-icon" href="/apple-touch-icon.png" th:href="@{/apple-touch-icon.png}">
```

## 검증

- 로컬 서버: `/favicon.ico`(image/x-icon), `/favicon.svg`(image/svg+xml), `/apple-touch-icon.png`(image/png) 모두 200
- `/`, `/ai/recommend`, `/member/login`, `/member/register` 응답 HTML 에 링크 3개 확인
- 32px 축소본에서도 집·체크 모양 식별 가능 확인
