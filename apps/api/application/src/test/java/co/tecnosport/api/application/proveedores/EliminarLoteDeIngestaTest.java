package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.EliminarProducto;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/**
 * Con {@link EliminarProducto} de verdad: la traducción de sus negativas a «se queda» vive aquí
 * desde que el puerto {@code EliminacionDeProductos} se borró (27 de octubre de 2026), y se prueba
 * contra el caso de uso, no contra un doble que ya devuelve el booleano.
 */
class EliminarLoteDeIngestaTest {

  private final ApoyoDeIngesta.RepositorioLotesEnMemoria lotes =
      new ApoyoDeIngesta.RepositorioLotesEnMemoria();
  private final ApoyoDeIngesta.AlmacenEnMemoria almacen = new ApoyoDeIngesta.AlmacenEnMemoria();
  private final Map<UUID, Producto> catalogo = new HashMap<>();
  private final Set<UUID> variantesVendidas = new HashSet<>();
  private final List<UUID> productosBorrados = new ArrayList<>();

  @SuppressWarnings("unchecked")
  private final EliminarProducto eliminarProducto =
      new EliminarProducto(
          doble(
              RepositorioProductos.class,
              (metodo, args) ->
                  switch (metodo) {
                    case "buscarPorId" -> Optional.ofNullable(catalogo.get((UUID) args[0]));
                    case "eliminar" -> {
                      catalogo.remove((UUID) args[0]);
                      productosBorrados.add((UUID) args[0]);
                      yield null;
                    }
                    default -> throw new UnsupportedOperationException(metodo);
                  }),
          doble(
              RepositorioPedidos.class,
              (metodo, args) -> {
                if (!metodo.equals("hayLineasDeAlgunaVariante")) {
                  throw new UnsupportedOperationException(metodo);
                }
                return ((Collection<UUID>) args[0]).stream().anyMatch(variantesVendidas::contains);
              }),
          doble(
              AlmacenDeImagenes.class,
              (metodo, args) -> {
                if (!metodo.equals("eliminarPorPrefijo")) {
                  throw new UnsupportedOperationException(metodo);
                }
                return 0;
              }));

  private final EliminarLoteDeIngesta eliminarLote =
      new EliminarLoteDeIngesta(lotes, eliminarProducto, almacen);

  /** Un doble que solo responde los métodos que este caso de uso usa, por nombre. */
  @SuppressWarnings("unchecked")
  private static <T> T doble(Class<T> tipo, BiFunction<String, Object[], Object> respuesta) {
    return (T)
        Proxy.newProxyInstance(
            tipo.getClassLoader(),
            new Class<?>[] {tipo},
            (proxy, metodo, args) -> respuesta.apply(metodo.getName(), args));
  }

  private UUID enBorrador() {
    Producto producto = conVariante();
    catalogo.put(producto.id(), producto);
    return producto.id();
  }

  private UUID publicado() {
    Producto producto = conVariante();
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(800, "https://x/0.jpg", 1000)),
            null,
            600,
            new HashContenido("%064x".formatted(0)),
            "alt es",
            "alt en"));
    producto.publicar();
    catalogo.put(producto.id(), producto);
    return producto.id();
  }

  private UUID vendido() {
    UUID id = enBorrador();
    variantesVendidas.add(catalogo.get(id).variantes().getFirst().id());
    return id;
  }

  private static Producto conVariante() {
    Producto producto =
        Producto.crear(
            "Bolso",
            new Slug("bolso-" + UUID.randomUUID()),
            "",
            Marca.crear("Genérica"),
            Categoria.crear("Morrales", new Slug("bolsos-dama-morrales"), LineaCatalogo.BOLSOS));
    producto.agregarVariante(
        Variante.crear(
            new Sku("PRV-" + UUID.randomUUID().toString().substring(0, 8)),
            Dinero.deCop(60000),
            BigDecimal.ZERO,
            null,
            null,
            List.of()));
    return producto;
  }

  private LoteIngesta loteTerminado() {
    LoteIngesta lote =
        LoteIngesta.recibirExportacion(
            UUID.randomUUID(), "proveedores/x/exportacion.zip", ApoyoDeIngesta.AHORA);
    lote.iniciar(ApoyoDeIngesta.AHORA.plusSeconds(1));
    lote.terminar(
        new ResumenIngesta(4, 0, 4, 2, 2, 0, 0, 0, 0, 0), ApoyoDeIngesta.AHORA.plusSeconds(60));
    lotes.guardar(lote);
    return lote;
  }

  @Test
  void borraLosProductosNoPublicadosLosArchivosYElLote() {
    LoteIngesta lote = loteTerminado();
    UUID enBorrador = enBorrador();
    UUID publicado = publicado();
    almacen.objetos.put("proveedores/x/exportacion.zip", new byte[] {1});
    almacen.objetos.put("proveedores/x/foto.jpg", new byte[] {2});
    lotes.dependencias.put(
        lote.id(),
        new DependenciasDeLote(
            List.of(enBorrador, publicado),
            List.of("proveedores/x/exportacion.zip", "proveedores/x/foto.jpg")));

    LoteEliminado resultado = eliminarLote.ejecutar(lote.id());

    assertEquals(new LoteEliminado(1, 1, 2), resultado);
    assertEquals(List.of(enBorrador), productosBorrados);
    assertTrue(almacen.objetos.isEmpty());
    assertEquals(List.of(lote.id()), lotes.eliminados);
  }

  /** El vendido se queda como el publicado, y el que ya no existe cuenta como borrado. */
  @Test
  void elVendidoSeQuedaYElQueYaNoExisteCuentaComoBorrado() {
    LoteIngesta lote = loteTerminado();
    UUID vendido = vendido();
    UUID yaNoEsta = UUID.randomUUID();
    lotes.dependencias.put(
        lote.id(), new DependenciasDeLote(List.of(vendido, yaNoEsta), List.of()));

    LoteEliminado resultado = eliminarLote.ejecutar(lote.id());

    assertEquals(new LoteEliminado(1, 1, 0), resultado);
    assertTrue(productosBorrados.isEmpty());
    assertTrue(catalogo.containsKey(vendido));
  }

  @Test
  void conElLoteEnCursoNoBorraNada() {
    LoteIngesta lote =
        LoteIngesta.recibirExportacion(
            UUID.randomUUID(), "proveedores/x/exportacion.zip", ApoyoDeIngesta.AHORA);
    lotes.guardar(lote);
    UUID producto = enBorrador();
    almacen.objetos.put("proveedores/x/exportacion.zip", new byte[] {1});
    lotes.dependencias.put(
        lote.id(),
        new DependenciasDeLote(List.of(producto), List.of("proveedores/x/exportacion.zip")));

    assertThrows(LoteEnCursoException.class, () -> eliminarLote.ejecutar(lote.id()));

    assertTrue(productosBorrados.isEmpty());
    assertFalse(almacen.objetos.isEmpty());
    assertTrue(lotes.eliminados.isEmpty());
  }

  @Test
  void unLoteQueNoExisteFalla() {
    assertThrows(LoteNoEncontradoException.class, () -> eliminarLote.ejecutar(UUID.randomUUID()));
    assertTrue(lotes.eliminados.isEmpty());
  }
}
