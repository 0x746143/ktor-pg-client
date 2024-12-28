package com.github.x746143.wire3

import com.github.x746143.PgAuthProperties
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class StartupHandlerTest {

    @Test
    fun testStartupMessage() = runBlocking {
        val output = ByteChannel()
        val props = object : PgAuthProperties {
            override val database = "dbname"
            override val username = "username"
            override val password = "password"
            override val appName = "test-app"
        }
        StartupHandler(ByteChannel(), output, props).sendStartupMessage()
        output.close()
        val expected = """
            [00000064][00030000]
            user[00]username[00]
            database[00]dbname[00]
            application_name[00]test-app[00]
            client_encoding[00]utf8[00]
            DateStyle[00]ISO[00][00]
            """.mixedHexToByteArray()
        assertArrayEquals(expected, output.toByteArray())
    }

    @Test
    fun authenticate() {
        TODO()
    }
}