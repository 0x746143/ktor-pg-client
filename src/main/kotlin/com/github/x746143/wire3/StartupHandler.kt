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
import kotlinx.io.Source
import kotlinx.io.readString
import kotlinx.io.writeString

// https://www.postgresql.org/docs/16/protocol-flow.html#PROTOCOL-FLOW-START-UP
internal class StartupHandler(
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
    private val props: PgAuthProperties,
    private val scramClient: ScramClient = ScramClient.builder()
        .advertisedMechanisms(scramMechanisms)
        .username(props.username)
        .password(props.password.toCharArray())
        .build()
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

        private const val AUTHENTICATION_TYPE = 'R'.code.toByte()
        private const val PARAMETER_STATUS_TYPE = 'S'.code.toByte()
        private const val BACKEND_KEY_DATA_TYPE = 'K'.code.toByte()
        private const val READY_FOR_QUERY_TYPE = 'Z'.code.toByte()
        private const val SASL_RESPONSE_TYPE = 'p'.code.toByte()

        val scramMechanisms = listOf("SCRAM-SHA-256", "SCRAM-SHA-256-PLUS")
        private val scramSha256 = scramMechanisms[0].toByteArray()
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
        while (true) {
            val messageType = input.readByte()
            val messageLength = input.readInt()
            val source = input.readPacket(messageLength - 4)
            when (messageType) {
                AUTHENTICATION_TYPE -> authenticateScram(source)
                PARAMETER_STATUS_TYPE -> {} // TODO: handling
                BACKEND_KEY_DATA_TYPE -> {} // TODO: handling
                READY_FOR_QUERY_TYPE -> return
                else -> {
                    println(source.readString())
                    throw PgException("Unsupported message type: ${messageType.toInt().toChar()}")
                }
            }
        }
    }

    private suspend fun authenticateScram(source: Source) {
        when (val authDataType = source.readInt()) {
            // Backend: AuthenticationSASL
            // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONSASL
            // Frontend: SASLInitialResponse
            // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-SASLINITIALRESPONSE
            10 -> output.writePgMessage(SASL_RESPONSE_TYPE) {
                writeCString(scramSha256)
                scramClient.clientFirstMessage()
                    .toString()
                    .toByteArray()
                    .also { writeInt(it.size) }
                    .also { write(it) }
            }

            // Backend: AuthenticationSASLContinue
            // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONSASLCONTINUE
            // Frontend: SASLResponse
            // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-SASLRESPONSE
            11 -> output.writePgMessage(SASL_RESPONSE_TYPE) {
                scramClient.serverFirstMessage(source.readString())
                writeString(scramClient.clientFinalMessage().toString())
            }

            // Backend: AuthenticationSASLFinal
            // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONSASLFINAL
            12 -> scramClient.serverFinalMessage(source.readString())

            // Backend: AuthenticationOk
            // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONOK
            0 -> {} // TODO: handling

            else -> throw PgException("Unsupported authentication data type: $authDataType")
        }
    }
}