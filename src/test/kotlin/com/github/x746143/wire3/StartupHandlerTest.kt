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