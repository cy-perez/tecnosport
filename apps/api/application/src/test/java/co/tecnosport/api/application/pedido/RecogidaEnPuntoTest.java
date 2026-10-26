package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** La recogida se ofrece solo si está encendida y todo el carrito viene de proveedores locales. */
class RecogidaEnPuntoTest {

  private static final Instant AHORA = Instant.parse("2026-10-26T15:00:00Z");

  private final RepositorioProductosFalso productos = new RepositorioProductosFalso();
  private final RepositorioProveedoresFalso proveedores = new RepositorioProveedoresFalso();

  private final Proveedor riverah = proveedor("La Riverah", true);
  private final Proveedor violeta = proveedor("Violeta", true);
  private final Proveedor mayorista = proveedor("Mayorista", false);

  private final Variante deRiverah;
  private final Variante deVioleta;
  private final Variante delMayorista;
  private final Variante manual;
  private final Variante deProveedorBorrado;

  RecogidaEnPuntoTest() {
    proveedores.conProveedores(riverah, violeta, mayorista);
    Producto pRiverah = deProveedor("Blusa", riverah.id());
    Producto pVioleta = deProveedor("Bolso", violeta.id());
    Producto pMayorista = deProveedor("Celular", mayorista.id());
    Producto pBorrado = deProveedor("Falda", UUID.randomUUID());
    Producto pManual =
        Producto.crear("Gorra", new Slug("gorra"), "Gorra.", Marca.crear("TecnoSport"), ropa());
    deRiverah = conVariante(pRiverah, "TS-1");
    deVioleta = conVariante(pVioleta, "TS-2");
    delMayorista = conVariante(pMayorista, "TS-3");
    deProveedorBorrado = conVariante(pBorrado, "TS-4");
    manual = conVariante(pManual, "TS-5");
    productos.conProductos(pRiverah, pVioleta, pMayorista, pBorrado, pManual);
  }

  private RecogidaEnPunto recogida(boolean encendida) {
    return new RecogidaEnPunto(new ModalidadesDeEntrega(encendida), productos, proveedores);
  }

  @Test
  void todoDeProveedoresLocalesSeRecogeAunqueSeanDistintos() {
    assertTrue(recogida(true).disponiblePara(List.of(deRiverah.id(), deVioleta.id())));
  }

  @Test
  void conLaRecogidaApagadaNiLoLocalSeRecoge() {
    assertFalse(recogida(false).disponiblePara(List.of(deRiverah.id())));
  }

  @Test
  void unSoloArticuloNoLocalLaQuitaParaTodoElCarrito() {
    assertFalse(recogida(true).disponiblePara(List.of(deRiverah.id(), delMayorista.id())));
  }

  @Test
  void unProductoManualNoEsDeProveedorLocal() {
    assertFalse(recogida(true).disponiblePara(List.of(deVioleta.id(), manual.id())));
  }

  @Test
  void loQueNoSeSabeDeDondeVieneNoSeRecoge() {
    assertFalse(recogida(true).disponiblePara(List.of(deProveedorBorrado.id())));
    assertFalse(recogida(true).disponiblePara(List.of(UUID.randomUUID())));
    assertFalse(recogida(true).disponiblePara(List.of()));
    assertFalse(recogida(true).disponiblePara(null));
  }

  @Test
  void dejarDeSerLocalSeNotaEnLaSiguientePregunta() {
    RecogidaEnPunto recogida = recogida(true);
    assertTrue(recogida.disponiblePara(List.of(deRiverah.id())));

    riverah.definirLocal(false);

    assertFalse(recogida.disponiblePara(List.of(deRiverah.id())));
  }

  @Test
  void exigirSoloMiraLaRecogida() {
    RecogidaEnPunto recogida = recogida(true);
    List<UUID> mixto = List.of(deRiverah.id(), delMayorista.id());

    assertThrows(
        RetiroEnPuntoNoDisponibleException.class,
        () -> recogida.exigirDisponible(TipoEntrega.RETIRO_EN_PUNTO, mixto));
    assertDoesNotThrow(() -> recogida.exigirDisponible(TipoEntrega.ENVIO_A_DOMICILIO, mixto));
    assertDoesNotThrow(
        () -> recogida.exigirDisponible(TipoEntrega.RETIRO_EN_PUNTO, List.of(deVioleta.id())));
  }

  /** Al exigir, la variante inventada la responde el caso de uso con su 404, no esto con un 409. */
  @Test
  void alExigirLaVarianteQueNoExisteNoDecide() {
    RecogidaEnPunto recogida = recogida(true);

    assertDoesNotThrow(
        () ->
            recogida.exigirDisponible(
                TipoEntrega.RETIRO_EN_PUNTO, List.of(deVioleta.id(), UUID.randomUUID())));
    assertThrows(
        RetiroEnPuntoNoDisponibleException.class,
        () ->
            recogida.exigirDisponible(
                TipoEntrega.RETIRO_EN_PUNTO, List.of(manual.id(), UUID.randomUUID())));
    assertThrows(
        RetiroEnPuntoNoDisponibleException.class,
        () ->
            recogida(false)
                .exigirDisponible(TipoEntrega.RETIRO_EN_PUNTO, List.of(UUID.randomUUID())));
  }

  private static Proveedor proveedor(String nombre, boolean local) {
    Proveedor proveedor =
        Proveedor.crear(
            nombre, LineaCatalogo.ROPA, "+57 300", nombre, null, OrdenDePublicacion.TEXTO_PRIMERO);
    proveedor.definirLocal(local);
    return proveedor;
  }

  private static Categoria ropa() {
    return Categoria.crear("Blusas", new Slug("ropa-dama-blusas"), LineaCatalogo.ROPA);
  }

  private static Producto deProveedor(String nombre, UUID proveedorId) {
    return Producto.crearDeProveedor(
        nombre,
        new Slug(nombre.toLowerCase()),
        nombre + ".",
        Marca.crear("TecnoSport"),
        ropa(),
        proveedorId,
        Dinero.deCop(40_000),
        HuellaProveedor.calcular(proveedorId, nombre, Dinero.deCop(40_000)),
        AHORA);
  }

  private static Variante conVariante(Producto producto, String sku) {
    Variante variante =
        Variante.crear(
            new Sku(sku),
            Dinero.deCop(60_000),
            new BigDecimal("0.19"),
            null,
            new Paquete(300, 30, 25, 4),
            List.of());
    producto.agregarVariante(variante);
    return variante;
  }
}
