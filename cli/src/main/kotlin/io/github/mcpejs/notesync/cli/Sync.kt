package io.github.mcpejs.notesync.cli

import java.nio.charset.CharacterCodingException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

class Sync(private val api: ApiClient, private val folder: Path) {
    private val state = loadState(folder)
    private var failed = false

    private fun markdownFiles(): List<Path> = Files.list(folder).use { files ->
        files.filter { Files.isRegularFile(it) && it.extension == "md" }.toList()
    }

    // 파일을 먼저 쓰고 상태를 기록한다. 순서를 바꾸면 낡은 파일이 수정본으로 보인다
    private fun writeLocal(fileName: String, docId: Long, versionId: Long?, content: String) {
        Files.writeString(folder.resolve(fileName), content)
        state[fileName] = FileState(docId, versionId, contentHash(content))
        saveState(folder, state)
    }

    private fun download(fileName: String, document: RemoteDocument) {
        val currentId = document.currentVersionId
        val content = if (currentId == null) "" else api.getContent(document.id, currentId)
        writeLocal(fileName, document.id, currentId, content)
    }

    private fun warn(message: String) {
        System.err.println("경고: $message")
        failed = true
    }

    // 한 항목의 실패가 남은 항목을 막지 않게 한다. 토큰 오류와 네트워크 오류는 호출한 쪽까지 올라가서 전체를 멈춘다
    private fun attempt(name: String, action: () -> Unit) {
        try {
            action()
        } catch (e: ApiException) {
            if (e.fatal) throw e
            warn("$name: ${e.message}")
        } catch (e: CharacterCodingException) {
            warn("$name: UTF-8 파일이 아닙니다")
        } catch (e: FileSystemException) {
            warn("$name: 파일을 읽거나 쓸 수 없습니다 (${e.message})")
        }
    }

    // 모두 성공하면 true
    fun push(): Boolean {
        for (file in markdownFiles()) {
            attempt(file.name) { pushFile(file.name, file.nameWithoutExtension, Files.readString(file)) }
        }
        return !failed
    }

    private fun pushFile(fileName: String, title: String, content: String) {
        val saved = state[fileName] ?: createEntry(fileName, title)
        val hash=contentHash(content)

        if(saved.hash==hash) return

        val key=idempotencyKey(saved.docId, saved.versionId, hash)
        val result=api.saveVersion(saved.docId, saved.versionId, content, key)

        when(result){
            is Saved -> {
                state[fileName] = FileState(saved.docId,result.versionId, hash)
                saveState(folder, state)
            }
            is Conflict -> {
                // 사본 파일로 받기. 재시도라서 이미 받은 사본이면 그 파일의 수정을 덮지 않는다
                val copyName = fileNameFor(result.copyTitle, result.copyId, state, folder)
                if(state[copyName] == null) download(copyName, api.getDocument(result.copyId))

                // 서버에 현재 버전이 없으면 상태와 서버가 어긋난 것이라 내 파일을 그대로 둔다
                val latestId = result.currentVersionId
                if(latestId == null) {
                    warn("$fileName: 서버에 현재 버전이 없습니다. 파일을 유지합니다. 내 수정은 $copyName 에도 있습니다")
                    return
                }

                // 원본 파일 최신으로 덮어쓰기
                writeLocal(fileName, saved.docId, latestId, api.getContent(saved.docId, latestId))
                println("충돌: $fileName 은 최신으로 바뀌었고, 내 수정은 $copyName 에 있습니다")
            }
        }
    }

    private fun createEntry(fileName: String, title: String): FileState {
        val remoteDocument=api.createDocument(title)
        val fileState= FileState(remoteDocument.id,null,"")
        state[fileName] = fileState
        saveState(folder, state)
        return fileState
    }

    // 모두 성공하면 true
    fun pull(): Boolean {
        val documents = api.listDocuments()
        for (document in documents) {
            attempt(document.title) { pullDocument(document) }
        }

        val remoteIds = documents.map { it.id }.toSet()
        for ((fileName, fileState) in state) {
            if(fileState.docId !in remoteIds) warn("$fileName: 서버에 문서 ${fileState.docId}가 없습니다. 파일을 유지합니다")
        }
        return !failed
    }

    private fun pullDocument(document: RemoteDocument) {
        val currentId =document.currentVersionId
        val localFound=state.entries.find { it.value.docId == document.id }
        if(localFound==null){
            download(fileNameFor(document.title, document.id, state, folder), document)
            return
        }

        val fileName=localFound.key
        val fileState=localFound.value
        val seenId=fileState.versionId

        // 버전은 사라지지 않으므로 내가 본 버전이 서버보다 앞설 수 없다. DB 초기화나 다른 서버로 봐서 파일을 건드리지 않는다
        if(seenId != null && (currentId == null || seenId > currentId)) {
            warn("$fileName: 서버가 내 상태보다 뒤에 있습니다. 파일을 유지합니다")
            return
        }

        // 서버에 삭제가 없으니 지운 파일은 복구한다
        val file=folder.resolve(fileName)
        if(!Files.exists(file)) {
            download(fileName, document)
            return
        }

        if(seenId==currentId) return // 이미 최신, 로컬 내용이 변했든 아니든 push에서 판단함

        if(contentHash(Files.readString(file)) != fileState.hash) {
            println("건너뜀: $fileName 은 로컬과 서버가 모두 바뀌었습니다. push 하면 내 수정은 충돌 사본으로 보존됩니다")
            return
        }

        download(fileName, document)
    }
}
