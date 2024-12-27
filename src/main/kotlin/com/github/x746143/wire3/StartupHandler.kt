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

// https://www.postgresql.org/docs/16/protocol-flow.html#PROTOCOL-FLOW-START-UP
internal class StartupHandler(
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
    private val props: PgAuthProperties
) {
    companion object {
        // configurable params
        private val userParam = "user".toByteArray()
        private val databaseParam = "database".toByteArray()
        // https://www.postgresql.org/docs/16/runtime-config-client.html
        // SQL command: show all
        private val appNameParam = "application_name".toByteArray()
        // hard-coded params
        private val encodingParam = "client_encoding".toByteArray()
        private val encodingValue = "utf8".toByteArray()
        private val dateStyleParam = "DateStyle".toByteArray()
        private val dateStyleValue = "ISO".toByteArray()
        // TODO: Add more parameters
    }

    // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-STARTUPMESSAGE
    suspend fun sendStartupMessage() {
        output.writePgMessage {
            writeInt(0x00030000) // The protocol version 3.0
            writeList {
                param(userParam, props.username)
                param(databaseParam, props.database)
                param(appNameParam, props.appName)
                param(encodingParam, encodingValue)
                param(dateStyleParam, dateStyleValue)
            }
        }
    }

    suspend fun authenticate() {
        TODO()
    }
}