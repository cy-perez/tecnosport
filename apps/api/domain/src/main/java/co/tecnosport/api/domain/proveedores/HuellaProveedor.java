package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * La identidad de un producto de proveedor, en un SHA-256. Hay cuatro, según lo que se identifica.
 *
 * <ul>
 *   <li>{@link #calcular}: <b>el texto del anuncio</b> —proveedor, título normalizado y precio—. El
 *       precio va dentro a propósito: «Conjunto pantalón» a 60.000 en tela burda y a 45.000 en
 *       algodón son dos productos. Desde el 9 de octubre de 2026 ya no identifica un producto, solo
 *       un anuncio repetido, y eso junto con la foto (ver abajo).
 *   <li>{@link #deAnuncio}: <b>la prenda de un anuncio sin código</b> —el texto y además la fecha
 *       del mensaje—. La Riverah repite el mismo texto con otra prenda: el «Busito manga larga» a
 *       58.000 salió azul a las 12:06 y gris a las 19:32 del 8 de octubre. Con la huella del texto
 *       eran el mismo producto, el segundo se descartaba y no podía aprobarse. Lo que reconoce la
 *       misma prenda otro día es su foto, la huella visual.
 *   <li>{@link #deReferencia}: <b>la prenda con código del proveedor</b> —«(Q339)»—. El código sí
 *       la identifica, a cualquier hora y a cualquier precio, y por eso manda sobre las otras.
 *   <li>{@link #deModelo}: un modelo de tecnología de la lista de precios.
 * </ul>
 */
public record HuellaProveedor(String valor) {

  private static final Pattern FORMATO = Pattern.compile("^[0-9a-f]{64}$");

  public HuellaProveedor {
    if (valor == null || !FORMATO.matcher(valor).matches()) {
      throw new ExcepcionDeDominio("La huella de proveedor es un SHA-256 en hexadecimal.");
    }
  }

  public static HuellaProveedor calcular(UUID proveedorId, String titulo, Dinero precioProveedor) {
    if (proveedorId == null || precioProveedor == null) {
      throw new ExcepcionDeDominio("La huella necesita proveedor y precio.");
    }
    String tituloNormalizado = NormalizadorDeTitulo.normalizar(titulo);
    if (tituloNormalizado.isEmpty()) {
      throw new ExcepcionDeDominio("La huella necesita un título con alguna letra o número.");
    }
    String material =
        proveedorId + "|" + tituloNormalizado + "|" + precioProveedor.valor().toPlainString();
    return new HuellaProveedor(sha256(material));
  }

  /**
   * La prenda de un anuncio sin código: el texto y la fecha del mensaje que lo publicó. Dos
   * anuncios con el mismo texto a distinta hora son dos productos (9 de octubre de 2026).
   */
  public static HuellaProveedor deAnuncio(
      UUID proveedorId, String titulo, Dinero precioProveedor, Instant publicadoEn) {
    if (publicadoEn == null) {
      throw new ExcepcionDeDominio("La huella de un anuncio necesita su fecha.");
    }
    HuellaProveedor delTexto = calcular(proveedorId, titulo, precioProveedor);
    return new HuellaProveedor(sha256(delTexto.valor() + "|anuncio|" + publicadoEn));
  }

  /** La prenda que el proveedor marca con un código: el código manda, sin título ni precio. */
  public static HuellaProveedor deReferencia(UUID proveedorId, String codigo) {
    if (proveedorId == null || codigo == null || codigo.isBlank()) {
      throw new ExcepcionDeDominio("La huella de una referencia necesita proveedor y código.");
    }
    return new HuellaProveedor(sha256(proveedorId + "|referencia|" + codigo.strip()));
  }

  /**
   * La huella de un modelo de tecnología: el proveedor y el id del modelo que decide la skill de
   * listas, <b>sin precio</b>. Al revés que en las prendas, aquí el título sí distingue —«Galaxy
   * A17 5G» es uno solo, cueste lo que cueste esta semana—, y el costo cambia con cada lista: con
   * el precio dentro, cada lista nueva crearía un producto nuevo.
   */
  public static HuellaProveedor deModelo(UUID proveedorId, String idModelo) {
    if (proveedorId == null || idModelo == null || idModelo.isBlank()) {
      throw new ExcepcionDeDominio("La huella de un modelo necesita proveedor e id del modelo.");
    }
    return new HuellaProveedor(sha256(proveedorId + "|modelo|" + idModelo.strip()));
  }

  static String sha256(String texto) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("La JVM no ofrece SHA-256.", e);
    }
  }
}
