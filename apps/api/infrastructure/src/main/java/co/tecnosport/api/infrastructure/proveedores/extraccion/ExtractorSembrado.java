package co.tecnosport.api.infrastructure.proveedores.extraccion;

import co.tecnosport.api.application.proveedores.ExtractorDeProductos;
import co.tecnosport.api.application.proveedores.ResultadoExtraccion;
import co.tecnosport.api.application.proveedores.TextoDePublicacion;
import co.tecnosport.api.application.proveedores.UsoDelExtractor;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.PatronDePrecio;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * El extractor cuando no hay clave de la API: lo que se puede sacar con expresiones regulares, y
 * nada más.
 *
 * <p>Existe para que {@code bootRun} y los recorridos de extremo a extremo funcionen sin hablar con
 * nadie, igual que {@code PublicadorSembrado} en difusión. <b>Deja la confianza en cero</b> y el
 * tipo en {@code OTRO} a propósito: todo borrador que salga de aquí lleva alertas y lo mira una
 * persona. Un sembrado que pareciera un extractor de verdad sería una mentira útil solo hasta el
 * primer producto publicado con un título a medias.
 */
public final class ExtractorSembrado implements ExtractorDeProductos {

  private static final Pattern AGOTADO =
      Pattern.compile("agotad|se acab|sin stock|no hay", Pattern.CASE_INSENSITIVE);
  private static final Pattern ADORNO =
      Pattern.compile(
          "^(nueva colecci[oó]n|nuevamente disponible|gama alta)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern NO_LETRAS = Pattern.compile("[^\\p{L}\\p{N} ]");

  @Override
  public ResultadoExtraccion extraer(TextoDePublicacion texto) {
    String completo = texto.completo();
    Optional<Dinero> precio = PatronDePrecio.extraer(completo);
    String titulo = primeraLineaConSentido(completo);
    ProductoExtraido producto =
        new ProductoExtraido(
            precio.isPresent(),
            AGOTADO.matcher(completo).find(),
            titulo,
            texto.lineaDelProveedor(),
            TipoProductoProveedor.OTRO,
            precio.orElse(null),
            Tallas.desconocida(),
            null,
            List.of(),
            null,
            null,
            null,
            false,
            BigDecimal.ZERO,
            "Extraído sin modelo: solo el precio y la primera línea. Revisar todo.");
    String jsonCrudo =
        "{\"sembrado\":true,\"titulo\":"
            + (titulo == null ? "null" : "\"" + titulo.replace("\"", "'") + "\"")
            + ",\"precio_proveedor_cop\":"
            + precio.map(p -> p.valor().toPlainString()).orElse("null")
            + "}";
    return new ResultadoExtraccion(producto, jsonCrudo, new UsoDelExtractor("sembrado", 0, 0, 0));
  }

  private static String primeraLineaConSentido(String texto) {
    for (String linea : texto.split("\\r?\\n")) {
      String limpia = NO_LETRAS.matcher(linea).replaceAll("").strip().replaceAll("\\s+", " ");
      if (limpia.isEmpty()
          || PatronDePrecio.tienePrecio(linea)
          || ADORNO.matcher(limpia).matches()
          || limpia.toLowerCase(Locale.ROOT).startsWith("http")) {
        continue;
      }
      return limpia;
    }
    return null;
  }
}
