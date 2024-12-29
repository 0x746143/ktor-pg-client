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
import kotlinx.io.Source
import kotlinx.io.readString
import kotlinx.io.writeString

// https://www.postgresql.org/docs/16/protocol-flow.html#PROTOCOL-FLOW-START-UP
internal class StartupAuthHandler(
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

        val scramMechanisms = listOf("SCRAM-SHA-256", "SCRAM-SHA-256-PLUS")
        private val scramSha256 = scramMechanisms[0].toByteArray()
    }

    // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-STARTUPMESSAGE
    suspend fun sendStartupMessage() {
        output.writePgMessage {
            writeInt(MessageCode.PROTOCOL_VERSION)
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
                BackendMessage.AUTHENTICATION -> authenticateScram(source)
                // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-PARAMETERSTATUS
                BackendMessage.PARAMETER_STATUS -> {} // TODO: handling
                // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-BACKENDKEYDATA
                BackendMessage.BACKEND_KEY_DATA -> {} // TODO: handling
                // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-ERRORRESPONSE
                BackendMessage.ERROR_RESPONSE -> {
                    generateSequence {
                        source.readByte().toInt().let { fieldType ->
                            if (fieldType == 0) null
                            else fieldType to source.readCString()
                        }
                    }.toMap().let { fields ->
                        throw PgException(
                            fields[ErrorField.MESSAGE] ?: "Unknown error",
                            fields[ErrorField.SEVERITY],
                            fields[ErrorField.CODE],
                        )
                    }
                }
                // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-READYFORQUERY
                BackendMessage.READY_FOR_QUERY -> return
                else -> throw PgException("Unsupported message type: ${messageType.toInt().toChar()}")
            }
        }
    }

    private suspend fun authenticateScram(source: Source) {
        when (val authDataType = source.readInt()) {
            // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONSASL
            // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-SASLINITIALRESPONSE
            Authentication.SASL -> output.writePgMessage(FrontendMessage.SASL_INITIAL_RESPONSE) {
                writeCString(scramSha256)
                scramClient.clientFirstMessage()
                    .toString()
                    .toByteArray()
                    .also { writeInt(it.size) }
                    .also { write(it) }
            }
            // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONSASLCONTINUE
            // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-SASLRESPONSE
            Authentication.SASL_CONTINUE -> output.writePgMessage(FrontendMessage.SASL_RESPONSE) {
                scramClient.serverFirstMessage(source.readString())
                writeString(scramClient.clientFinalMessage().toString())
            }
            // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONSASLFINAL
            Authentication.SASL_FINAL -> scramClient.serverFinalMessage(source.readString())
            // https://www.postgresql.org/docs/16/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-AUTHENTICATIONOK
            Authentication.OK -> {} // TODO: handling
            else -> throw PgException("Unsupported authentication data type: $authDataType")
        }
    }
}