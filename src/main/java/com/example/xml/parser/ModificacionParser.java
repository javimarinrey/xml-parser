package com.example.xml.parser;

import com.example.xml.model.ModificacionRequest;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Parses the remainder of a MODIFICACION XML request.
 *
 * <p>Thread-safe: stateless.
 */
public final class ModificacionParser implements XmlParser<ModificacionRequest> {

    @Override
    public String tipo() {
        return "MODIFICACION";
    }

    @Override
    public ModificacionRequest parse(XMLStreamReader reader) throws XMLStreamException {
        String id    = null;
        String campo = null;
        String valor = null;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }

            switch (reader.getLocalName()) {
                case "id"    -> id    = reader.getElementText();
                case "campo" -> campo = reader.getElementText();
                case "valor" -> valor = reader.getElementText();
            }

            if (id != null && campo != null && valor != null) {
                break;
            }
        }

        return new ModificacionRequest(id, campo, valor);
    }
}
