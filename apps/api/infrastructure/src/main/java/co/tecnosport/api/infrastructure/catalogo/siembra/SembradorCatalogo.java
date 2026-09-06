package co.tecnosport.api.infrastructure.catalogo.siembra;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.infrastructure.catalogo.AtributoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.CategoriaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.ImagenProductoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.MarcaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.ProductoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.SetRotacionJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.VarianteAtributoValorJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.VarianteJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.entidad.AtributoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.SetRotacionJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteAtributoValorJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Catálogo de ejemplo para desarrollo local. Nunca corre en producción: solo se activa con el
 * perfil "local", que {@code bootstrap/build.gradle.kts} fija por defecto en la tarea {@code
 * bootRun} y en ningún otro lado.
 *
 * <p>Construye entidades JPA directamente, sin pasar por las fábricas de dominio: no hay todavía
 * ningún caso de uso de escritura real que necesite un mapeador dominio→JPA, y construirlo solo
 * para este sembrador sería anticipar la Fase 4 sin un consumidor.
 */
@Component
@Profile("local")
@Order(1)
public class SembradorCatalogo implements ApplicationRunner {

  /** El objetivo de docs/10-captura-360.md: donde el arrastre empieza a sentirse continuo. */
  private static final int FOTOGRAMAS_OBJETIVO = 8;

  /** El mínimo publicable de la misma tabla: cada arrastre salta 90 grados. */
  private static final int FOTOGRAMAS_MINIMOS = 4;

  private final MarcaJpaRepository marcas;
  private final CategoriaJpaRepository categorias;
  private final AtributoJpaRepository atributos;
  private final ProductoJpaRepository productos;
  private final VarianteJpaRepository variantes;
  private final VarianteAtributoValorJpaRepository valoresAtributo;
  private final ImagenProductoJpaRepository imagenes;
  private final SetRotacionJpaRepository setsRotacion;

