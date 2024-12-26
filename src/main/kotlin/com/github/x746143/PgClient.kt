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
package com.github.x746143

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class PgClient(private val props: PgProperties) : PgConnection {
    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val socketBuilder = aSocket(selectorManager).tcp()
    private val connectionPool = Channel<PgConnectionImpl>(props.maxPoolSize)
    private val mutex = Mutex()
    private var connectionCounter = 0

    suspend fun initPool() {
        mutex.withLock {
            repeat(props.minPoolSize) {
                connectionPool.send(createConnection())
                connectionCounter++
            }
        }
    }

    private suspend fun createConnection(): PgConnectionImpl {
        val socket = socketBuilder.connect(props.host, props.port)
        val readChannel = socket.openReadChannel()
        val writeChannel = socket.openWriteChannel()
        authenticate(readChannel, writeChannel)
        return PgConnectionImpl(readChannel, writeChannel) {
            releaseConnection(it)
        }
    }

    private suspend fun authenticate(readChannel: ByteReadChannel, writeChannel: ByteWriteChannel) {
        TODO()
    }

    private suspend inline fun acquireConnection(): PgConnectionImpl = withTimeout(props.timeout) {
        connectionPool.tryReceive().getOrElse {
            mutex.withLock {
                if (connectionCounter < props.maxPoolSize) {
                    val connection = createConnection()
                    connectionCounter++
                    connection
                } else {
                    connectionPool.receive()
                }
            }
        }
    }

    private suspend inline fun releaseConnection(connection: PgConnectionImpl) {
        connectionPool.send(connection)
    }

    override suspend fun query(sql: String): List<Row> {
        return acquireConnection().use {
            it.query(sql)
        }
    }

    suspend fun transaction(block: (PgConnectionImpl) -> Unit) {
        return acquireConnection().use {
            block(it)
        }
    }

    override suspend fun close() {
        TODO()
    }
}