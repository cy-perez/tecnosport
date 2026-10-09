package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.proveedores.ProveedorInactivoException;
import co.tecnosport.api.application.proveedores.ProveedorNoEncontradoException;
import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.VarianteDeProveedor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Aplica la lista del día del proveedor de tecnología al catálogo.
 *
 * <p>Se vende con lo que el proveedor dice tener, y <b>la lista mueve solo la disponibilidad y el
 * costo</b> (decisión del 08/10/2026). El precio de venta, la descripción y las fotos son del
 * panel; la lista no los toca. Por cada modelo:
 *
 * <ul>
 *   <li><b>Ya se vende</b> (hay un producto con su huella): se renueva —vuelve a estar disponible
 *       si el proveedor lo había agotado—, cada configuración que vino actualiza su costo y su
 *       existencia vuelve a {@code existenciaPorVariante} por color. Las configuraciones que el
 *       producto no tiene y nadie descartó van a un borrador de ese producto.
 *   <li><b>No se vende</b>: entra o se actualiza su borrador en revisión, con las configuraciones
 *       que nadie haya descartado. Si no queda ninguna —el modelo se rechazó entero—, no se propone
 *       otra vez.
 * </ul>
 *
 * <p>Lo desaparecido deja de ofrecerse sin borrar nada: una configuración pierde su existencia
 * libre —<b>nunca la reservada</b>, que es de un pedido en curso— y un modelo queda agotado por el
 * proveedor, que lo saca de la vitrina y de la compra.
 *
 * <h2>Los libros se bloquean en orden</h2>
 *
 * <p>La reposición toca muchos libros de inventario con bloqueo pesimista mientras un comprador
 * puede estar reservando dos de ellos. Se ordenan por id de variante y se recorren en ese orden,
 * siempre el mismo, para que dos transacciones no se esperen en cruz.
 *
 * <p>Quien llama abre la transacción: toda la lista entra o no entra ninguna.
 */
public final class ImportarListaDeTecnologia {

  private final RepositorioProveedores repositorioProveedores;
  private final RepositorioProductosDeProveedor productosDeProveedor;
  private final RepositorioProductos repositorioProductos;
  private final RepositorioBorradoresTecnologia repositorioBorradores;
  private final RepositorioVariantesDeProveedor variantesDeProveedor;
  private final RepositorioInventario repositorioInventario;
  private final RepositorioListasDeTecnologia listas;
  private final Reloj reloj;
  private final int existenciaPorVariante;

  public ImportarListaDeTecnologia(
      RepositorioProveedores repositorioProveedores,
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioProductos repositorioProductos,
      RepositorioBorradoresTecnologia repositorioBorradores,
      RepositorioVariantesDeProveedor variantesDeProveedor,
      RepositorioInventario repositorioInventario,
      RepositorioListasDeTecnologia listas,
      Reloj reloj,
      int existenciaPorVariante) {
    this.repositorioProveedores = Objects.requireNonNull(repositorioProveedores);
    this.productosDeProveedor = Objects.requireNonNull(productosDeProveedor);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.variantesDeProveedor = Objects.requireNonNull(variantesDeProveedor);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.listas = Objects.requireNonNull(listas);
    this.reloj = Objects.requireNonNull(reloj);
    if (existenciaPorVariante < 1) {
      throw new IllegalArgumentException("La existencia por variante es por lo menos 1.");
    }
    this.existenciaPorVariante = existenciaPorVariante;
  }

