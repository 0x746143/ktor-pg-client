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

import kotlinx.io.Buffer
import kotlinx.io.readByteArray

fun String.mixedHexToByteArray(): ByteArray {
    val str = trimIndent().replace("\n", "")
    val buffer = Buffer()
    var hexMode = false
    var i = 0
    while (i < str.length) {
        val ch = str[i++]
        if (ch > '~') {
            throw IllegalArgumentException("Invalid character $str at index $i")
        }
        if (ch == '[' && !hexMode) {
            hexMode = true
            continue
        }
        if (ch == ']' && hexMode) {
            hexMode = false
            continue
        }
        if (hexMode) {
            val highNibble = ch.hexToInt(i) shl 4
            val lowNibble = str[i++].hexToInt(i)
            buffer.writeByte((highNibble or lowNibble).toByte())
        } else {
            buffer.writeByte(ch.code.toByte())
        }
    }
    return buffer.readByteArray()
}

private fun Char.hexToInt(i: Int): Int {
    return when (this) {
        in '0'..'9' -> this - '0'
        in 'a'..'f' -> this - 'a' + 10
        else -> throw IllegalArgumentException("Invalid hex character $this at index $i")
    }
}
