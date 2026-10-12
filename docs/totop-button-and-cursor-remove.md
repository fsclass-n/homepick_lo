# 푸터 커서 효과 제거 + 맨 위로(ToTop) 버튼 추가

- 작업일: 2026-10-12
- 대상 파일
  - 삭제: `src/main/resources/static/js/cursor.js`
  - 수정: `templates/common/footer.html`, `templates/common/layout.html`, `templates/index.html`,
    `templates/qna/list.html`, `templates/sub/ai-recommend.html`,
    `static/css/common.css`, `static/css/footer.css`, `static/js/footer.js`

## 1) 마우스 따라다니는 커서 효과 제거

- `cursor.js` 파일 삭제: 푸터 영역에서 주황색 점 + 따라다니는 원을 그리던 커스텀 커서
- 4개 템플릿의 `<script src=".../js/cursor.js">` 제거
- `common.css`의 커서 스타일 블록 제거 (`.custom-cursor`, `.custom-cursor-follower`, `cursor: none` 규칙)

## 2) ToTop 버튼

버튼은 이미 마크업이 있었지만 **`th:fragment="footerFragment"` 바깥**에 있어서
실제 페이지에는 렌더링되지 않던 상태였음 → fragment 안(`<footer>` 내부)으로 이동.

- 위치: 오른쪽 하단 고정 (`position: fixed; bottom/right: 2rem`, 모바일 1.25rem)
- 디자인: 48px 원형, 흰 배경 + 브랜드 블루 아이콘(`fa-chevron-up`), 연한 테두리·그림자
  - 호버 시 블루 배경으로 반전 + 살짝 위로 뜸
- 동작 (`footer.js`)
  - 300px 이상 스크롤 → `.show` 클래스로 부드럽게 나타남
  - 최상단 근처 → 사라짐 (opacity + translateY 전환)
  - 클릭 → `scrollTo({behavior:'smooth'})` 부드러운 상단 이동
  - 스크롤된 상태로 새로고침해도 로드 직후 상태가 맞게 표시되도록 초기 체크 추가
- CSS 정의되지 않은 변수(`--color-text-light`, `--color-bg-dark`) 사용 부분을
  common.css 토큰(`--color-pure-white`, `--color-primary`, `--color-border`)으로 교체

## 검증

- 로컬 서버 `/`, `/ai/recommend`, `/qna` HTML에 버튼 렌더링 확인, `cursor.js`/`custom-cursor` 참조 없음 확인
- headless Chrome 초기 렌더에서 `show` 클래스 없이 숨김 상태 확인
