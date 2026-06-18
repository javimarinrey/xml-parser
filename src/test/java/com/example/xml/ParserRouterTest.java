package com.example.xml;

import com.example.xml.exception.UnknownTipoException;
import com.example.xml.exception.XmlParseException;
import com.example.xml.model.AltaRequest;
import com.example.xml.model.ConsultaRequest;
import com.example.xml.model.ModificacionRequest;
import com.example.xml.parser.AltaParser;
import com.example.xml.parser.ConsultaParser;
import com.example.xml.parser.ModificacionParser;
import com.example.xml.parser.XmlParser;
import com.example.xml.router.ParserRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserRouterTest {

    private ParserRouter router;

    @BeforeEach
    void setUp() {
        List<XmlParser<?>> parsers = List.of(
                new ConsultaParser(),
                new AltaParser(),
                new ModificacionParser()
        );
        router = new ParserRouter(parsers);
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CONSULTA: todos los campos parseados correctamente")
    void consulta_allFieldsMapped() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <solicitud>
                    <tipo>CONSULTA</tipo>
                    <id>99</id>
                    <filtro>pendiente</filtro>
                </solicitud>
                """;

        ConsultaRequest result = (ConsultaRequest) router.dispatch(stream(xml));

        assertEquals("99", result.id());
        assertEquals("pendiente", result.filtro());
    }

    @Test
    @DisplayName("ALTA: todos los campos parseados correctamente")
    void alta_allFieldsMapped() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <solicitud>
                    <tipo>ALTA</tipo>
                    <nombre>María López</nombre>
                    <email>maria@example.com</email>
                    <perfil>USER</perfil>
                </solicitud>
                """;

        AltaRequest result = (AltaRequest) router.dispatch(stream(xml));

        assertEquals("María López", result.nombre());
        assertEquals("maria@example.com", result.email());
        assertEquals("USER", result.perfil());
    }

    @Test
    @DisplayName("MODIFICACION: todos los campos parseados correctamente")
    void modificacion_allFieldsMapped() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <solicitud>
                    <tipo>MODIFICACION</tipo>
                    <id>42</id>
                    <campo>email</campo>
                    <valor>nuevo@example.com</valor>
                </solicitud>
                """;

        ModificacionRequest result = (ModificacionRequest) router.dispatch(stream(xml));

        assertEquals("42", result.id());
        assertEquals("email", result.campo());
        assertEquals("nuevo@example.com", result.valor());
    }

    @Test
    @DisplayName("Campos antes de <tipo> son ignorados sin error")
    void camposAntesDeTipo_ignorados() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <solicitud>
                    <metadata>algo</metadata>
                    <tipo>CONSULTA</tipo>
                    <id>1</id>
                    <filtro>activo</filtro>
                </solicitud>
                """;

        assertDoesNotThrow(() -> router.dispatch(stream(xml)));
    }

    // ── Error cases ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Tipo desconocido lanza UnknownTipoException")
    void tipoDesconocido_throwsUnknownTipo() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <solicitud>
                    <tipo>BAJA</tipo>
                    <id>1</id>
                </solicitud>
                """;

        UnknownTipoException ex = assertThrows(
                UnknownTipoException.class,
                () -> router.dispatch(stream(xml))
        );
        assertEquals("BAJA", ex.getTipo());
    }

    @Test
    @DisplayName("XML sin tag <tipo> lanza XmlParseException")
    void sinTipo_throwsXmlParseException() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <solicitud>
                    <id>1</id>
                </solicitud>
                """;

        assertThrows(XmlParseException.class, () -> router.dispatch(stream(xml)));
    }

    @Test
    @DisplayName("XML malformado lanza XmlParseException")
    void xmlMalformado_throwsXmlParseException() {
        String xml = "<?xml version=\"1.0\"?><solicitud><tipo>CONSULTA</tipo><id>1</MISSING>";
        assertThrows(XmlParseException.class, () -> router.dispatch(stream(xml)));
    }

    @Test
    @DisplayName("Tag <tipo> vacío lanza XmlParseException")
    void tipoVacio_throwsXmlParseException() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <solicitud>
                    <tipo>   </tipo>
                    <id>1</id>
                </solicitud>
                """;

        assertThrows(XmlParseException.class, () -> router.dispatch(stream(xml)));
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private static InputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
