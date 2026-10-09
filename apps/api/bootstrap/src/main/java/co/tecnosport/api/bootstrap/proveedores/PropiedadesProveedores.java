package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.TopesDeGanancia;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code tecnosport.proveedores.*}: la ingesta por WhatsApp.
 *
 * <p>{@code bucket} es el privado de los originales del proveedor, distinto del de imágenes y
 * también uno por ambiente (ADR-0058). Los topes de la exportación son parámetros técnicos y no
 * datos de negocio: el primero es lo que cabe abrir en memoria en una instancia de Cloud Run y el
 * segundo es la barandilla contra un zip que infle. La cola es corta a propósito: un lote tarda
 * minutos y el panel lo usa una persona.
 */
@ConfigurationProperties(prefix = "tecnosport.proveedores")
public record PropiedadesProveedores(
    String bucket,
    long minutosUrlFirmada,
    long exportacionMaximaBytes,
    long descomprimidoMaximoBytes,
    long fotoMaximaBytes,
    int colaDeIngestas,
    Map<LineaCatalogo, BigDecimal> margenPorLinea,
    Ganancia ganancia,
    Huella huella,
    Duration ventanaDisponibilidad,
    JobExpiracion jobExpiracion,
    Tecnologia tecnologia) {

  /**
   * El proveedor de tecnología, que manda listas de precios en vez de publicaciones (ADR-0075).
   *
   * @param existenciaPorVariante las unidades que se ofrecen de cada color de cada configuración
   *     que trae la lista: se vende con lo que el proveedor dice tener, y se repone con cada lista
   *     (decisión del negocio, 8 de octubre de 2026)
   * @param ventanaDisponibilidad cuánto vale una lista: pasado eso sin otra, lo que trajo se
   *     oculta. Distinta de la de los mensajes porque las listas llegan cada varios días
   */
  public record Tecnologia(int existenciaPorVariante, Duration ventanaDisponibilidad) {
    public Tecnologia {
      if (existenciaPorVariante < 1) {
        throw new IllegalStateException(
            "tecnosport.proveedores.tecnologia.existencia-por-variante es por lo menos 1.");
      }
      if (ventanaDisponibilidad == null
          || ventanaDisponibilidad.isNegative()
          || ventanaDisponibilidad.isZero()) {
        throw new IllegalStateException(
            "tecnosport.proveedores.tecnologia.ventana-disponibilidad debe ser una duración"
                + " positiva.");
      }
    }
  }

  /**
   * Cada cuánto corre el job que oculta lo vencido, y cuánto espera tras arrancar. El intervalo se
   * acorta a minutos para probar; {@code habilitado} lo apaga sin desplegar.
   */
  public record JobExpiracion(boolean habilitado, Duration intervalo, Duration retrasoInicial) {
    public JobExpiracion {
      if (intervalo == null || intervalo.isNegative() || intervalo.isZero()) {
        throw new IllegalStateException(
            "tecnosport.proveedores.job-expiracion.intervalo debe ser una duración positiva.");
      }
      if (retrasoInicial == null || retrasoInicial.isNegative()) {
        throw new IllegalStateException(
            "tecnosport.proveedores.job-expiracion.retraso-inicial no puede ser negativo.");
      }
    }
  }

  /**
   * Lo menos y lo más que el precio sugerido le gana a cada unidad, en pesos enteros. Datos del
   * negocio (3 de octubre de 2026), no parámetros técnicos.
   */
  public record Ganancia(long minima, long maxima) {
    public Ganancia {
      if (minima < 0 || maxima < minima) {
        throw new IllegalStateException(
            "tecnosport.proveedores.ganancia: la mínima no puede ser negativa ni superar la"
                + " máxima.");
      }
    }

    public TopesDeGanancia aTopes() {
      return new TopesDeGanancia(Dinero.deCop(minima), Dinero.deCop(maxima));
    }
  }

  /** La distancia de Hamming hasta la que dos fotos son la misma. */
  public record Huella(int umbralHamming) {
    public Huella {
      if (umbralHamming < 0 || umbralHamming > 64) {
        throw new IllegalStateException(
            "tecnosport.proveedores.huella.umbral-hamming va de 0 a 64.");
      }
    }
  }

  public PropiedadesProveedores {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException("tecnosport.proveedores.bucket no puede estar vacío.");
    }
    if (minutosUrlFirmada <= 0) {
      throw new IllegalStateException(
          "tecnosport.proveedores.minutos-url-firmada debe ser mayor que cero.");
    }
    if (exportacionMaximaBytes <= 0) {
      throw new IllegalStateException(
          "tecnosport.proveedores.exportacion-maxima-bytes debe ser mayor que cero.");
    }
    if (descomprimidoMaximoBytes < exportacionMaximaBytes) {
      throw new IllegalStateException(
          "tecnosport.proveedores.descomprimido-maximo-bytes no puede ser menor que el tope del"
              + " zip: un zip nunca infla a menos de lo que pesa.");
    }
    if (fotoMaximaBytes <= 0) {
      throw new IllegalStateException(
          "tecnosport.proveedores.foto-maxima-bytes debe ser mayor que cero.");
    }
    if (colaDeIngestas <= 0) {
      throw new IllegalStateException(
          "tecnosport.proveedores.cola-de-ingestas debe ser mayor que cero.");
    }
    if (margenPorLinea == null) {
      throw new IllegalStateException("Falta tecnosport.proveedores.margen-por-linea.");
    }
    // Solo las líneas que entran por la exportación del chat llevan factor de margen: el precio de
    // la tecnología lo decide una persona en el panel, con el precio de mercado a la vista.
    for (LineaCatalogo linea : Proveedor.LINEAS_POR_EXPORTACION) {
      BigDecimal factor = margenPorLinea.get(linea);
      if (factor == null || factor.compareTo(BigDecimal.ONE) < 0) {
        throw new IllegalStateException(
            "tecnosport.proveedores.margen-por-linea."
                + linea
                + " tiene que existir y ser al menos 1: es lo que multiplica el precio del"
                + " proveedor.");
      }
    }
    if (ganancia == null) {
      throw new IllegalStateException("Falta tecnosport.proveedores.ganancia.");
    }
    if (huella == null) {
      throw new IllegalStateException("Falta tecnosport.proveedores.huella.umbral-hamming.");
    }
    if (ventanaDisponibilidad == null
        || ventanaDisponibilidad.isNegative()
        || ventanaDisponibilidad.isZero()) {
      throw new IllegalStateException(
          "tecnosport.proveedores.ventana-disponibilidad debe ser una duración positiva.");
    }
    if (jobExpiracion == null) {
      throw new IllegalStateException("Falta tecnosport.proveedores.job-expiracion.");
    }
    if (tecnologia == null) {
      throw new IllegalStateException("Falta tecnosport.proveedores.tecnologia.");
    }
  }
}