  public SembradorCatalogo(
      MarcaJpaRepository marcas,
      CategoriaJpaRepository categorias,
      AtributoJpaRepository atributos,
      ProductoJpaRepository productos,
      VarianteJpaRepository variantes,
      VarianteAtributoValorJpaRepository valoresAtributo,
      ImagenProductoJpaRepository imagenes,
      SetRotacionJpaRepository setsRotacion) {
    this.marcas = marcas;
    this.categorias = categorias;
    this.atributos = atributos;
    this.productos = productos;
    this.variantes = variantes;
    this.valoresAtributo = valoresAtributo;
    this.imagenes = imagenes;
    this.setsRotacion = setsRotacion;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (productos.count() > 0) {
      return;
    }

    Instant ahora = Instant.now();

    MarcaJpaEntity tecnosport = guardarMarca("TecnoSport", ahora);
    MarcaJpaEntity underTrail = guardarMarca("Under Trail", ahora);

    CategoriaJpaEntity ropaDeportiva =
        guardarCategoria("Ropa deportiva", "ropa-deportiva", "ROPA_Y_CALZADO", ahora);
    CategoriaJpaEntity calzadoDeportivo =
        guardarCategoria("Calzado deportivo", "calzado-deportivo", "ROPA_Y_CALZADO", ahora);
    CategoriaJpaEntity bolsos = guardarCategoria("Bolsos", "bolsos", "BOLSOS", ahora);
    CategoriaJpaEntity celulares = guardarCategoria("Celulares", "celulares", "CELULARES", ahora);

    AtributoJpaEntity tallaRopa =
        guardarAtributo("Talla", "TEXTO", List.of("S", "M", "L", "XL"), ahora);
    AtributoJpaEntity tallaCalzado = guardarAtributo("Talla calzado", "NUMERO", List.of(), ahora);
    AtributoJpaEntity color = guardarAtributo("Color", "COLOR", List.of(), ahora);
    AtributoJpaEntity genero =
        guardarAtributo("Género", "TEXTO", List.of("Hombre", "Mujer", "Unisex"), ahora);
    AtributoJpaEntity capacidad =
        guardarAtributo("Capacidad", "TEXTO", List.of("18L", "25L", "35L"), ahora);
    AtributoJpaEntity almacenamiento =
        guardarAtributo("Almacenamiento", "TEXTO", List.of("64GB", "128GB", "256GB"), ahora);
    AtributoJpaEntity ram = guardarAtributo("RAM", "TEXTO", List.of("4GB", "6GB", "8GB"), ahora);
    // "12" meses es un valor de siembra de desarrollo, no una política de garantía real —
    // docs/02-modelo-datos.md exige el atributo, pero el valor lo define el negocio cuando exista
    // un panel para cargarlo (Fase 4). Mismo criterio que los precios y las fotos de picsum.photos.
    AtributoJpaEntity garantia = guardarAtributo("Garantía", "NUMERO", List.of(), ahora);

    ProductoJpaEntity camiseta =
        guardarProductoPublicado(
            "Camiseta running Dry-Fit",
            "camiseta-running-dry-fit",
            tecnosport,
            ropaDeportiva,
            ahora);
    guardarVariante(
        camiseta,
        "TS-CAM-AZ-M",
        "89900",
        "0.19",
        12,
        ahora,
        List.of(
            valor(tallaRopa, "M", null),
            valor(color, "Azul marino", "#1E3A8A"),
            valor(genero, "Unisex", null)));
    guardarVariante(
        camiseta,
        "TS-CAM-NG-L",
        "89900",
        "0.19",
        8,
        ahora,
        List.of(
            valor(tallaRopa, "L", null),
            valor(color, "Negro", "#111111"),
            valor(genero, "Unisex", null)));

    ProductoJpaEntity tenis =
        guardarProductoPublicado(
            "Tenis trail runner", "tenis-trail-runner", underTrail, calzadoDeportivo, ahora);
    guardarVariante(
        tenis,
        "UT-TEN-40",
        "349900",
        "0.19",
        5,
        ahora,
        List.of(
            valor(tallaCalzado, "40", null),
            valor(color, "Gris grafito", "#374151"),
            valor(genero, "Hombre", null)));
    guardarVariante(
        tenis,
        "UT-TEN-38.5",
        "349900",
        "0.19",
        4,
        ahora,
        List.of(
            valor(tallaCalzado, "38.5", null),
            valor(color, "Coral", "#FB7185"),
            valor(genero, "Mujer", null)));

    ProductoJpaEntity morral =
        guardarProductoPublicado(
            "Morral urbano 25L", "morral-urbano-25l", tecnosport, bolsos, ahora);
    guardarVariante(
        morral,
        "TS-MOR-NG-25",
        "159900",
        "0.19",
        10,
        ahora,
        List.of(valor(capacidad, "25L", null), valor(color, "Negro", "#111111")));
    guardarVariante(
        morral,
        "TS-MOR-AZ-25",
        "159900",
        "0.19",
        6,
        ahora,
        List.of(valor(capacidad, "25L", null), valor(color, "Azul marino", "#1E3A8A")));

    ProductoJpaEntity celular =
        guardarProductoPublicado(
            "Celular TecnoSport Aurora", "celular-tecnosport-aurora", tecnosport, celulares, ahora);
    guardarVariante(
        celular,
        "TS-CEL-AUR-128",
        "1299900",
        "0.19",
        3,
        ahora,
        List.of(
            valor(almacenamiento, "128GB", null),
            valor(ram, "8GB", null),
            valor(color, "Negro", "#111111"),
            valor(garantia, "12", null)));
    guardarVariante(
        celular,
        "TS-CEL-AUR-256",
        "1499900",
        "0.19",
        2,
        ahora,
        List.of(
            valor(almacenamiento, "256GB", null),
            valor(ram, "8GB", null),
            valor(color, "Azul marino", "#1E3A8A"),
            valor(garantia, "12", null)));

    guardarImagenPrincipal(camiseta, ahora);
    guardarImagenPrincipal(tenis, ahora);
    guardarImagenPrincipal(morral, ahora);
    guardarImagenPrincipal(celular, ahora);

    guardarGaleria(camiseta, ahora);
    guardarGaleria(tenis, ahora);
    guardarGaleria(morral, ahora);
    guardarGaleria(celular, ahora);

    // Dos productos con set de rotación, de distinto tamaño, y los otros dos sin ninguno. Los tres
    // casos hacen falta en desarrollo: la ficha con visor, la ficha sin visor, y sobre todo pasar
    // de un set a otro dentro de la misma sesión, que es el camino donde vivían la carrera de la
    // precarga y el reinicio del fotograma (docs/09-plan-de-arranque.md, Fase 5).
    guardarSetRotacion(tenis, FOTOGRAMAS_OBJETIVO, ahora);
    guardarSetRotacion(morral, FOTOGRAMAS_MINIMOS, ahora);
  }

  private MarcaJpaEntity guardarMarca(String nombre, Instant ahora) {
    return marcas.save(new MarcaJpaEntity(GeneradorIdentificador.nuevo(), nombre, ahora));
  }

  private CategoriaJpaEntity guardarCategoria(
      String nombre, String slug, String linea, Instant ahora) {
    return categorias.save(
        new CategoriaJpaEntity(GeneradorIdentificador.nuevo(), nombre, slug, linea, ahora));
  }

