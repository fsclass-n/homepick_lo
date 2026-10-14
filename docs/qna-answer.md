# Q&A 게시판 관리자 답변

## 권한: 관리자(role = ADMIN)만

| 항목 | 내용 |
|---|---|
| 관리자 계정 | 기존 `hong`(홍길동) → 아이디를 **`hpadmin`** 으로 바꾸고 `role = 'ADMIN'` 지정 (비밀번호 그대로) |
| 권한 확인 | 로그인할 때 `member.role` 을 읽어 세션에 저장 (`MemberSessionDto.role`, `isAdmin()`) — 일반 · 소셜 로그인 모두 |
| 답변 수 | 글마다 1개 (다시 저장하면 수정) |

```sql
-- TiDB 반영 완료 (2026-10-14)
UPDATE member SET user_id = 'hpadmin', role = 'ADMIN' WHERE user_id = 'hong';   -- 1건
ALTER TABLE qna ADD COLUMN answer TEXT NULL;               -- 관리자 답변
ALTER TABLE qna ADD COLUMN answer_writer VARCHAR(50) NULL; -- 답변한 관리자 이름
ALTER TABLE qna ADD COLUMN answered_at DATETIME NULL;      -- 답변 일시
```

기존 글 24건은 그대로 유지되고 새 칸은 비어 있습니다.
관리자를 추가하려면 `UPDATE member SET role = 'ADMIN' WHERE user_id = '...';` 후 **다시 로그인**하면 됩니다 (권한은 로그인할 때 읽음).

## 화면 (상세 보기)

| 보는 사람 | 화면 |
|---|---|
| 누구나 | 답변이 있으면 파란 상자 **「홈픽 답변 · 관리자 이름 · 일시」** + 답변 내용(줄바꿈 유지), 없으면 「답변을 준비하고 있어요」 |
| 관리자 | 그 아래 점선 상자 **관리자 답변 작성**(답변이 있으면 「수정」, 기존 답변이 채워짐), 글자 수, **답변 등록 / 답변 수정** 버튼 |

저장하면 같은 상세 화면으로 돌아와 「답변이 등록되었습니다.」 안내, 상태 배지가 **답변완료**(초록)로 바뀌고 목록에도 반영됩니다.

## 처리 (`POST /qna/{번호}/answer`)

| 경우 | 동작 |
|---|---|
| 관리자 | 저장(답변 · 관리자 이름 · 현재 시각, 상태 `답변완료`) → 같은 상세(보던 목록 페이지 · 검색 조건 유지) |
| 일반 회원 | 저장 안 함 + 「답변은 관리자만 등록할 수 있습니다.」 (입력란도 안 보임) |
| 비로그인 | 로그인 화면 → 로그인 후 그 글로 복귀 |
| 빈 답변 · 2,000자 초과 | 저장 안 함, 입력 내용 유지 + 오류 안내 |
| 없는 글 | 목록 + 「삭제되었거나 존재하지 않는 글입니다.」 |

- 답변 내용은 `th:text` 로 출력(태그는 글자로만), 줄바꿈 `\r\n` → `\n` 정리, 버튼 연타 방지

## 파일

| 파일 | 변경 |
|---|---|
| `schema.sql` | 답변 칸 3개, ALTER · 관리자 지정 SQL 기록 |
| `dto/MemberSessionDto.java` | `role`, `isAdmin()` (기존 2개 인자 생성자는 USER) |
| `repository/JdbcMemberRepository.java` | `findRole(userId)` |
| `controller/LoginController.java`, `config/OAuth2LoginSuccessHandler.java` | 로그인 시 권한을 세션에 저장 |
| `dto/QnaResponse.java` | `answer`, `answerWriter`, `answeredAt` |
| `repository/JdbcQnaRepository.java` | 조회에 답변 칸 포함, `saveAnswer` |
| `service/QnaService.java` | `validateAnswer`, `answer` |
| `controller/QnaController.java` | `POST /qna/{id}/answer`, 상세에 `isAdmin` |
| `templates/qna/detail.html`, `static/css/qna.css` | 답변 표시 상자, 관리자 입력란, 등록 안내 |
| `src/test/.../QnaAnswerTests.java` (신규) | 답변 테스트 8개 |

## 검증 (`./gradlew test` 30개 통과)

| 테스트 | 확인 내용 |
|---|---|
| 관리자 답변 | 상태 `답변완료`, 답변 · 관리자 이름 · 일시 저장, 공백 · 줄바꿈 정리, 상세에 표시, 비로그인에게 입력란 없음 |
| 답변 수정 | 입력란에 기존 답변 + 「답변 수정」, 다시 저장하면 바뀜 |
| 일반 회원 | 입력란 없음, 저장 안 됨, 상태 `대기중` 유지 |
| 비로그인 · 빈 답변 · 2,001자 · 없는 글 | 로그인 이동 / 오류 + 입력 유지 / 목록 이동 |
| 태그 | `<script>` 가 글자로 출력 |
| 권한 조회 | ADMIN 회원은 ADMIN, 없는 아이디는 USER |

- 테스트는 롤백되어 실제 DB 그대로 (qna 24건 · 답변 0건, 회원 4명 · 관리자 hpadmin 1명 확인)
- 관리자 화면(답변 표시 + 수정 입력란) 캡처 확인
