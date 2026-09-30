package co.tecnosport.api.domain.proveedores;

/**
 * Qué trae un mensaje. Tres valores y no los doce tipos de WhatsApp, porque para armar un producto
 * solo importan dos cosas: si hay texto que leer y si hay una foto que publicar. Un audio, un
 * sticker o un documento son {@code OTRO}: se registran para que el lote cuadre y no se usan.
 */
public enum TipoMensaje {
  TEXTO,
  IMAGEN,
  OTRO
}
