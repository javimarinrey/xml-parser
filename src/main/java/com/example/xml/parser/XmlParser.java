package com.example.xml.parser;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Contract for all type-specific XML parsers.
 *
 * <p>Implementations receive an {@link XMLStreamReader} already positioned
 * just after {@code </tipo>} (end of the phase-1 peek). They must parse
 * the remaining content and return a fully populated DTO.
 *
 * <p>Implementations MUST be stateless and thread-safe — a single instance
 * is shared across all concurrent requests.
 *
 * @param <T> the DTO type produced by this parser
 */
public interface XmlParser<T> {

    /**
     * Returns the value of {@code <tipo>} this parser handles (e.g. "CONSULTA").
     */
    String tipo();

    /**
     * Parses the remainder of the XML stream and returns the populated DTO.
     *
     * @param reader positioned just after {@code </tipo>}
     * @return parsed DTO
     * @throws XMLStreamException on StAX errors
     */
    T parse(XMLStreamReader reader) throws XMLStreamException;
}