  private AtributoJpaEntity guardarAtributo(
      String nombre, String tipo, List<String> valoresPermitidos, Instant ahora) {
    return atributos.save(
        new AtributoJpaEntity(
            GeneradorIdentificador.nuevo(), nombre, tipo, valoresPermitidos, ahora));
  }

  private ProductoJpaEntity guardarProductoPublicado(
      String nombre,
      String slug,
      MarcaJpaEntity marca,
      CategoriaJpaEntity categoria,
      Instant ahora) {
    return productos.save(
        new ProductoJpaEntity(
            GeneradorIdentificador.nuevo(),
            nombre,
            slug,
            "",
            marca.getId(),
            categoria.getId(),
            "PUBLICADO",
            ahora,
            ahora));
  }

  private record ValorPendiente(AtributoJpaEntity atributo, String valor, String colorHex) {}

  private ValorPendiente valor(AtributoJpaEntity atributo, String valor, String colorHex) {
    return new ValorPendiente(atributo, valor, colorHex);
  }

  private void guardarVariante(
      ProductoJpaEntity producto,
      String sku,
      String precio,
      String tasaIva,
      int existencia,
      Instant ahora,
      List<ValorPendiente> valores) {
    VarianteJpaEntity variante =
        variantes.save(
            new VarianteJpaEntity(
                GeneradorIdentificador.nuevo(),
                producto.getId(),
                sku,
                new BigDecimal(precio),
                new BigDecimal(tasaIva),
                existencia,
                null,
                "ACTIVA",
                ahora));

    for (ValorPendiente pendiente : valores) {
      valoresAtributo.save(
          new VarianteAtributoValorJpaEntity(
              GeneradorIdentificador.nuevo(),
              variante.getId(),
              pendiente.atributo().getId(),
              pendiente.valor(),
              pendiente.colorHex()));
    }
  }

  private void guardarImagenPrincipal(ProductoJpaEntity producto, Instant ahora) {
    String url = "https://picsum.photos/seed/" + producto.getSlug() + "/800/600";
    imagenes.save(
        new ImagenProductoJpaEntity(
            GeneradorIdentificador.nuevo(),
            producto.getId(),
            null,
            null,
            "PRINCIPAL",
            0,
            url,
            url,
            800,
            600,
            120_000,
            "seed-" + producto.getSlug(),
            producto.getNombre(),
            producto.getNombre(),
            ahora));
  }

  /**
   * Set de rotación publicado, con el número de fotogramas que pida quien llama — la tabla de
   * docs/10-captura-360.md contempla 4, 8 y 16. Las imágenes son de picsum.photos y no son un giro
   * real: cada fotograma es una foto distinta, así que sirven para ejercitar el visor —el índice
   * circular, la precarga, el arrastre— y no para juzgar cómo se ve una rotación de verdad. Eso
   * llega con el asistente de captura.
   */
  private void guardarSetRotacion(ProductoJpaEntity producto, int fotogramas, Instant ahora) {
    UUID setId = GeneradorIdentificador.nuevo();
    setsRotacion.save(
        new SetRotacionJpaEntity(
            setId,
            producto.getId(),
            null,
            fotogramas,
            // PUBLICADO porque la ficha pública solo expone la rotación en ese estado
            // (MapeadorRespuestasCatalogo, docs/03-api.md).
            "PUBLICADO",
            "siembra",
            ahora,
            "siembra",
            "siembra"));

    for (int orden = 0; orden < fotogramas; orden++) {
      String url =
          "https://picsum.photos/seed/" + producto.getSlug() + "-360-" + orden + "/1000/1000";
      imagenes.save(
          new ImagenProductoJpaEntity(
              GeneradorIdentificador.nuevo(),
              producto.getId(),
              null,
              setId,
              "ROTACION",
              orden,
              url,
              url,
              1000,
              1000,
              180_000,
              "seed-" + producto.getSlug() + "-360-" + orden,
              producto.getNombre(),
              producto.getNombre(),
              ahora));
    }
  }

  private void guardarGaleria(ProductoJpaEntity producto, Instant ahora) {
    for (int orden = 0; orden < 2; orden++) {
      String url =
          "https://picsum.photos/seed/" + producto.getSlug() + "-galeria-" + orden + "/800/600";
      imagenes.save(
          new ImagenProductoJpaEntity(
              GeneradorIdentificador.nuevo(),
              producto.getId(),
              null,
              null,
              "GALERIA",
              orden,
              url,
              url,
              800,
              600,
              120_000,
              "seed-" + producto.getSlug() + "-galeria-" + orden,
              producto.getNombre(),
              producto.getNombre(),
              ahora));
    }
  }
}
