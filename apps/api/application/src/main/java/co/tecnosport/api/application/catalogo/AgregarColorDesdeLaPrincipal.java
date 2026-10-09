package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.AtributoInvalidoException;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.ImagenProductoInvalidaException;
import co.tecnosport.api.domain.catalogo.MuestraDeColor;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.ProductoConColorException;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Sku;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * La foto principal muestra un color en que el producto todavía no se vende: se empieza a vender en
 * él y la foto queda marcada con ese color (8 de octubre de 2026).
 *
 * <p>El color de una foto es la variante que retrata —la ficha la enseña cuando se elige ese
 * color—, así que un color nuevo son variantes nuevas: una por talla, con el precio y el empaque de
 * la que ya existe en esa talla y las unidades que dice quien la carga, que el panel pide talla por
 * talla. Sin existencia inventada: lo que no se cuenta no se vende.
 *
 * <p>Un producto sin ningún color —un borrador aprobado sin tono— es otro caso: sus variantes no
 * tienen color, y agregarle unas rojas dejaría en la misma ficha tallas «sin color» al lado de
 * tallas rojas. Ahí las que ya existen toman el color, con la existencia que ya tienen.
 *
 * <p>Toca el producto, sus variantes y el inventario: quien llama lo envuelve en una transacción,
 * como a {@code AprobarBorrador}.
 */
public final class AgregarColorDesdeLaPrincipal {

  private static final String ATRIBUTO_COLOR = "color";

  /** {@code variante.sku} es {@code varchar(60)}: el sufijo del color no se come el SKU. */
  private static final int LARGO_MAXIMO_DEL_SUFIJO = 20;

  private final RepositorioProductos repositorioProductos;
  private final RepositorioAtributos repositorioAtributos;
  private final RepositorioPaletaDeColores paleta;
  private final AgregarVariante agregarVariante;

