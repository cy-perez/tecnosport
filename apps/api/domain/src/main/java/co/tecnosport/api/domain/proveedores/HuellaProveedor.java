package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lo que hace que dos anuncios sean el mismo producto: el proveedor, el título normalizado y el
 * precio del proveedor, en un SHA-256.
 *
 * <p><b>El precio va dentro a propósito.</b> «Conjunto pantalón» a 60.000 en tela burda y «Conjunto
 * pantalón» a 45.000 en algodón son dos productos, y el título no los distingue. El costo es que un
 * cambio de precio sobre el mismo título no cae aquí; cae en la huella visual, que reconoce la
 * misma foto aunque el texto cambie.
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

  private static String sha256(String texto) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("La JVM no ofrece SHA-256.", e);
    }
  }
}
