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
import com.ongres.scram.client.ScramClient
import io.ktor.utils.io.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun testSuccessfulSaslAuthentication() = runBlocking {
        val input = ByteChannel(autoFlush = true)
        val output = ByteChannel(autoFlush = true)

        val scramClient = ScramClient.builder()
            .advertisedMechanisms(listOf("SCRAM-SHA-256", "SCRAM-SHA-256-PLUS"))
            .username("postgres")
            .password("postgres".toCharArray())
            .nonceSupplier { "\$>n-:R]0GY^zP>f_D;L@1'Wg" }
            .build()

        val props = object : PgAuthProperties {
            override val database = "postgres"
            override val username = "postgres"
            override val password = "postgres"
            override val appName = ""
        }

        val job = launch {
            StartupHandler(input, output, props, scramClient).authenticate()
        }

        val authRequestSasl = """
            R[00000017][0000000a]
            SCRAM-SHA-256[00][00]
            """.mixedHexToByteArray()
        input.writeByteArray(authRequestSasl)

        yield()
        val expectedSaslInitialResponse = """
            p[0000003e]SCRAM-SHA-256[00][00000028]
            [6e2c2c6e3d706f7374677265732c723d243e6e2d
            3a525d3047595e7a503e665f443b4c4031275767]
            """.mixedHexToByteArray()
        assertEquals(63, output.availableForRead)
        val actualSaslInitialResponse = output.readByteArray(output.availableForRead)
        assertArrayEquals(expectedSaslInitialResponse, actualSaslInitialResponse)

        val authRequestSaslContinue = """
            R[0000005c][0000000b]
            [723d243e6e2d3a525d3047595e7a503e665f443b4c40312757674d51
            734a7650336d2f38736a7444752f49457135614264682c733d495242
            54487875584e5761586b327441464c456e74773d3d2c693d34303936]
            """.mixedHexToByteArray()
        input.writeByteArray(authRequestSaslContinue)

        yield()
        val expectedSaslResponse = """
            p[0000006c]
            [633d626977732c723d243e6e2d3a525d3047595e7a503e665f44
            3b4c40312757674d51734a7650336d2f38736a7444752f494571
            35614264682c703d3962576e73766768704e756178725536324e
            6a3554572b547470647757474d2f34313648354a38515952553d]
            """.mixedHexToByteArray()
        assertEquals(109, output.availableForRead)
        val actualSaslResponse = output.readByteArray(output.availableForRead)
        assertArrayEquals(expectedSaslResponse, actualSaslResponse)

        val authRequestSaslComplete = """
            R[00000036][0000000c]
            [763d744336394766646554645557306937795653577944
            774d2b67434a5130624e6f7671564a505272515850733d]
            """.mixedHexToByteArray()
        input.writeByteArray(authRequestSaslComplete)

        val authRequestSuccess = """
            R[00000008][00000000]
        """.mixedHexToByteArray()
        input.writeByteArray(authRequestSuccess)

        val readyForQuery = "Z[00000005]I".mixedHexToByteArray()
        input.writeByteArray(readyForQuery)

        job.join()
    }
}