  public Resultado ejecutar(ImportarListaDeTecnologiaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Proveedor proveedor =
        repositorioProveedores
            .buscarPorId(comando.proveedorId())
            .orElseThrow(() -> new ProveedorNoEncontradoException(comando.proveedorId()));
    if (!proveedor.activo()) {
      throw new ProveedorInactivoException(proveedor.nombre());
    }
    if (!proveedor.entraPorLista()) {
      throw new ProveedorSinListasException(proveedor.nombre());
    }
    // Antes de tocar nada. Una lista vieja deshace la de hoy —repone lo que hoy está retirado y
    // retira lo que hoy está repuesto—, y la misma lista dos veces repone lo que se vendió entre
    // las dos. Las dos cosas mueven inventario sin que el proveedor haya dicho nada nuevo.
    String huellaDeLista = comando.huella();
    var ultima = listas.fechaDeLaUltima(proveedor.id());
    if (ultima.isPresent() && comando.fechaLista().isBefore(ultima.get())) {
      throw new ListaDeTecnologiaDesactualizadaException(comando.fechaLista(), ultima.get());
    }
    if (listas.yaEntro(proveedor.id(), huellaDeLista)) {
      throw new ListaDeTecnologiaYaImportadaException(comando.fechaLista());
    }
    Instant visto = comando.fechaLista().atStartOfDay(ZonaDelNegocio.ZONA).toInstant();
    Instant ahora = reloj.ahora();
    String motivo = "Lista del proveedor del " + comando.fechaLista();
    Contador cuenta = new Contador();

    // Variante → existencia libre que tiene que quedar. Ordenado por id: es el orden de bloqueo.
    Map<UUID, Integer> objetivos = new TreeMap<>();

    for (String desaparecida : comando.configuracionesDesaparecidas()) {
      for (VarianteDeProveedor v :
          variantesDeProveedor.deConfiguracion(proveedor.id(), desaparecida)) {
        objetivos.put(v.varianteId(), 0);
      }
    }

    for (ImportarListaDeTecnologiaComando.Modelo entrada : comando.modelos()) {
      HuellaProveedor huella =
          HuellaProveedor.deModelo(proveedor.id(), entrada.modelo().idModelo());
      Optional<Producto> producto = productosDeProveedor.buscarPorHuella(proveedor.id(), huella);
      Set<String> descartados = descartados(proveedor.id(), entrada.modelo().idModelo());
      List<ConfiguracionTecnologia> porProponer = new ArrayList<>();

      if (producto.isPresent()) {
        Producto existente = producto.get();
        existente.renovar(visto);
        repositorioProductos.actualizar(existente);
        cuenta.renovados++;
        Map<String, List<VarianteDeProveedor>> conocidas = porConfiguracion(existente.id());
        Map<UUID, Dinero> precioPorVariante = preciosDe(existente);
        for (ConfiguracionTecnologia c : entrada.configuraciones()) {
          List<VarianteDeProveedor> variantes = conocidas.get(c.sku());
          if (variantes == null) {
            if (!descartados.contains(c.sku())) {
              porProponer.add(c);
            }
            continue;
          }
          for (VarianteDeProveedor v : variantes) {
            variantesDeProveedor.guardar(v.conCosto(c.costoProveedor(), visto));
            objetivos.put(v.varianteId(), hoyLoTiene(c, v.color()) ? existenciaPorVariante : 0);
            Dinero precio = precioPorVariante.get(v.varianteId());
            if (precio != null && precio.valor().compareTo(c.costoProveedor().valor()) <= 0) {
              cuenta.sinMargen.add(c.titulo() + " · " + v.color());
            }
          }
          for (String color : c.coloresSugeridos()) {
            if (variantes.stream()
                .noneMatch(v -> ConfiguracionTecnologia.mismoColor(v.color(), color))) {
              cuenta.coloresSinVariante.add(c.titulo() + " · " + color);
            }
          }
        }
      } else {
        for (ConfiguracionTecnologia c : entrada.configuraciones()) {
          if (!descartados.contains(c.sku())) {
            porProponer.add(c);
          }
        }
      }

      if (porProponer.isEmpty()) {
        if (producto.isEmpty()) {
          cuenta.yaDecididos.add(entrada.modelo().titulo());
        }
        continue;
      }
      proponer(proveedor, entrada, porProponer, producto.map(Producto::id), visto, ahora, cuenta);
    }

    for (String idModelo : comando.modelosDesaparecidos()) {
      productosDeProveedor
          .buscarPorHuella(proveedor.id(), HuellaProveedor.deModelo(proveedor.id(), idModelo))
          .ifPresent(
              p -> {
                p.marcarAgotadoPorProveedor(visto);
                repositorioProductos.actualizar(p);
                cuenta.agotados++;
              });
    }

    listas.registrar(proveedor.id(), comando.fechaLista(), huellaDeLista, ahora);

    for (Map.Entry<UUID, Integer> objetivo : objetivos.entrySet()) {
      Inventario libro = repositorioInventario.abrirLibroConBloqueo(objetivo.getKey());
      int diferencia = objetivo.getValue() - libro.saldoDisponible(ahora);
      if (diferencia > 0) {
        libro.registrarEntrada(diferencia, motivo, ahora);
      } else if (diferencia < 0) {
        // Baja solo lo libre: el objetivo nunca es negativo, así que el total queda en lo reservado
        // o por encima.
        libro.registrarAjuste(diferencia, motivo, ahora);
      }
      if (diferencia != 0) {
        repositorioInventario.guardar(libro);
      }
      if (objetivo.getValue() > 0) {
        cuenta.repuestas++;
      } else {
        cuenta.retiradas++;
      }
    }

    return new Resultado(
        cuenta.renovados,
        cuenta.repuestas,
        cuenta.retiradas,
        cuenta.agotados,
        cuenta.borradoresNuevos,
        cuenta.borradoresActualizados,
        cuenta.yaDecididos,
        cuenta.sinMargen,
        cuenta.coloresSinVariante);
  }

