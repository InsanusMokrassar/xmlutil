/*
 * Copyright (c) 2021.
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

package nl.adaptivity.xmlutil

import nl.adaptivity.xmlutil.util.*
import nl.adaptivity.xmlutil.util.filterTyped
import nl.adaptivity.xmlutil.util.isElement
import nl.adaptivity.xmlutil.util.isText
import org.w3c.dom.*

/**
 * Created by pdvrieze on 22/03/17.
 */
public class DomReader(public val delegate: Node) : XmlReader {
    private var current: Node? = null

    override val namespaceURI: String
        get() = (current as Element?)?.run { namespaceURI ?: "" }
            ?: throw XmlException("Only elements have a namespace uri")

    override val localName: String
        get() = (current as Element?)?.localName
            ?: throw XmlException("Only elements have a local name")

    override val prefix: String
        get() = (current as Element?)?.run { prefix ?: "" }
            ?: throw XmlException("Only elements have a prefix")


    override var isStarted: Boolean = false
        private set

    private var atEndOfElement: Boolean = false

    override var depth: Int = 0
        private set

    override val text: String
        get() = when (current?.nodeType) {
            Node.ENTITY_REFERENCE_NODE,
            Node.COMMENT_NODE,
            Node.TEXT_NODE,
            Node.PROCESSING_INSTRUCTION_NODE,
            Node.CDATA_SECTION_NODE -> (current as CharacterData).data
            else                    -> throw XmlException("Node is not a text node")
        }

    override val attributeCount: Int get() = (current as Element?)?.attributes?.length ?: 0

    override val eventType: EventType
        get() = when (val c = current) {
            null -> EventType.END_DOCUMENT
            else -> c.nodeType.toEventType(atEndOfElement)
        }

    private var _namespaceAttrs: List<Attr>? = null
    internal val namespaceAttrs: List<Attr>
        get() {

            return _namespaceAttrs ?: (
                    currentElement.attributes.filterTyped<Attr> { it.prefix == "xmlns" || (it.prefix.isNullOrEmpty() && it.localName == "xmlns") }.also {
                        _namespaceAttrs = it
                    })

        }

    override val locationInfo: String?
        get() {

            fun <A : Appendable> helper(node: Node?, result: A): A = when {
                node == null ||
                        node.nodeType == Node.DOCUMENT_NODE
                     -> result

                node.isElement
                     -> helper(node.parentNode, result).apply { append('/').append(node.nodeName) }

                node.isText
                     -> helper(node.parentNode, result).apply { append("/text()") }

                else -> helper(node.parentNode, result).apply { append("/.") }
            }

            return helper(current, StringBuilder()).toString()
        }

    private val requireCurrent get() = current ?: throw IllegalStateException("No current element")
    internal val currentElement get() = (current as Element)

    override val namespaceContext: IterableNamespaceContext
        get() = object : IterableNamespaceContext {
            private val currentElement: Element? = (requireCurrent as? Element) ?: requireCurrent.parentNode as? Element

            override fun getNamespaceURI(prefix: String): String? {
                return currentElement?.lookupNamespaceURI(prefix)
            }

            override fun getPrefix(namespaceURI: String): String? {
                return currentElement?.lookupPrefix(namespaceURI)
            }

            override fun freeze(): IterableNamespaceContext = this

            override fun iterator(): Iterator<Namespace> {
                return sequence<Namespace> {
                    var c: Element? = currentElement
                    while (c!=null) {
                        c.attributes.forEachAttr { attr ->
                            when {
                                attr.prefix == "xmlns" ->
                                    yield(XmlEvent.NamespaceImpl(attr.localName, attr.value))

                                attr.prefix.isNullOrEmpty() && attr.localName == "xmlns" ->
                                    yield(XmlEvent.NamespaceImpl("", attr.value))
                            }
                        }
                        c = c.parentElement
                    }
                }.iterator()
            }

            @Suppress("OverridingDeprecatedMember")
            override fun getPrefixesCompat(namespaceURI: String): Iterator<String> {
                // TODO return all possible ones by doing so recursively
                return listOfNotNull(getPrefix(namespaceURI)).iterator()
            }
        }

