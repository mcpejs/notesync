package io.github.mcpejs.notesync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("test")
class UserConcurrencyTest {

    @Autowired
    lateinit var userService: UserService

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    fun createsUser() {
        val email = "test-${UUID.randomUUID()}@example.com"

        val saved = userService.create("tester", email)

        val found = userRepository.findById(saved.id!!).get()
        assertEquals(email, found.email)
    }

    @Test
    fun parallelCreatesUser() {
        val email = "test-${UUID.randomUUID()}@example.com"

        val success = AtomicInteger(0)
        val conflict = AtomicInteger(0)

        val threads = 10
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)

        repeat(threads) {
            pool.submit {
                start.await()
                try {
                    userService.create("tester", email)
                    success.incrementAndGet()
                } catch (e: DataIntegrityViolationException) {
                    conflict.incrementAndGet()
                }
            }
        }

        start.countDown()
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)

        assertEquals(1, success.get())
        assertEquals(threads - 1, conflict.get())
        assertEquals(1, userRepository.findAll().count { it.email == email })
    }
}