  public AgregarColorDesdeLaPrincipal(
      RepositorioProductos repositorioProductos,
      RepositorioAtributos repositorioAtributos,
      RepositorioPaletaDeColores paleta,
      AgregarVariante agregarVariante) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioAtributos = Objects.requireNonNull(repositorioAtributos);
    this.paleta = Objects.requireNonNull(paleta);
    this.agregarVariante = Objects.requireNonNull(agregarVariante);
  }

  /**
   * @return la foto principal, ya marcada con el color nuevo
   */
  public ImagenProducto ejecutar(AgregarColorDesdeLaPrincipalComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Producto producto = cargar(comando.productoId());
    if (producto.imagenPrincipal().isEmpty()) {
      throw new ImagenProductoInvalidaException(
          "'" + producto.nombre() + "' no tiene foto principal.");
    }
    String color = comando.color().strip();
    MuestraDeColor muestra =
        MuestraDeColor.componer(color, paleta.listarTodos())
            .orElseThrow(
                () ->
                    new AtributoInvalidoException("'" + color + "' no es un color de la paleta."));
    Atributo atributoColor = atributoColor();

    UUID varianteDeLaFoto;
    if (!producto.tieneColor()) {
      ValorAtributo valor = ValorAtributo.deColor(atributoColor, color, muestra);
      List<Variante> coloreadas = producto.colorearVariantesSinColor(valor);
      for (Variante variante : coloreadas) {
        repositorioProductos.agregarAtributoAVariante(variante.id(), valor);
      }
      varianteDeLaFoto = coloreadas.get(0).id();
    } else {
      if (producto.tieneElColor(color)) {
        throw new ProductoConColorException(
            "'" + producto.nombre() + "' ya se vende en " + color + ": se elige de sus colores.");
      }
      varianteDeLaFoto = crearVariantesDelColor(producto, comando, color, atributoColor, muestra);
      // `AgregarVariante` carga el producto por su cuenta: el de aquí no tiene las variantes
      // nuevas.
      producto = cargar(comando.productoId());
    }

    ImagenProducto marcada = producto.asignarVarianteAImagenPrincipal(varianteDeLaFoto);
    repositorioProductos.guardarVarianteDeImagen(marcada.id(), varianteDeLaFoto);
    return marcada;
  }

  /** Una variante por talla, en el orden de las tallas; devuelve la primera, la de la foto. */
  private UUID crearVariantesDelColor(
      Producto producto,
      AgregarColorDesdeLaPrincipalComando comando,
      String color,
      Atributo atributoColor,
      MuestraDeColor muestra) {
    List<Variante> modelos = producto.modelosSinColor();
    Map<UUID, Integer> existenciaPorModelo = existenciaPorModelo(comando, modelos);
    Set<String> skusTomados = new HashSet<>();
    UUID primera = null;
    for (Variante modelo : modelos) {
      List<ValorAtributoComando> atributos = new ArrayList<>();
      atributos.add(new ValorAtributoComando(atributoColor.id(), color, muestra.primerHex()));
      for (ValorAtributo otro : modelo.atributosSinColor()) {
        atributos.add(new ValorAtributoComando(otro.atributo().id(), otro.valor(), null));
      }
      Paquete paquete = modelo.paquete().orElse(null);
      VarianteCreada creada =
          agregarVariante.ejecutar(
              new AgregarVarianteComando(
                  producto.id(),
                  skuLibre(modelo.sku(), color, skusTomados),
                  modelo.precio().valor().longValueExact(),
                  modelo.tasaIva(),
                  null,
                  existenciaPorModelo.get(modelo.id()),
                  paquete == null ? null : paquete.pesoGramos(),
                  paquete == null ? null : paquete.largoCm(),
                  paquete == null ? null : paquete.anchoCm(),
                  paquete == null ? null : paquete.altoCm(),
                  atributos));
      if (primera == null) {
        primera = creada.variante().id();
      }
    }
    return primera;
  }

  /**
   * Exactamente una existencia por talla: ni una de más —de una variante ajena o repetida— ni una
   * de menos, que dejaría el color nuevo sin una talla que los demás colores sí tienen.
   */
  private static Map<UUID, Integer> existenciaPorModelo(
      AgregarColorDesdeLaPrincipalComando comando, List<Variante> modelos) {
    Set<UUID> esperados = new HashSet<>();
    for (Variante modelo : modelos) {
      esperados.add(modelo.id());
    }
    Map<UUID, Integer> porModelo = new HashMap<>();
    for (ExistenciaDelColorNuevo existencia : comando.existencias()) {
      if (!esperados.contains(existencia.modeloId())
          || porModelo.put(existencia.modeloId(), existencia.existencia()) != null) {
        throw new ExcepcionDeDominio(
            "La variante '" + existencia.modeloId() + "' no es una talla de este producto.");
      }
    }
    if (!porModelo.keySet().equals(esperados)) {
      throw new ExcepcionDeDominio("Falta la existencia de alguna talla del color nuevo.");
    }
    return porModelo;
  }

  /** El SKU de la talla con el color detrás —{@code PRV-1A2B3C4D-1-ROJO}—, y un número si choca. */
  private String skuLibre(Sku modelo, String color, Set<String> tomados) {
    String sufijo =
        Normalizer.normalize(color, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "-")
            .replaceAll("^-|-$", "");
    if (sufijo.length() > LARGO_MAXIMO_DEL_SUFIJO) {
      sufijo = sufijo.substring(0, LARGO_MAXIMO_DEL_SUFIJO).replaceAll("-$", "");
    }
    String base = modelo.valor() + "-" + sufijo;
    String candidato = base;
    int intento = 2;
    while (tomados.contains(candidato)
        || repositorioProductos.existeVarianteConSku(new Sku(candidato))) {
      candidato = base + "-" + intento++;
    }
    tomados.add(candidato);
    return candidato;
  }

  private Producto cargar(UUID productoId) {
    return repositorioProductos
        .buscarPorId(productoId)
        .orElseThrow(() -> new ProductoNoEncontradoPorIdException(productoId));
  }

  /**
   * El mismo atributo que usa la aprobación de un borrador; si no existe, el catálogo está roto.
   */
  private Atributo atributoColor() {
    return repositorioAtributos.listarTodas().stream()
        .filter(a -> a.tipo() == TipoAtributo.COLOR)
        .filter(a -> a.nombre().toLowerCase(Locale.ROOT).equals(ATRIBUTO_COLOR))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("El catálogo no tiene el atributo Color."));
  }
}
