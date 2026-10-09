package co.tecnosport.api.application.proveedores;

import java.util.List;
import java.util.Objects;

/**
 * Lo que trae una exportación: el nombre del chat y sus mensajes.
 *
 * <p>El nombre es lo que deja saber si la exportación es el chat de caballero de un proveedor que
 * publica en dos (9 de octubre de 2026); el archivo que sube el panel se guarda con un nombre
 * aleatorio, así que tiene que salir de adentro.
 *
 * @param nombre el del chat, tal como lo escribe WhatsApp; nulo si la exportación no lo dice
 * @param mensajes todos los del archivo, en orden de aparición, de cualquier remitente
 */
public record ChatExportado(String nombre, List<MensajeCrudo> mensajes) {

  public ChatExportado {
    mensajes = List.copyOf(Objects.requireNonNull(mensajes, "Un chat trae su lista de mensajes."));
  }
}