    override val namespaceDecls: List<Namespace>
        get() {
            return sequence<Namespace> {
                for (attr in attributes) {
                    when {
                        attr.prefix == "xmlns" ->
                            yield(XmlEvent.NamespaceImpl(attr.localName, attr.value))

                        attr.prefix.isEmpty() && attr.localName == "xmlns" ->
                            yield(XmlEvent.NamespaceImpl("", attr.value))
                    }
                }
            }.toList()
        }
    override val encoding: String?
        get() = when (val d = delegate) {
            is Document -> d.inputEncoding
            else        -> d.ownerDocument!!.inputEncoding
        }

    override val standalone: Boolean?
        get() = null // not defined on DOM.

    override val version: String? get() = "1.0"

    override fun hasNext(): Boolean {
        return !atEndOfElement || current != delegate
    }

    override fun next(): EventType {
        _namespaceAttrs = null // reset lazy value
        val c = current
        if (c == null) {
            isStarted = true
            current = delegate
            return EventType.START_DOCUMENT
        } else { // set current to the new element
            when {
                atEndOfElement       -> {
                    if (c.nextSibling != null) {
                        current = c.nextSibling
                        atEndOfElement = false
                        // This falls back all the way to the bottom to return the current even type (starting the sibling)
                    } else { // no more siblings, go back to parent
                        current = c.parentNode
                        return current?.nodeType?.toEventType(true) ?: EventType.END_DOCUMENT
                    }
                }
                c.firstChild != null -> { // If we have a child, the next element is the first child
                    current = c.firstChild
                }
                else                 -> {
                    // We have no children, but we have a sibling. We are at the end of this element, next we will return
                    // the sibling, or close the parent if there is no sibling
                    atEndOfElement = true
                    return EventType.END_ELEMENT
                }
/*
                else                  -> {
                    atEndOfElement = true // We are the last item in the parent, so the parent needs to be end of an element as well
                    return EventType.END_ELEMENT
                }
*/
            }
            val nodeType = current!!.nodeType
            if (nodeType != Node.ELEMENT_NODE && nodeType != Node.DOCUMENT_NODE) {
                atEndOfElement = true // No child elements for things like text
            }
            return nodeType.toEventType(atEndOfElement)
        }
    }

    override fun getAttributeNamespace(index: Int): String {
        val attr = currentElement.attributes.get(index) ?: throw IndexOutOfBoundsException()
        return attr.namespaceURI ?: ""
    }

    override fun getAttributePrefix(index: Int): String {
        val attr = currentElement.attributes.get(index) ?: throw IndexOutOfBoundsException()
        return attr.prefix ?: ""
    }

    override fun getAttributeLocalName(index: Int): String {
        val attr = currentElement.attributes.get(index) ?: throw IndexOutOfBoundsException()
        return attr.localName
    }

    override fun getAttributeValue(index: Int): String {
        val attr: Attr = currentElement.attributes.item(index) as Attr? ?: throw IndexOutOfBoundsException()
        return attr.value
    }

    override fun getAttributeValue(nsUri: String?, localName: String): String? {
        return currentElement.getAttributeNS(nsUri, localName)
    }

    override fun close() {
        current = null
    }

    override fun getNamespacePrefix(namespaceUri: String): String? {
        return requireCurrent.myLookupPrefix(namespaceUri)
    }

    override fun getNamespaceURI(prefix: String): String? {
        return requireCurrent.myLookupNamespaceURI(prefix)
    }
}


private fun Short.toEventType(endOfElement: Boolean): EventType {
    return when (this) {
        Node.ATTRIBUTE_NODE              -> EventType.ATTRIBUTE
        Node.CDATA_SECTION_NODE          -> EventType.CDSECT
        Node.COMMENT_NODE                -> EventType.COMMENT
        Node.DOCUMENT_TYPE_NODE          -> EventType.DOCDECL
        Node.ENTITY_REFERENCE_NODE       -> EventType.ENTITY_REF
        Node.DOCUMENT_NODE               -> if (endOfElement) EventType.START_DOCUMENT else EventType.END_DOCUMENT
//    Node.DOCUMENT_NODE -> EventType.END_DOCUMENT
        Node.PROCESSING_INSTRUCTION_NODE -> EventType.PROCESSING_INSTRUCTION
        Node.TEXT_NODE                   -> EventType.TEXT
        Node.ELEMENT_NODE                -> if (endOfElement) EventType.END_ELEMENT else EventType.START_ELEMENT
//    Node.ELEMENT_NODE -> EventType.END_ELEMENT
        else                             -> throw XmlException("Unsupported event type")
    }
}
