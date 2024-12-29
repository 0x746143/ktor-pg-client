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
package com.github.x746143.integration

import com.github.x746143.PgClient
import com.github.x746143.PgException
import com.github.x746143.PgProperties
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT
import kotlin.test.*
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticationTest {

    private val postgres = PostgreSQLContainer("postgres:16")
    private lateinit var props: PgProperties

    @BeforeAll
    fun setUp() {
        postgres.start()
        props = PgProperties(
            port = postgres.getMappedPort(POSTGRESQL_PORT),
            username = postgres.username,
            password = postgres.password,
            database = postgres.databaseName,
            minPoolSize = 1,
            maxPoolSize = 1,
            timeout = 1.seconds
        )
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    @Timeout(1)
    @Test
    fun testSuccessfulAuthentication() = runBlocking {
        val pgClient = PgClient(props)
        pgClient.initPool()
        assertEquals(1, pgClient.connectionCounter)
    }

    @Timeout(1)
    @Test
    fun testFailedAuthentication() = runBlocking {
        val ex = assertThrows<PgException> {
            PgClient(props.copy(password = "incorrect_password")).initPool()
        }
        assertEquals("FATAL", ex.severity)
        assertEquals("28P01", ex.code)
        assertContains(ex.message, "password authentication failed")
    }
}