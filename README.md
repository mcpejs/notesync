# notesync

여러 기기 사이에서 Markdown 노트를 동기화하는 Kotlin, Spring Boot 서버와 CLI 클라이언트입니다.

## CLI

```bash
./gradlew :cli:installDist
```

```bash
export NOTESYNC_URL=http://localhost:8080
export NOTESYNC_TOKEN=<로그인으로 받은 토큰>
```

동기화할 폴더에서 실행합니다. 폴더 하나가 기기 하나이고, 상태는 `.notesync/state.json`에 저장됩니다.

```bash
notesync push
notesync pull
```

- `push`: 바뀐 파일을 저장합니다. 다른 기기가 먼저 저장했다면 내 수정은 충돌 사본으로 보존되고, 원본 파일은 최신 내용으로 바뀝니다.
- `pull`: 서버의 새 내용을 받습니다. 로컬과 서버가 모두 바뀐 파일은 건너뜁니다.

### 제한

- 삭제는 동기화되지 않습니다. 지운 파일은 다음 pull에서 복구됩니다.
- 폴더 바로 아래의 UTF-8 `.md` 파일만 다룹니다. 하위 폴더와 이미지는 대상이 아닙니다.
- 파일 이름을 바꾸면 새 문서로 저장되고, 옛 이름은 다음 pull에서 복구됩니다.
- 병합은 하지 않습니다. 충돌하면 두 내용을 모두 보존합니다.
