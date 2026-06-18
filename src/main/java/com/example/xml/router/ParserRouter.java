package com.example.xml.router;

import com.example.xml.exception.UnknownTipoException;
import com.example.xml.exception.XmlParseException;
import com.example.xml.parser.XmlParser;
import com.example.xml.pool.XmlStreamReaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Two-phase XML dispatcher.
 *
 * <p><b>Phase 1</b> — reads the stream only until {@code <tipo>} is found,
 * extracts its text content, and leaves the reader positioned just after
 * {@code </tipo>}. Cost: O(bytes until {@code </tipo>}).
 *
 * <p><b>Phase 2</b> — delegates to the matching {@link XmlParser} using the
 * <em>same</em> {@link XMLStreamReader}. No re-parsing, no stream copy.
 *
 * <p>This class is thread-safe. The internal {@code Map} is immutable after
 * construction. Each call to {@link #dispatch} creates its own reader.
 */
public final class ParserRouter {

    private static final Logger log = LoggerFactory.getLogger(ParserRouter.class);

    /** Tag name that identifies the request type. */
    private static final String TIPO_TAG = "tipo";

    /** Buffer size for wrapping raw InputStreams (8 KiB). */
    private static final int BUFFER_SIZE = 8_192;

    /** Immutable map built once at startup. */
    private final Map<String, XmlParser<?>> parsers;

    /**
     * Builds the router from the supplied list of parsers.
     *
     * @param parsers list of all registered {@link XmlParser} implementations
     * @throws IllegalArgumentException if two parsers share the same tipo
     */
    public ParserRouter(List<XmlParser<?>> parsers) {
        this.parsers = parsers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        XmlParser::tipo,
                        Function.identity(),
                        (a, b) -> { throw new IllegalArgumentException(
                                "Tipo duplicado en parsers: " + a.tipo()); }
                ));

        log.info("ParserRouter inicializado con {} tipos: {}",
                this.parsers.size(), this.parsers.keySet());
    }

    /**
     * Dispatches the XML in {@code rawXml} to the appropriate parser.
     *
     * @param rawXml the raw request body; ownership is NOT transferred
     *               (the caller remains responsible for closing it)
     * @return the DTO produced by the matching parser
     * @throws XmlParseException    if the XML is malformed or {@code <tipo>} is absent
     * @throws UnknownTipoException if no parser is registered for the detected tipo
     */
    public Object dispatch(InputStream rawXml) {
        // Wrap in a buffer if not already buffered — StAX has no internal buffer
        InputStream buffered = rawXml instanceof BufferedInputStream
                ? rawXml
                : new BufferedInputStream(rawXml, BUFFER_SIZE);

        XMLStreamReader reader = XmlStreamReaderFactory.create(buffered);
        try {
            // ── Phase 1: peek for <tipo> ──────────────────────────────────────
            String tipo = peekTipo(reader);
            log.debug("Tipo detectado: {}", tipo);

            // ── Phase 2: full parse with the same reader ──────────────────────
            return route(tipo, reader);

        } catch (XMLStreamException e) {
            throw new XmlParseException("Error StAX durante el parseo", e);
        } finally {
            closeQuietly(reader);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Advances the reader until it finds a {@code START_ELEMENT} named
     * {@value #TIPO_TAG}, then returns its text content via
     * {@link XMLStreamReader#getElementText()} — which also consumes the
     * matching {@code END_ELEMENT}, leaving the cursor ready for phase 2.
     */
    private static String peekTipo(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT
                    && TIPO_TAG.equals(reader.getLocalName())) {
                String value = reader.getElementText().strip();
                if (value.isEmpty()) {
                    throw new XmlParseException("El tag <tipo> está vacío");
                }
                return value;
            }
        }
        throw new XmlParseException("No se encontró el tag <tipo> en el XML");
    }

    /**
     * Looks up the parser for {@code tipo} and delegates phase 2.
     * Cast is safe because each parser is registered with its own type token.
     */
    @SuppressWarnings("unchecked")
    private <T> T route(String tipo, XMLStreamReader reader) throws XMLStreamException {
        XmlParser<T> parser = (XmlParser<T>) parsers.get(tipo);
        if (parser == null) {
            throw new UnknownTipoException(tipo);
        }
        return parser.parse(reader);
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (XMLStreamException e) {
                log.warn("Error al cerrar XMLStreamReader", e);
            }
        }
    }
}
