package com.example.xml.parser;

import com.example.xml.model.AltaRequest;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Parses the remainder of an ALTA XML request.
 *
 * <p>Thread-safe: stateless.
 */
public final class AltaParser implements XmlParser<AltaRequest> {

    @Override
    public String tipo() {
        return "ALTA";
    }

    @Override
    public AltaRequest parse(XMLStreamReader reader) throws XMLStreamException {
        String nombre = null;
        String email  = null;
        String perfil = null;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }

            switch (reader.getLocalName()) {
                case "nombre" -> nombre = reader.getElementText();
                case "email"  -> email  = reader.getElementText();
                case "perfil" -> perfil = reader.getElementText();
            }

            if (nombre != null && email != null && perfil != null) {
                break;
            }
        }

        return new AltaRequest(nombre, email, perfil);
    }
}
