package com.example.xml.parser;

import com.example.xml.model.ConsultaRequest;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Parses the remainder of a CONSULTA XML request.
 *
 * <p>Receives the {@link XMLStreamReader} positioned just after
 * {@code </tipo>} and reads until end of document.
 *
 * <p>Thread-safe: stateless, no instance fields mutated during parsing.
 */
public final class ConsultaParser implements XmlParser<ConsultaRequest> {

    @Override
    public String tipo() {
        return "CONSULTA";
    }

    @Override
    public ConsultaRequest parse(XMLStreamReader reader) throws XMLStreamException {
        String id     = null;
        String filtro = null;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }

            switch (reader.getLocalName()) {
                case "id"     -> id     = reader.getElementText();
                case "filtro" -> filtro = reader.getElementText();
                // Ignore unrecognised tags silently
            }

            if (id != null && filtro != null) {
                break; // All fields collected — stop early
            }
        }

        return new ConsultaRequest(id, filtro);
    }
}
