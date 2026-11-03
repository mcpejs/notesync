# notesync

여러 기기에서 같은 Markdown 노트를 고쳐도 내용이 사라지지 않게 동기화하는 서버와 CLI 클라이언트입니다. 늦게 저장한 쪽의 수정은 서버가 충돌 사본으로 보존합니다.

**기술 스택:** Kotlin 2.3, Spring Boot 4.1, Spring Data JPA (Hibernate 7.4), MySQL 8.0, JWT (jjwt), JUnit 5, MockMvc

```
CLI (폴더 하나 = 기기 하나)  --HTTP + JWT-->  Spring Boot  -->  MySQL
  push: 바뀐 파일 저장                          문서, 불변 버전, 문단 청크
  pull: 새 버전 받기
```

## 핵심 설계

### 1. 동시 수정은 409와 충돌 사본으로 처리

- **문제:** 두 기기가 같은 버전을 기준으로 고치고 저장하면, 서버가 나중 저장을 그냥 받아들일 때 먼저 저장한 내용이 조용히 사라집니다 (lost update).
- **결정:** 클라이언트가 저장할 때 자신이 본 버전(`baseVersionId`)을 보냅니다. 서버의 현재 버전과 다르면 서버가 그 내용을 새 문서 `제목(충돌 사본)`으로 저장하고 409 `VERSION_CONFLICT`와 사본 ID를 돌려줍니다.
- **근거:** 거절, 자동 병합, 나중 저장 우선과 비교했습니다. 자연어는 의미 단위로 나눌 수 없어서, 고친 곳이 겹치지 않아도 합친 결과가 모순될 수 있습니다. 사본은 요청 한 번 안에 두 내용을 모두 남기고, 어느 쪽을 쓸지는 사람이 나중에 정합니다. 버전은 불변으로 쌓고 문서는 현재 버전만 가리킵니다.

### 2. 동시 저장 교착: 문서 행을 먼저 잠금

- **문제:** 처음엔 JPA `@Version`으로 동시 저장을 막는다고 설계했습니다. 같은 기준 버전으로 진짜 동시 저장을 40번 돌리자 39번이 `CannotAcquireLockException`(500), 1번이 `DataIntegrityViolationException`이었고 `@Version` 예외는 0번이었습니다. 진 쪽은 사본을 받지 못했습니다.
- **원인:** `SHOW ENGINE INNODB STATUS`와 `performance_schema.data_locks`로 교착을 확인했습니다. 버전 INSERT의 FK 검사가 문서 행에 S 잠금을 잡고, 이후 문서 갱신의 X 잠금과 UNIQUE 키 대기가 엇갈렸습니다. 터미널 두 개로 같은 순서를 실행해 `ERROR 1213`을 손으로 재현했습니다.
- **결정:** 트랜잭션의 첫 쿼리에서 `SELECT ... FOR UPDATE`로 문서 행을 잠가 같은 문서의 저장을 줄 세웁니다. 첫 쿼리여야 하는 이유는 일반 SELECT가 먼저 스냅샷을 고정하면 기다린 뒤에도 낡은 값을 읽기 때문입니다.
- **결과:** 동시 저장 40번과 같은 키 동시 재시도 40번을 테스트로 넣었고, 모두 통과했으며 진 쪽은 전부 사본을 받습니다.

### 3. 같은 사용자의 청크 경합: 사용자 행을 먼저 잠금

- **문제:** 청크는 사용자별로 중복을 없앱니다 (`UNIQUE (user_id, content_hash)`). 같은 사용자의 서로 다른 문서 두 개가 같은 새 문단을 동시에 저장하면, 문서 잠금으로는 줄이 서지 않아 UNIQUE 충돌이 납니다.
- **결정:** 모든 저장 경로가 사용자 행, 문서 행 순서로 잠급니다. 순서를 한 함수 안에 넣어 빠뜨리거나 뒤집을 수 없게 했습니다.
- **근거:** 사용자 행 잠금 한 줄을 지우고 두 문서 동시 저장 테스트(40번)를 돌리면 `Duplicate entry`로 실패하고, 되돌리면 통과합니다. 잠금 시간을 제한하려고 버전당 청크를 4,096개로 제한했습니다. 작은 문단만으로 1MB를 채우면 약 35만 개까지 나올 수 있기 때문입니다. 4,096개 저장은 로컬 테스트에서 약 1.4초였습니다.

### 4. 재시도용 멱등 키

