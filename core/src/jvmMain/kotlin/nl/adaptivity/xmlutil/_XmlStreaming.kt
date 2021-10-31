/*
 * Copyright (c) 2018.
 *
 * This file is part of XmlUtil.
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
@file:JvmName("JVMXmlStreamingKt")

package nl.adaptivity.xmlutil

import nl.adaptivity.xmlutil.XmlStreaming.deSerialize
import nl.adaptivity.xmlutil.core.KtXmlReader
import nl.adaptivity.xmlutil.core.KtXmlWriter
import nl.adaptivity.xmlutil.core.impl.XmlStreamingJavaCommon
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.StringReader
import java.util.*
import javax.xml.transform.Result
import javax.xml.transform.Source


public actual object XmlStreaming : XmlStreamingJavaCommon() {

    private val serviceLoader: ServiceLoader<XmlStreamingFactory> by lazy {
        val service = XmlStreamingFactory::class.java
        ServiceLoader.load(service, service.classLoader)
    }

    @Suppress("ObjectPropertyName")
    private var _factory: XmlStreamingFactory? = StAXStreamingFactory()

    private val factory: XmlStreamingFactory
        get() = _factory ?: serviceLoader.first().also { _factory = it }

    override fun newWriter(result: Result, repairNamespaces: Boolean): XmlWriter {
        return factory.newWriter(result, repairNamespaces)
    }

    override fun newWriter(outputStream: OutputStream, encoding: String, repairNamespaces: Boolean): XmlWriter {
        return factory.newWriter(outputStream, encoding, repairNamespaces)
    }

    public actual override fun newWriter(
        writer: java.io.Writer,
        repairNamespaces: Boolean,
        xmlDeclMode: XmlDeclMode
    ): XmlWriter {
        return factory.newWriter(writer, repairNamespaces = repairNamespaces, xmlDeclMode = xmlDeclMode)
    }

    public actual override fun newWriter(output: Appendable, repairNamespaces: Boolean, xmlDeclMode: XmlDeclMode): XmlWriter {
        return factory.newWriter(output, repairNamespaces, xmlDeclMode)
    }

    public actual fun newGenericWriter(
        output: Appendable,
        isRepairNamespaces: Boolean,
        xmlDeclMode: XmlDeclMode
    ): KtXmlWriter {
        return KtXmlWriter(output, isRepairNamespaces, xmlDeclMode)
    }

    override fun newReader(inputStream: InputStream, encoding: String): XmlReader {
        return factory.newReader(inputStream, encoding)
    }

    override fun newReader(reader: Reader): XmlReader {
        return factory.newReader(reader)
    }

    @Deprecated("Note that sources are inefficient and poorly designed, relying on runtime types")
    override fun newReader(source: Source): XmlReader {
        return factory.newReader(source)
    }

    public actual override fun newReader(input: CharSequence): XmlReader {
        return factory.newReader(input)
    }

    override fun newReader(inputStr: String): XmlReader {
        return factory.newReader(inputStr)
    }

    public actual fun newGenericReader(input: CharSequence): XmlReader =
        newGenericReader(StringReader(input.toString()))

    public fun newGenericReader(input: String): XmlReader =
        newGenericReader(StringReader(input))

    public fun newGenericReader(inputStream: InputStream, encoding: String?): XmlReader =
        KtXmlReader(inputStream, encoding)

    public actual fun newGenericReader(reader: Reader): XmlReader = KtXmlReader(reader)

    public actual override fun setFactory(factory: XmlStreamingFactory?) {
        _factory = factory ?: StAXStreamingFactory()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Note that sources are inefficient and poorly designed, relying on runtime types")
    override fun toCharArray(content: Source): CharArray {
        return newReader(content).toCharArrayWriter().toCharArray()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Note that sources are inefficient and poorly designed, relying on runtime types")
    override fun toString(source: Source): String {
        return newReader(source).toCharArrayWriter().toString()
    }

}


public inline fun <reified T : Any> deserialize(input: InputStream): T = deSerialize(input, T::class.java)

public inline fun <reified T : Any> deserialize(input: Reader): T = deSerialize(input, T::class.java)

public inline fun <reified T : Any> deserialize(input: String): T = deSerialize(input, T::class.java)
