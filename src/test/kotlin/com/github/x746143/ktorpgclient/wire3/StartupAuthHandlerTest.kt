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
package com.github.x746143.ktorpgclient.wire3

import com.github.x746143.ktorpgclient.PgAuthProperties
import com.github.x746143.ktorpgclient.PgException
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
    private var props = TestPgAuthProperties("test-user", "test-password", "test-db", "test-app")

    @Timeout(1)
    @Test
    fun testStartupMessage() = runBlocking {
        StartupAuthHandler(input, output, props).sendStartupMessage()
        output.close()
        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-STARTUPMESSAGE
        val expected = """
            [00000066][00030000]
            user[00]test-user[00]
            database[00]test-db[00]
            application_name[00]test-app[00]
            client_encoding[00]utf8[00]
            DateStyle[00]ISO[00][00]
            """.mixedHexToByteArray()
        assertContentEquals(expected, output.toByteArray())
    }

    @Timeout(1)
    @Test
    fun testSaslAuthentication() = runBlocking {
        val scramClient = ScramClient.builder()
            .advertisedMechanisms(listOf("SCRAM-SHA-256", "SCRAM-SHA-256-PLUS"))
            .username(props.username)
            .password(props.password.toCharArray())
            .nonceSupplier { "Zr_UEQW:@]US$3;>;OWTSOJF" }
            .build()

        val job = launch {
            StartupAuthHandler(input, output, props, scramClient).authenticate()
        }

        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONSASL
        val authRequestSasl = "R[00000017][0000000a]SCRAM-SHA-256[00][00]".mixedHexToByteArray()
        input.writeByteArray(authRequestSasl)

        yield()
        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-SASLINITIALRESPONSE
        val expectedSaslInitialResponse = """
            p[0000003f]SCRAM-SHA-256[00][00000029]
            n,,n=test-user,r=Zr_UEQW:@]US$3;>;OWTSOJF
            """.mixedHexToByteArray()
        val actualSaslInitialResponse = output.readByteArray(output.availableForRead)
        assertContentEquals(expectedSaslInitialResponse, actualSaslInitialResponse)

        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONSASLCONTINUE
        val authRequestSaslContinue = """
            R[0000005c][0000000b]
            r=Zr_UEQW:@]US$3;>;OWTSOJFVt7fBPDMwIckFZhqWGSWllyY,
            s=rD4Klg5kIRdD9Lmh0WIhfA==,i=4096
            """.mixedHexToByteArray()
        input.writeByteArray(authRequestSaslContinue)

        yield()
        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-SASLRESPONSE
        val expectedSaslResponse = """
            p[0000006c]
            c=biws,r=Zr_UEQW:@]US$3;>;OWTSOJFVt7fBPDMwIckFZhqWGSWllyY,
            p=vgKpQ5YXjKrIcvyuopH/bp+7Rhp5we1poCyKkIMyrpI=
            """.mixedHexToByteArray()
        val actualSaslResponse = output.readByteArray(output.availableForRead)
        assertContentEquals(expectedSaslResponse, actualSaslResponse)

        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONSASLFINAL
        val authRequestSaslFinal = """
            R[00000036][0000000c]v=IpT7VwZ1dlYdlXrfOKJUdS+VVTz+8/Oark1phDOpQFQ=
            """.mixedHexToByteArray()
        input.writeByteArray(authRequestSaslFinal)

        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONOK
        val authRequestSuccess = "R[00000008][00000000]".mixedHexToByteArray()
        input.writeByteArray(authRequestSuccess)

        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-READYFORQUERY
        val readyForQuery = "Z[00000005]I".mixedHexToByteArray()
        input.writeByteArray(readyForQuery)

        job.join()
    }

    @Timeout(1)
    @Test
    fun testMd5Authentication() = runBlocking {
        val job = launch {
            StartupAuthHandler(input, output, props).authenticate()
        }

        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONMD5PASSWORD
        val authenticationMD5Password = "R[0000000c][00000005][34ac9b4f]".mixedHexToByteArray()
        input.writeByteArray(authenticationMD5Password)

        yield()
        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-PASSWORDMESSAGE
        val expectedPasswordMessage = """
            p[00000028]md5d0083471c712392fd3ba76ada9f85d3c[00]
            """.mixedHexToByteArray()
        val actualPasswordMessage = output.readByteArray(output.availableForRead)
        assertContentEquals(expectedPasswordMessage, actualPasswordMessage)

        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONOK
        val authRequestSuccess = "R[00000008][00000000]".mixedHexToByteArray()
        input.writeByteArray(authRequestSuccess)

        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-READYFORQUERY
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
        // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-ERRORRESPONSE
        val errorResponse = """
            E[00000069]SFATAL[00]VFATAL[00]C28P01[00]
            Mpassword authentication failed for user "test-user"[00]
            Fauth.c[00]L323[00]Rauth_failed[00][00]
            """.mixedHexToByteArray()
        input.writeByteArray(errorResponse)

        val ex = deferredException.await()
        assertEquals("FATAL", ex.severity)
        assertEquals("28P01", ex.code)
        assertContains(ex.message, "password authentication failed")
    }

    private class TestPgAuthProperties(
        override val username: String,
        override val password: String,
        override val database: String,
        override val appName: String
    ) : PgAuthProperties
}