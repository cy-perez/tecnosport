package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Lo que hace único a un mensaje del proveedor, venga de donde venga.
 *
 * <p>La API de WhatsApp da un identificador por mensaje y ese se usa tal cual. Una exportación de
 * chat no da ninguno, así que se fabrica uno determinista con lo que la exportación sí trae:
 * proveedor, fecha y contenido. <b>Es lo que hace inofensivo subir dos veces el mismo archivo</b>:
 * el segundo intento produce los mismos identificadores y el repositorio los reconoce.
 *
 * <p>Con una limitación que conviene saber: la exportación de Android escribe la hora sin segundos,
 * así que dos mensajes con el mismo texto dentro del mismo minuto colapsan en uno. En un chat de
 * proveedor eso es un reenvío, no dos productos.
 */
public record IdExternoDeMensaje(String valor) {

  public static final int LARGO_MAXIMO = 200;

  public IdExternoDeMensaje {
    if (valor == null || valor.isBlank()) {
      throw new ExcepcionDeDominio("El id externo de un mensaje no puede estar vacío.");
    }
    valor = valor.strip();
    if (valor.length() > LARGO_MAXIMO) {
      throw new ExcepcionDeDominio(
          "El id externo de un mensaje no puede pasar de " + LARGO_MAXIMO + " caracteres.");
    }
  }

  /**
   * Para un mensaje de exportación: SHA-256 de proveedor, instante y contenido. El contenido es el
   * texto en un mensaje de texto y el nombre del archivo en un adjunto, porque el adjunto no tiene
   * otro texto que lo distinga de la foto siguiente.
   */
  public static IdExternoDeMensaje deExportacion(
      UUID proveedorId, Instant enviadoEn, String contenido) {
    if (proveedorId == null || enviadoEn == null) {
      throw new ExcepcionDeDominio("El id de exportación necesita proveedor y fecha.");
    }
    String material = proveedorId + "|" + enviadoEn.getEpochSecond() + "|" + nulo(contenido);
    return new IdExternoDeMensaje(sha256(material));
  }

  private static String nulo(String contenido) {
    return contenido == null ? "" : contenido;
  }

  private static String sha256(String texto) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 está en toda JVM; si faltara no habría forma de seguir.
      throw new IllegalStateException("La JVM no ofrece SHA-256.", e);
    }
  }
}
