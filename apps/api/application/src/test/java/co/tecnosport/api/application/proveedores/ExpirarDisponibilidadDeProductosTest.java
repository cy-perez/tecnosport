package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioProductosDeProveedorEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeCatalogoParaIngesta.RepositorioProductosEnMemoria;
import co.tecnosport.api.domain.catalogo.EstadoDisponibilidad;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Con el reloj movido a mano, nunca esperando: es lo que el puerto Reloj compra. */
class ExpirarDisponibilidadDeProductosTest {

  private static final Instant VISTO = Instant.parse("2026-09-28T15:15:00Z");
  private static final Duration UN_MINUTO = Duration.ofMinutes(1);

  private final RepositorioProductosEnMemoria productos = new RepositorioProductosEnMemoria();
  private final UUID proveedorA = UUID.randomUUID();
  private final UUID proveedorB = UUID.randomUUID();

  /** Un reloj que se adelanta desde la prueba. */
  private static final class RelojAjustable implements Reloj {
    Instant ahora = VISTO;

    @Override
    public Instant ahora() {
      return ahora;
    }
  }

  private Producto deProveedor(UUID proveedorId, String titulo) {
    Producto producto =
        Producto.crearDeProveedor(
            titulo,
            Slug.generarDesde(titulo),
            "",
            ApoyoDeCatalogoParaIngesta.MARCA,
            ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO,
            proveedorId,
            Dinero.deCop(53000),
            HuellaProveedor.calcular(proveedorId, titulo, Dinero.deCop(53000)),
            VISTO);
    productos.guardar(producto);
    return producto;
  }

  private ExpirarDisponibilidadDeProductos caso(RelojAjustable reloj, Duration ventana) {
    return new ExpirarDisponibilidadDeProductos(
        new RepositorioProductosDeProveedorEnMemoria(productos), productos, reloj, ventana);
  }

  /** El quinto criterio de aceptación: ventana de un minuto y el reloj dos minutos adelante. */
  @Test
  void conElRelojDosMinutosAdelanteOcultaLoQueLlevaMasDeUnMinutoSinVerse() {
    Producto viejo = deProveedor(proveedorA, "Bolso viejo");
    Producto reciente = deProveedor(proveedorA, "Bolso reciente");
    Producto deOtro = deProveedor(proveedorB, "Morral");
    Producto manual =
        Producto.crear(
            "Camiseta",
            new Slug("camiseta"),
            "",
            ApoyoDeCatalogoParaIngesta.MARCA,
            ApoyoDeCatalogoParaIngesta.BOLSOS_DE_MANO);
    productos.guardar(manual);
    RelojAjustable reloj = new RelojAjustable();
    reloj.ahora = VISTO.plus(Duration.ofMinutes(2));
    reciente.renovar(VISTO.plusSeconds(90));

    ExpirarDisponibilidadDeProductos.Resultado resultado = caso(reloj, UN_MINUTO).ejecutar();

    assertEquals(2, resultado.ocultados());
    assertEquals(Map.of(proveedorA, 1, proveedorB, 1), resultado.porProveedor());
    assertEquals(EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO, viejo.estadoDisponibilidad());
    assertEquals(EstadoDisponibilidad.OCULTO_POR_VENCIMIENTO, deOtro.estadoDisponibilidad());
    assertEquals(EstadoDisponibilidad.DISPONIBLE, reciente.estadoDisponibilidad());
    assertEquals(EstadoDisponibilidad.DISPONIBLE, manual.estadoDisponibilidad(), "un manual nunca");
  }

  @Test
  void antesDeQueVenzaLaVentanaNoOcultaNada() {
    deProveedor(proveedorA, "Bolso");
    RelojAjustable reloj = new RelojAjustable();
    reloj.ahora = VISTO.plusSeconds(59);

    assertEquals(0, caso(reloj, UN_MINUTO).ejecutar().ocultados());
  }

  /** Correrlo dos veces oculta una vez: la segunda vuelta no encuentra disponibles vencidos. */
  @Test
  void esIdempotente() {
    deProveedor(proveedorA, "Bolso");
    RelojAjustable reloj = new RelojAjustable();
    reloj.ahora = VISTO.plus(Duration.ofDays(4));
    ExpirarDisponibilidadDeProductos caso = caso(reloj, Duration.ofDays(3));

    assertEquals(1, caso.ejecutar().ocultados());
    assertEquals(0, caso.ejecutar().ocultados());
  }

  /** Lo que el proveedor volvió a anunciar se reactiva, y la siguiente vuelta lo respeta. */
  @Test
  void unaRenovacionLoSacaDelVencimiento() {
    Producto producto = deProveedor(proveedorA, "Bolso");
    RelojAjustable reloj = new RelojAjustable();
    reloj.ahora = VISTO.plus(Duration.ofMinutes(2));
    ExpirarDisponibilidadDeProductos caso = caso(reloj, UN_MINUTO);
    caso.ejecutar();

    producto.renovar(reloj.ahora);
    productos.actualizar(producto);

    assertEquals(EstadoDisponibilidad.DISPONIBLE, producto.estadoDisponibilidad());
    assertEquals(0, caso.ejecutar().ocultados());
  }

  @Test
  void laVentanaTieneQueSerPositiva() {
    assertThrows(IllegalArgumentException.class, () -> caso(new RelojAjustable(), Duration.ZERO));
  }
}
