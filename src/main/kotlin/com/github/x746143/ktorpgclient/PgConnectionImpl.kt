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
package com.github.x746143.ktorpgclient

import io.ktor.utils.io.*

class PgConnectionImpl(
    private val readChannel: ByteReadChannel,
    private val writeChannel: ByteWriteChannel,
    private val onClose: (suspend (PgConnectionImpl) -> Unit)?
) : PgConnection {

    override suspend fun query(sql: String): List<Row> {
        TODO()
    }

    private suspend fun closeConnection() {
        TODO()
    }

    override suspend fun close() {
        onClose?.invoke(this) ?: closeConnection()
    }
}