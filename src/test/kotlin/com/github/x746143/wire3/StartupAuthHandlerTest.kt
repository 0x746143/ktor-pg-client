/*
 * Copyright 2024 0x746143
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.x746143.wire3

import com.github.x746143.PgAuthProperties
import com.github.x746143.PgException
import com.ongres.scram.client.ScramClient
import io.ktor.utils.io.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class StartupAuthHandlerTest {

    private var input = ByteChannel(autoFlush = true)
    private var output = ByteChannel(autoFlush = true)
    private var props = TestPgAuthProperties("dbname", "username", "password", "test-app")

    @Timeout(1)
    @Test
    fun testStartupMessage() = runBlocking {
        StartupAuthHandler(input, output, props).sendStartupMessage()
        output.close()
        val expected = """
            [00000064][00030000]
            user[00]username[00]
            database[00]dbname[00]
            application_name[00]test-app[00]
            client_encoding[00]utf8[00]
            DateStyle[00]ISO[00][00]
            """.mixedHexToByteArray()
        assertContentEquals(expected, output.toByteArray())
    }

    @Timeout(1)
    @Test
    fun testSuccessfulSaslAuthentication() = runBlocking {
        val scramClient = ScramClient.builder()
            .advertisedMechanisms(listOf("SCRAM-SHA-256", "SCRAM-SHA-256-PLUS"))
            .username("postgres")
            .password("postgres".toCharArray())
            .nonceSupplier { "$>n-:R]0GY^zP>f_D;L@1'Wg" }
            .build()

        val job = launch {
            StartupAuthHandler(input, output, props, scramClient).authenticate()
        }

        val authRequestSasl = "R[00000017][0000000a]SCRAM-SHA-256[00][00]".mixedHexToByteArray()
        input.writeByteArray(authRequestSasl)

        yield()
        val expectedSaslInitialResponse = """
            p[0000003e]SCRAM-SHA-256[00][00000028]
            n,,n=postgres,r=$>n-:R]0GY^zP>f_D;L@1'Wg
            """.mixedHexToByteArray()
        val actualSaslInitialResponse = output.readByteArray(output.availableForRead)
        assertContentEquals(expectedSaslInitialResponse, actualSaslInitialResponse)

        val authRequestSaslContinue = """
            R[0000005c][0000000b]
            r=$>n-:R]0GY^zP>f_D;L@1'WgMQsJvP3m/8sjtDu/IEq5aBdh,
            s=IRBTHxuXNWaXk2tAFLEntw==,i=4096
            """.mixedHexToByteArray()
        input.writeByteArray(authRequestSaslContinue)

        yield()
        val expectedSaslResponse = """
            p[0000006c]
            c=biws,r=$>n-:R]0GY^zP>f_D;L@1'WgMQsJvP3m/8sjtDu/IEq5aBdh,
            p=9bWnsvghpNuaxrU62Nj5TW+TtpdwWGM/416H5J8QYRU=
            """.mixedHexToByteArray()
        val actualSaslResponse = output.readByteArray(output.availableForRead)
        assertContentEquals(expectedSaslResponse, actualSaslResponse)

        val authRequestSaslComplete = """
            R[00000036][0000000c]v=tC69GfdeTdUW0i7yVSWyDwM+gCJQ0bNovqVJPRrQXPs=
            """.mixedHexToByteArray()
        input.writeByteArray(authRequestSaslComplete)

        val authRequestSuccess = "R[00000008][00000000]".mixedHexToByteArray()
        input.writeByteArray(authRequestSuccess)

        val readyForQuery = "Z[00000005]I".mixedHexToByteArray()
        input.writeByteArray(readyForQuery)

        job.join()
    }

    @Timeout(1)
    @Test
    fun testFailedAuthentication() = runBlocking {
        val deferredException = async {
            assertThrows<PgException> {
                StartupAuthHandler(input, output, props).authenticate()
            }
        }
        val errorResponse = """
            E[00000064]SFATAL[00]VFATAL[00]C28P01[00]
            Mpassword authentication failed for user "test"[00]
            Fauth.c[00]L323[00]Rauth_failed[00][00]
            """.mixedHexToByteArray()
        input.writeByteArray(errorResponse)

        val ex = deferredException.await()
        assertEquals("FATAL", ex.severity)
        assertEquals("28P01", ex.code)
        assertContains(ex.message, "password authentication failed")
    }

    private class TestPgAuthProperties(
        override val database: String,
        override val username: String,
        override val password: String,
        override val appName: String
    ) : PgAuthProperties
}