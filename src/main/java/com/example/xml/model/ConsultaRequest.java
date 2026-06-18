package com.example.xml.model;

/**
 * DTO for tipo = CONSULTA.
 *
 * Example XML:
 * <pre>{@code
 * <solicitud>
 *   <tipo>CONSULTA</tipo>
 *   <id>12345</id>
 *   <filtro>activo</filtro>
 * </solicitud>
 * }</pre>
 */
public record ConsultaRequest(String id, String filtro) {}
