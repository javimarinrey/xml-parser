package com.example.xml.pool;

import com.example.xml.exception.XmlParseException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;

/**
 * Holds a single, shared {@link XMLInputFactory} configured for maximum
 * throughput and security.
 *
 * <p>{@link XMLInputFactory} is thread-safe and expensive to create —
 * one instance for the entire JVM lifetime. {@link XMLStreamReader}
 * is NOT thread-safe; a new one is created per request.
 *
 * <p>Security hardening applied:
 * <ul>
 *   <li>DTD support disabled (prevents XXE attacks)</li>
 *   <li>External entity resolution disabled</li>
 *   <li>Coalescing disabled (avoids large string allocations)</li>
 *   <li>Validation disabled (schema validation is a separate concern)</li>
 * </ul>
 */
public final class XmlStreamReaderFactory {

    private static final XMLInputFactory FACTORY;

    static {
        FACTORY = XMLInputFactory.newInstance();

        // Security: disable DTD and external entities (XXE prevention)
        FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        // Performance: skip validation and coalescing
        FACTORY.setProperty(XMLInputFactory.IS_VALIDATING, false);
        FACTORY.setProperty(XMLInputFactory.IS_COALESCING, false);

        // Namespace awareness (keep true — required for modern XML)
        FACTORY.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
    }

    private XmlStreamReaderFactory() {}

    /**
     * Creates a new {@link XMLStreamReader} wrapping the given stream.
     *
     * <p>The caller is responsible for closing the reader (and therefore
     * the underlying stream) after use.
     *
     * @param inputStream raw XML bytes; should be buffered before passing in
     * @return a new, non-thread-safe XMLStreamReader
     * @throws XmlParseException if StAX fails to initialise the reader
     */
    public static XMLStreamReader create(InputStream inputStream) {
        try {
            return FACTORY.createXMLStreamReader(inputStream);
        } catch (XMLStreamException e) {
            throw new XmlParseException("No se pudo crear el XMLStreamReader", e);
        }
    }
}
