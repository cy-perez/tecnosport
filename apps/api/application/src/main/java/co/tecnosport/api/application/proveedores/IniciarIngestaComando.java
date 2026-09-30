package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/**
 * @param objectKey la key que devolvió {@link SolicitarSubidaDeExportacion}
 */
public record IniciarIngestaComando(UUID proveedorId, String objectKey) {}
