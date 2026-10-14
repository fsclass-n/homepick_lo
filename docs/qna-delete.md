# Q&A 게시판 글 삭제

## 권한: 본인 글만

기존 `qna` 테이블에는 작성자 **이름**만 있어서, 동명이인이 남의 글을 지울 수 있는 문제가 있었습니다.
작성 회원 **아이디** 컬럼을 추가해 본인 확인을 정확하게 했습니다.

```sql
-- TiDB 반영 완료 (2026-10-14): 기존 글 24건 그대로 유지, 새 컬럼은 NULL
ALTER TABLE qna ADD COLUMN writer_id VARCHAR(50) NULL AFTER writer;
```

| 글 | 삭제 |
|---|---|
| 새로 쓴 글 (writer_id 저장) | 작성한 회원만 가능 |
| 예전 글 24건 (writer_id 없음) | 누구도 불가 (삭제 버튼도 안 보임) |

## 화면

- 상세 보기 하단: 본인 글에만 빨간 테두리 **삭제** 버튼 (목록 · 글쓰기 버튼 옆)
- 삭제를 누르면 확인 창: 「이 글을 삭제할까요? '제목' 삭제한 글은 되돌릴 수 없습니다.」 → 취소 / **삭제하기**
- 삭제 후 **보던 목록 페이지 · 검색 결과**로 돌아가 「글이 삭제되었습니다.」 안내

## 처리 (`POST /qna/{번호}/delete`)

| 경우 | 동작 |
|---|---|
| 본인 글 | 삭제 → 목록(같은 페이지 · 검색 조건) + 완료 안내 |
| 다른 회원 글 · 예전 글 | 삭제 안 함 → 상세 화면 + 「본인이 작성한 글만 삭제할 수 있습니다.」 |
| 비로그인 | 삭제 안 함 → 로그인 화면, 로그인 후 그 글로 복귀 |
| 이미 삭제된 글 | 목록 + 「이미 삭제되었거나 존재하지 않는 글입니다.」 |
| GET 으로 접근 | 삭제되지 않음 (주소 입력 · 링크 클릭으로 지워지지 않게 POST 만 허용) |

- 권한 확인은 화면(버튼 표시)과 서버에서 두 번: `DELETE FROM qna WHERE id = ? AND writer_id = ?` — 번호와 아이디가 모두 맞을 때만 지워져, 요청을 조작해도 남의 글은 삭제되지 않음
- 삭제하기 버튼 연타 방지 (클릭 후 「삭제 중...」)
- 삭제는 실제로 행을 지움(되돌릴 수 없음)

## 파일

| 파일 | 변경 |
|---|---|
| `schema.sql` | `writer_id` 컬럼 + 기존 테이블용 ALTER 문(기록) |
| `dto/QnaResponse.java` | `writerId` |
| `repository/JdbcQnaRepository.java` | 조회에 `writer_id` 포함, `insert(..., writerId)`, `deleteByIdAndWriterId` |
| `service/QnaService.java` | `isOwner`, `delete`, `write(..., writerId)` |
| `controller/QnaController.java` | `POST /qna/{id}/delete`, 상세에 `canDelete` · 목록 복귀 값, 글쓰기 때 아이디 저장 |
| `templates/qna/detail.html` | 삭제 버튼 · 확인 창 · 안내 문구 |
| `static/css/qna.css` | 삭제 버튼 · 확인 창 스타일 |
| `src/test/.../QnaDeleteTests.java` (신규) | 삭제 테스트 7개, `QnaWriteTests` 에 writer_id 저장 확인 추가 |

## 검증 (`./gradlew test` 22개 통과)

| 테스트 | 확인 내용 |
|---|---|
| 삭제 버튼 표시 | 본인에게만, 다른 회원 · 비로그인에게는 없음 |
| 본인 삭제 | 삭제됨, `page=2&type=title&keyword=전세` 목록으로 이동 + 안내 |
| 다른 회원 | 삭제 안 됨 + 안내 |
| 예전 글(writer_id 없음) | 삭제 안 됨, 버튼 없음 |
| 비로그인 | 로그인으로, 로그인 후 그 글로 복귀 주소 저장, 삭제 안 됨 |
| 이미 삭제된 글 | 목록 + 안내 |
| GET 요청 | 삭제 안 됨 |

- 테스트는 롤백되어 실제 DB 는 그대로 (테스트 전후 24건 확인)
- 삭제 버튼 · 확인 창 화면 캡처 확인

## 참고

- 프로젝트 전체 설정(`SecurityConfig`)에서 CSRF 보호가 꺼져 있습니다. 최신 브라우저는 세션 쿠키를 다른 사이트의 POST 에 보내지 않아(SameSite=Lax 기본) 위험이 낮지만, 회원 기능이 늘어나면 CSRF 보호를 켜는 것을 권장합니다.
