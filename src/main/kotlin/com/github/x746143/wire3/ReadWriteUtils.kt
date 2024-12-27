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

import io.ktor.utils.io.*
import kotlinx.io.*

internal fun Buffer.writeCString(str: String) {
    writeString(str)
    writeByte(0)
}

internal fun Buffer.writeCString(arr: ByteArray) {
    write(arr)
    writeByte(0)
}

internal inline fun Buffer.writeList(block: Buffer.() -> Unit) {
    block()
    writeByte(0)
}

internal fun Buffer.param(name: ByteArray, value: String) {
    writeCString(name)
    writeCString(value)
}

internal fun Buffer.param(name: ByteArray, value: ByteArray) {
    writeCString(name)
    writeCString(value)
}

internal fun Source.readCString(): String {
    return readString(indexOf(0))
}

internal fun Source.readCStringOrNull(): String? {
    val length = peek().indexOf(0)
    return if (length.toInt() > 0) readString(length) else null
}

internal suspend inline fun ByteWriteChannel.writePgMessage(
    flush: Boolean = true,
    identifier: Byte,
    block: Buffer.() -> Unit
) {
    writeByte(identifier)
    writePgMessage(flush, block)
}

internal suspend inline fun ByteWriteChannel.writePgMessage(
    flush: Boolean = true,
    block: Buffer.() -> Unit
) {
    val buffer = Buffer()
    buffer.block()
    writeInt(buffer.size.toInt() + 4)
    writeBuffer(buffer)
    if (flush) {
        flush()
    }
}

internal suspend inline fun ByteWriteChannel.writePgMessage(
    flush: Boolean = true,
    identifier: Byte
) {
    writeByte(identifier)
    writeInt(4)
    if (flush) {
        flush()
    }
}