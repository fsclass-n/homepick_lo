# 메인(index.html) 가로 스크롤바 생김 · 푸터 아래로 더 스크롤되는 문제 수정

- 작업일: 2026-09-28
- 대상 파일: `src/main/resources/static/css/common.css`

## 증상

1. 메인 페이지에 **가로 스크롤바**가 생김
2. 푸터 끝에서 **아래로 빈 공간이 더 스크롤**됨
3. 마우스를 움직이면 스크롤 가능한 범위가 바뀜

## 원인

`cursor.js` 가 푸터용 커스텀 커서 요소 2개를 `<body>` 맨 끝(푸터 아래)에 추가하는데, **이 요소들의 CSS가 삭제된 상태**였음.

```js
// cursor.js
document.body.appendChild(cursor);    // <div class="custom-cursor">
document.body.appendChild(follower);  // <div class="custom-cursor-follower">

// 마우스 위치만큼 이동
cursor.style.transform = `translate3d(${mouseX}px, ${mouseY}px, 0)`;
```

- 원래 `common.css` 에 `.custom-cursor`, `.custom-cursor-follower` 스타일(`position: fixed`, 6px / 32px 크기, 기본 숨김)이 있었음
- 커밋 `57f2b1d` 에서 `common.css` 를 정리하면서 **커서 CSS 블록 전체가 삭제**됨
- CSS가 없으니 두 div는 일반 블록 요소가 됨 → **화면 폭 100% 너비로 푸터 아래 문서 흐름에 배치**
- 여기에 JS가 `translate3d(mouseX, mouseY)` 를 계속 적용 → 요소가 오른쪽·아래로 밀려나며 문서 스크롤 영역을 넓힘
  - `mouseX` 만큼 오른쪽으로 → **가로 스크롤바**
  - `mouseY` 만큼 아래로 → **푸터 아래 추가 스크롤**

## 수정 내용

`common.css` 에 첫 커밋(`377c69d`) 당시의 커스텀 커서 CSS를 그대로 복원.

| 선택자 | 역할 |
|---|---|
| `.custom-cursor` | 6px 점. `position: fixed`, `pointer-events: none`, 기본 숨김 |
| `.custom-cursor-follower` | 32px 원(트레일). `position: fixed`, 기본 숨김 |
| `.visible` | 푸터에 마우스가 들어오면 표시 |
| `.active` | 푸터 링크/버튼 호버 시 확대 효과 |
| `@media (pointer: fine)` | 데스크탑에서 푸터 영역만 기본 커서 숨김 |
| `@media (max-width: 991.98px)` | 모바일/태블릿에서 커서 효과 숨김 |

`position: fixed` 로 화면 기준 배치가 되어 문서 흐름·스크롤 영역에 더 이상 영향을 주지 않음.

## 확인 방법

1. `/` 접속 후 **Ctrl+Shift+R** (강력 새로고침)
2. 가로 스크롤바가 없고, 푸터 끝에서 더 스크롤되지 않는지 확인
3. 푸터 위로 마우스를 올리면 주황색 커스텀 커서가 표시되는지 확인

## 참고

- 커서 효과를 쓰지 않을 거라면 CSS 복원 대신 `index.html` 의 `<script th:src="@{/js/cursor.js}"></script>` 를 삭제해도 됨
- 커서 색상은 `var(--color-accent)` (`#FF600D`) 를 사용