- **문제:** 응답을 못 받은 클라이언트가 다시 보내면 버전이나 충돌 사본이 두 번 만들어질 수 있습니다.
- **결정:** 저장 요청마다 `Idempotency-Key`를 받아 `versions.client_request_id` UNIQUE로 같은 트랜잭션 안에서 기록합니다. 같은 키가 다시 오면 첫 결과를 돌려줍니다. CLI는 키를 `문서 ID + 기준 버전 + 내용 해시`로 계산해서, 재시도 때 키를 저장해 둘 필요가 없습니다.
- **근거:** 처음 생각한 메모리 캐시는 재시작, 동시 재시도, 커밋과 캐시 저장 사이의 장애에서 기록과 결과가 어긋납니다. 키에서 기준 버전을 빼면 내용을 되돌린 저장이 옛 저장의 재시도로 처리됩니다.

### 5. 내용은 문단 청크로 저장

- **결정:** 서버가 내용을 빈 줄 기준 문단으로 자르고 SHA-256으로 사용자별 중복을 제거해 DB `BLOB`에 저장합니다. 이어 붙이면 원본과 같습니다 (`\r\n` 포함).
- **근거:** 제 노트 64개로 재 보니 청크 중앙값 183바이트, 최대 15,841바이트, 64KB를 넘는 문단은 0개였습니다. 가상으로 중간에 내용을 넣었을 때 새로 생기는 청크는 문단 분할이 64개(전체 996개), 고정 4KB 분할이 105개(전체 130개)였습니다. 청크가 작아서 파일 대신 DB에 두었고, 덕분에 버전과 같은 트랜잭션으로 롤백됩니다.
- **경계값:** `BLOB` 상한은 65,535바이트입니다. 65,536바이트는 `ERROR 1406`으로 거절되는 것을 확인했고, 더 긴 문단은 65,535바이트씩 자릅니다. 요청 본문은 1MB로 제한했습니다 (제 가장 큰 노트는 115,630바이트).
- **한계:** 클라이언트가 전체 내용을 보내므로 줄어드는 것은 저장 공간뿐이고 전송량은 줄지 않습니다.

## 시연

한 PC에서 폴더 `a`, `b`를 두 기기로 썼습니다. 아래 출력은 실제 실행에서 그대로 옮겼습니다 (PowerShell, 파일은 메모장으로 편집).

```powershell
# 기기 A: 새 노트를 만들고 올림
cd notesync-demo\a
notepad 회의록.md          # "아무내용이나" 입력
notesync push

# 기기 B: 받음
cd ..\b
notesync pull
Get-Content 회의록.md -Encoding UTF8
```
```
아무내용이나
```

```powershell
# B가 먼저 고쳐서 올림
notepad 회의록.md          # "아무내용이나 b에서편집"
notesync push

# A는 B의 수정을 받기 전에 다르게 고침
cd ..\a
notepad 회의록.md          # "아무내용이나a에서도 수정 받기전에"
notesync pull
```
```
건너뜀: 회의록.md 은 로컬과 서버가 모두 바뀌었습니다. push 하면 내 수정은 충돌 사본으로 보존됩니다
```

```powershell
notesync push
```
```
충돌: 회의록.md 은 최신으로 바뀌었고, 내 수정은 회의록(충돌 사본).md 에 있습니다
```

```powershell
Get-Content 회의록.md -Encoding UTF8
Get-Content "회의록(충돌 사본).md" -Encoding UTF8
```
```
아무내용이나 b에서편집
아무내용이나a에서도 수정 받기전에
```

```powershell
# B도 사본을 받음
cd ..\b
notesync pull
Get-ChildItem
```
```
Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----        2026-11-02   오후 4:35                .notesync
-a----        2026-11-02   오후 4:35             48 회의록(충돌 사본).md
-a----        2026-11-02   오후 4:34             32 회의록.md
```

## 실행 방법

JDK 21과 MySQL 8.0이 필요합니다. 시연용 `demo` 프로필은 전용 DB `notesync_demo`와 포트 18080을 씁니다.

**1. DB 준비 (MySQL root로 한 번)**

```sql
CREATE DATABASE notesync_demo;
CREATE USER IF NOT EXISTS 'notesync_app'@'localhost' IDENTIFIED BY '<DB 비밀번호>';
GRANT ALL PRIVILEGES ON notesync_demo.* TO 'notesync_app'@'localhost';
```

```bash
mysql -u notesync_app -p notesync_demo < docs/schema.sql
```

**2. 서버 실행.** DB 비밀번호와 JWT 키(32바이트 이상)는 환경 변수로만 넘깁니다.

```bash
export DB_PASSWORD='<DB 비밀번호>'
export JWT_SECRET='<32바이트 이상 임의 문자열>'
./gradlew bootRun --args='--spring.profiles.active=demo'
```

**3. 사용자 만들기와 토큰 받기**

```bash
curl -s -X POST localhost:18080/auth/signup -H 'Content-Type: application/json' \
  -d '{"name":"demo","email":"demo@example.com","password":"<비밀번호>"}'
export NOTESYNC_TOKEN=$(curl -s -X POST localhost:18080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"<비밀번호>"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
export NOTESYNC_URL=http://localhost:18080
```

