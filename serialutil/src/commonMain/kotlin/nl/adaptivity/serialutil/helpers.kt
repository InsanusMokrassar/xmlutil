/*
 * Copyright (c) 2019.
 *
 * This file is part of xmlutil.
 *
 * This file is licenced to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You should have received a copy of the license with the source distribution.
 * Alternatively, you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package nl.adaptivity.serialutil

import kotlinx.serialization.*
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import nl.adaptivity.serialutil.impl.toCharArray
import kotlin.jvm.JvmOverloads

object CharArrayAsStringSerializer : KSerializer<CharArray> {
    override val descriptor = PrimitiveSerialDescriptor("CharArrayAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CharArray) =
        encoder.encodeString(value.concatToString())

    override fun deserialize(decoder: Decoder): CharArray = decoder.decodeString().toCharArray()
}

@ExperimentalSerializationApi
fun Decoder.readNullableString(): String? =
    decodeNullableSerializableValue(String.serializer().nullable)

@ExperimentalSerializationApi
@JvmOverloads
fun CompositeDecoder.readNullableString(desc: SerialDescriptor, index: Int, previousValue: String? = null): String? =
    decodeNullableSerializableElement(desc, index, String.serializer().nullable, previousValue)

@ExperimentalSerializationApi
@Deprecated("Use new name", ReplaceWith("encodeNullableStringElement(desc, index, value)"), DeprecationLevel.ERROR)
fun CompositeEncoder.writeNullableStringElementValue(desc: SerialDescriptor, index: Int, value: String?) =
    encodeNullableStringElement(desc, index, value)

@ExperimentalSerializationApi
fun CompositeEncoder.encodeNullableStringElement(desc: SerialDescriptor, index: Int, value: String?) =
    encodeNullableSerializableElement(desc, index, String.serializer(), value)

@Deprecated("Use newer name", ReplaceWith("decodeElements(input, body)"))
inline fun DeserializationStrategy<*>.readElements(input: CompositeDecoder, body: (Int) -> Unit) =
    decodeElements(input, body)

/**
 * Helper function that helps decoding structure elements
 */
inline fun DeserializationStrategy<*>.decodeElements(input: CompositeDecoder, body: (Int) -> Unit) {
    var index = input.decodeElementIndex(descriptor)
    if (index == CompositeDecoder.DECODE_DONE) return

    while (index >= 0) {
        body(index)
        index = input.decodeElementIndex(descriptor)
    }
}

@Suppress("DEPRECATION")
@Deprecated("Use new named version that matches the newer api", ReplaceWith("decodeStructure(desc, body)"))
inline fun <T> Decoder.readBegin(desc: SerialDescriptor, body: CompositeDecoder.(desc: SerialDescriptor) -> T): T =
    decodeStructure(desc, body)


/**
 * Helper function that automatically closes the decoder on close.
 */
@Deprecated("Use kotlinx.serialization.decodeStructure instead", ReplaceWith("this.decodeStructure(desc, body)", "kotlinx.serialization.decodeStructure"))
inline fun <T> Decoder.decodeStructure(
    desc: SerialDescriptor,
    body: CompositeDecoder.(desc: SerialDescriptor) -> T
                                      ): T {
    val input = beginStructure(desc)
    var skipEnd = false
    try {
        return input.body(desc)
    } catch (e: Exception) {
        skipEnd = true
        throw e
    } finally {
        if (!skipEnd) {
            input.endStructure((desc))
        }
    }
}

@Deprecated("Just use the version now in kotlinx.serialization", ReplaceWith("this.encodeStructure(desc)", "kotlinx.serialization.encodeStructure"))
inline fun Encoder.writeStructure(desc: SerialDescriptor, body: CompositeEncoder.(desc: SerialDescriptor) -> Unit) {
    val output = beginStructure(desc)
    var skipEnd = false
    try {
        return output.body(desc)
    } catch (e: Exception) {
        skipEnd = true
        throw e
    } finally {
        if (!skipEnd) {
            output.endStructure(desc)
        }
    }
}

inline fun Encoder.writeCollection(
    desc: SerialDescriptor,
    collectionSize: Int,
    body: CompositeEncoder.(desc: SerialDescriptor) -> Unit
                                  ) {
    val output = beginCollection(desc, collectionSize)
    var skipEnd = false
    try {
        return output.body(desc)
    } catch (e: Exception) {
        skipEnd = true
        throw e
    } finally {
        if (!skipEnd) {
            output.endStructure(desc)
        }
    }
}
