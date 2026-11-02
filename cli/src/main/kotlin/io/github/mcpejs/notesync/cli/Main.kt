package io.github.mcpejs.notesync.cli

import java.io.IOException
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val baseUrl = System.getenv("NOTESYNC_URL")
    val token = System.getenv("NOTESYNC_TOKEN")
    if (args.size != 1 || baseUrl == null || token == null) {
        System.err.println("사용법: NOTESYNC_URL, NOTESYNC_TOKEN을 설정하고 notesync push|pull")
        exitProcess(2)
    }

    val sync = Sync(ApiClient(baseUrl, token), Path.of("").toAbsolutePath())
    val succeeded = try {
        when (args[0]) {
            "push" -> sync.push()
            "pull" -> sync.pull()
            else -> {
                System.err.println("알 수 없는 명령: ${args[0]}")
                exitProcess(2)
            }
        }
    } catch (e: ApiException) {
        System.err.println("중단: ${e.message}")
        false
    } catch (e: IOException) {
        System.err.println("중단: 서버에 연결할 수 없습니다 ($baseUrl)")
        false
    }

    if (!succeeded) exitProcess(1)
}