토큰은 1시간 뒤 만료됩니다. PowerShell에서는 `$env:NOTESYNC_TOKEN = (Invoke-RestMethod ...).accessToken` 형태로 넣습니다.

**4. CLI 설치와 실행**

```bash
./gradlew :cli:installDist
export PATH="$PWD/cli/build/install/notesync/bin:$PATH"
mkdir -p ~/notesync-demo/a ~/notesync-demo/b
cd ~/notesync-demo/a && notesync push
```

동기화할 폴더 안에서 `notesync push`, `notesync pull`을 실행합니다. 폴더마다 상태가 `.notesync/state.json`에 저장되어 폴더 하나가 기기 하나처럼 동작합니다.

**테스트:** `./gradlew test`는 테스트 DB `notesync_test`를 씁니다 (서버 55개, CLI 54개).

## API

`/auth/**`를 뺀 모든 요청은 `Authorization: Bearer <토큰>`이 필요합니다. 남의 문서는 없는 문서와 같은 404를 돌려줍니다.

| 메서드 | 경로 | 성공 | 실패 |
|---|---|---|---|
| POST | `/auth/signup` | 201 | 400 입력 오류, 409 이메일 중복 |
| POST | `/auth/login` | 200 | 400 입력 오류, 401 이메일 또는 비밀번호 불일치 |
| GET | `/users/me` | 200 | 401 |
| POST | `/documents` | 201 | 400 제목 100자 초과, 401 |
| GET | `/documents` | 200 | 401 |
| GET | `/documents/{id}` | 200 | 401, 404 |
| POST | `/documents/{id}/versions` | 201 | 400 키 없음 또는 형식 오류, 401, 404, 409 `VERSION_CONFLICT` (충돌 사본 생성), 411 길이 없음, 413 `BODY_TOO_LARGE` 또는 `TOO_MANY_CHUNKS` |
| GET | `/documents/{id}/versions/{versionId}` | 200 | 401, 404 |

버전 저장은 `Idempotency-Key` 헤더(UUID)와 본문 `{"baseVersionId": <본 버전 또는 null>, "content": "..."}`를 받습니다. 409 본문에는 `currentVersionId`, `conflictCopyId`, `conflictCopyTitle`이 들어 있습니다.

## 범위와 제한

- 삭제는 동기화되지 않습니다. 지운 파일은 다음 pull에서 복구됩니다.
- 폴더 바로 아래의 UTF-8 `.md` 파일만 다룹니다. 하위 폴더와 이미지는 대상이 아닙니다.
- 파일 이름을 바꾸면 새 문서로 저장되고, 옛 이름은 다음 pull에서 복구됩니다.
- 1MB를 넘는 파일은 413으로 저장되지 않지만, 문서는 먼저 만들어져서 다른 기기에 빈 파일로 나타납니다.
- 시연은 한 PC의 폴더 두 개로 두 기기를 흉내 낸 것입니다. 모바일 클라이언트와 실시간 동기화는 없습니다.

하지 않은 것:

- **자동 병합:** 충돌하면 두 내용을 모두 보존하고 사람이 고릅니다.
- **전송량 절감:** 클라이언트가 자르고 없는 청크만 올리는 API가 필요합니다.
- **리프레시 토큰과 로그아웃:** 액세스 토큰(1시간)만 있습니다.
- **버전 복원, 문서 삭제, 목록 페이징**
- **Redis 같은 캐시나 분산 잠금:** 서버 한 대와 DB 행 잠금으로 처리합니다.
- **CI, Docker 배포**

## AI 사용

CLI 코드는 AI가 작성했고, 동작 기준과 상태표(pull 20가지, push 11가지 경우)는 제가 정했습니다.

서버는 설계 결정(테이블 구조, 충돌 처리, 잠금 순서, 청크 규칙, 상태 코드)을 모두 제가 내렸습니다. 직접 작성한 코드는 스키마 SQL, `User`와 `Document` 엔티티와 리포지토리, 주인 검사, 버전 저장과 충돌 사본(`saveVersion`, `createConflictCopy`), 문서 행과 사용자 행 잠금, 청크 저장(`saveVersionChunks`), 내용 조회, `JwtProvider`, 인증 인터셉터와 그 설정, 일부 테스트입니다. 로그인과 비밀번호 해시 코드, 저장 흐름을 나누는 facade, 크기 제한, 문단 분할 대부분, 버전과 청크 엔티티, 대부분의 테스트(동시성 테스트 포함)는 AI가 작성했고 제가 리뷰했습니다. 날짜별 기록은 개발 일지에 남겼습니다.