  /**
   * Si la lista de hoy dice tener ese color de la configuración. Cuando la lista no dice colores
   * —sin emojis— vale para todos: no hay con qué retirar uno. Cuando sí los dice, el que no viene
   * se queda sin existencia libre, porque los términos prometen que llega el color que se elige.
   */
  private static boolean hoyLoTiene(ConfiguracionTecnologia hoy, String color) {
    return hoy.coloresSugeridos().isEmpty()
        || hoy.coloresSugeridos().stream()
            .anyMatch(c -> ConfiguracionTecnologia.mismoColor(c, color));
  }

  private void proponer(
      Proveedor proveedor,
      ImportarListaDeTecnologiaComando.Modelo entrada,
      List<ConfiguracionTecnologia> configuraciones,
      Optional<UUID> productoId,
      Instant visto,
      Instant ahora,
      Contador cuenta) {
    Optional<BorradorTecnologia> enRevision =
        repositorioBorradores.buscarEnRevisionParaActualizar(
            proveedor.id(), entrada.modelo().idModelo());
    if (enRevision.isPresent()) {
      BorradorTecnologia borrador = enRevision.get();
      borrador.actualizarConLista(entrada.modelo(), configuraciones, visto);
      repositorioBorradores.actualizar(borrador);
      cuenta.borradoresActualizados++;
      return;
    }
    repositorioBorradores.guardar(
        BorradorTecnologia.nuevo(
            proveedor.id(),
            entrada.modelo(),
            configuraciones,
            productoId.orElse(null),
            visto,
            ahora));
    cuenta.borradoresNuevos++;
  }

  private Set<String> descartados(UUID proveedorId, String idModelo) {
    Set<String> descartados = new HashSet<>();
    for (BorradorTecnologia resuelto :
        repositorioBorradores.listarResueltos(proveedorId, idModelo)) {
      descartados.addAll(resuelto.skusDescartados());
    }
    return descartados;
  }

  private Map<String, List<VarianteDeProveedor>> porConfiguracion(UUID productoId) {
    Map<String, List<VarianteDeProveedor>> mapa = new LinkedHashMap<>();
    for (VarianteDeProveedor v : variantesDeProveedor.deProducto(productoId)) {
      mapa.computeIfAbsent(v.configuracion(), k -> new ArrayList<>()).add(v);
    }
    return mapa;
  }

  private static Map<UUID, Dinero> preciosDe(Producto producto) {
    Map<UUID, Dinero> precios = new LinkedHashMap<>();
    for (Variante v : producto.variantes()) {
      precios.put(v.id(), v.precio());
    }
    return precios;
  }

  private static final class Contador {
    int renovados;
    int repuestas;
    int retiradas;
    int agotados;
    int borradoresNuevos;
    int borradoresActualizados;
    final List<String> yaDecididos = new ArrayList<>();
    final List<String> sinMargen = new ArrayList<>();
    final List<String> coloresSinVariante = new ArrayList<>();
  }

  /**
   * Lo que hizo la lista, para que quien la importa lo lea.
   *
   * @param variantesRepuestas las que quedaron con existencia, una por color de cada configuración
   *     que vino
   * @param variantesRetiradas las de configuraciones desaparecidas, que quedaron sin existencia
   *     libre
   * @param modelosYaDecididos los modelos nuevos que no se volvieron a proponer porque alguien ya
   *     los rechazó
   * @param sinMargen configuración y color que ahora cuestan lo mismo o más que su precio de venta:
   *     la lista no mueve el precio, así que alguien tiene que mirarlo
   * @param coloresSinVariante colores que la lista trae de una configuración que ya se vende y que
   *     el producto no tiene: no se proponen solos, se añaden desde el panel del producto
   */
  public record Resultado(
      int productosRenovados,
      int variantesRepuestas,
      int variantesRetiradas,
      int modelosAgotados,
      int borradoresNuevos,
      int borradoresActualizados,
      List<String> modelosYaDecididos,
      List<String> sinMargen,
      List<String> coloresSinVariante) {
    public Resultado {
      modelosYaDecididos = List.copyOf(modelosYaDecididos);
      sinMargen = List.copyOf(sinMargen);
      coloresSinVariante = List.copyOf(coloresSinVariante);
    }
  }
}
