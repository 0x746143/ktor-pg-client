package com.github.x746143.wire3

import com.github.x746143.PgAuthProperties
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class StartupHandlerTest {

    private object AuthProperties : PgAuthProperties {
        override val database = "dbname"
        override val username = "username"
        override val password = "password"
        override val appName = "test-app"
    }

    @Test
    fun sendStartupMessage() {
        val channel = ByteChannel()
        val handler = StartupHandler(ByteChannel(), channel, AuthProperties)
        val expected = ("[0000006400030000]" +
                "user[00]username[00]" +
                "database[00]dbname[00]" +
                "application_name[00]test-app[00]" +
                "client_encoding[00]utf8[00]" +
                "DateStyle[00]ISO[0000]")
            .mixedHexStringToByteArray()
        val actual = runBlocking {
            handler.sendStartupMessage()
            channel.close()
            channel.toByteArray()
        }
        assertArrayEquals(expected, actual)
    }

    @Test
    fun authenticate() {
        TODO()
    }
}