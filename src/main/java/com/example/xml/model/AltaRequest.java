package com.example.xml.model;

/**
 * DTO for tipo = ALTA.
 *
 * Example XML:
 * <pre>{@code
 * <solicitud>
 *   <tipo>ALTA</tipo>
 *   <nombre>Juan García</nombre>
 *   <email>juan@example.com</email>
 *   <perfil>ADMIN</perfil>
 * </solicitud>
 * }</pre>
 */
public record AltaRequest(String nombre, String email, String perfil) {}
