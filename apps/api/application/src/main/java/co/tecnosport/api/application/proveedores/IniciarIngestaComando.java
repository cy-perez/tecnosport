package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/**
 * @param objectKey la key que devolvió {@link SolicitarSubidaDeExportacion}
 * @param nombreArchivo con qué nombre lo eligió la persona, para reconocerlo en el historial; puede
 *     faltar
 */
public record IniciarIngestaComando(UUID proveedorId, String objectKey, String nombreArchivo) {

  /** Sin nombre: el de las pruebas y el de quien no lo sabe. */
  public IniciarIngestaComando(UUID proveedorId, String objectKey) {
    this(proveedorId, objectKey, null);
  }
}
