# DB 전환 후에도 `bad SQL grammar [... user_id = ?]` 가 계속 발생하는 문제

- 작업일: 2026-09-28
- 대상 파일
  - `src/main/java/com/onrender/homepick/controller/MemberApiController.java`
  - `src/main/java/com/onrender/homepick/repository/JdbcMemberRepository.java`

## 증상

TiDB에서 테이블 전환 SQL을 실행한 뒤에도 서버 로그에 같은 오류가 반복됨.

```
>> 회원가입 DB 오류: PreparedStatementCallback; bad SQL grammar [SELECT COUNT(*) FROM member WHERE user_id = ?]
```

## 원인

이 오류는 **앱이 연결된 DB의 `member` 테이블에 `user_id` 컬럼이 없다**는 뜻이다.
SQL을 실행한 DB와 앱이 연결된 DB가 서로 다를 가능성이 가장 높다.

- `application.properties` 의 접속 URL은 `jdbc:mysql://${TIDB_HOST}:${TIDB_PORT}/${TIDB_DATABASE}` 이다.
  즉 앱은 `homepick_db` 가 아니라 **`.env` 의 `TIDB_DATABASE` 값**에 연결한다.
- 전환 SQL은 `USE homepick_db;` 로 실행했으므로, `TIDB_DATABASE` 가 다른 값(예: `test`)이면 앱은 여전히 예전 테이블을 본다.
- 그 밖의 가능성
  - 전환 SQL이 일부만 실행됨 (`RENAME` 만 되고 `CREATE` 는 실패 등)
  - 다른 TiDB 클러스터/계정에서 실행함 (`TIDB_HOST`, `TIDB_USERNAME` 이 다름)

## 수정 내용 (진단 로그 추가)

원인을 정확히 확인할 수 있도록 오류가 날 때 로그를 더 자세히 남기게 했다.

1. `MemberApiController`: 오류 메시지를 요약본(`getMessage()`) 대신 **DB가 보낸 원문**(`getMostSpecificCause().getMessage()`)으로 출력
   - 예: `Unknown column 'user_id' in 'where clause'` → 컬럼 없음
   - 예: `Table 'xxx.member' doesn't exist` → 테이블 없음
2. `JdbcMemberRepository.describeMemberTable()` 추가: 오류가 나면 **현재 연결된 DB 이름**과 **`member` 테이블 컬럼 목록**을 함께 출력

```
>> 회원가입 DB 오류: Unknown column 'user_id' in 'where clause'
>> 연결 정보: DB=test, member 컬럼=[email, password, name, created_at]
```

사용자 화면에는 기존처럼 안내 문구만 표시된다.

## 확인 및 해결 방법

1. 서버를 재시작하고 가입을 한 번 더 시도한 뒤, 로그의 `>> 연결 정보: DB=...` 값을 확인한다.
2. 해당 DB에서 전환 SQL을 다시 실행한다 (`docs/member-table-migration.md` 4번 항목).

```sql
USE <로그에 나온 DB 이름>;
DESC member;
RENAME TABLE member TO member_legacy;
-- 이어서 신버전 CREATE TABLE 실행
```

3. `DESC member;` 결과에 `user_id`, `password_hash`, `birth`, `gender`, `phone`, `firebase_uid` 컬럼이 있는지 확인한다.
4. 서버를 재시작하고 다시 테스트한다.

## 참고

- `describeMemberTable()` 은 진단용이다. 문제가 해결되면 이 메서드와 컨트롤러의 `>> 연결 정보` 출력을 지워도 된다.
