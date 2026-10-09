package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.ChatDelZip;

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
   * @param chat cuál de los dos chats leer, cuando el proveedor los sube juntos en un zip; nulo
   *     cuando el archivo es de un solo chat
   * @throws ExportacionIlegibleException si el archivo no es lo que se esperaba, también si un zip
   *     de dos chats no trae la estructura acordada
   */
  ChatExportado leer(String referenciaArchivo, ChatDelZip chat);

  /** El de un archivo de un solo chat. */
  default ChatExportado leer(String referenciaArchivo) {
    return leer(referenciaArchivo, null);
  }
}
