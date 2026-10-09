package co.tecnosport.api.application.proveedores;

/**
 * De dónde salen los mensajes de un lote.
 *
 * <p>Hoy la única implementación lee una exportación de chat guardada en el almacén. Un webhook de
 * la WhatsApp Cloud API no encaja aquí —no se le pide, él empuja— y por eso el reemplazo no es este
 * puerto sino {@link RegistrarMensajesDeProveedor}: es el caso de uso el que recibe {@link
 * MensajeCrudo}, y da igual quién los trajo.
 */
public interface FuenteDeMensajes {

  /**
   * El nombre del chat y todos los mensajes del archivo, en orden de aparición, de cualquier
   * remitente.
   *
   * @throws ExportacionIlegibleException si el archivo no es lo que se esperaba
   */
  ChatExportado leer(String referenciaArchivo);
}
