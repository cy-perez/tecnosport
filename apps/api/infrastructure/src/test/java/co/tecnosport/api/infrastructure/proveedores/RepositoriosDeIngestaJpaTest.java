package co.tecnosport.api.infrastructure.proveedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.application.proveedores.LotesPaginados;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import co.tecnosport.api.domain.proveedores.TipoMensaje;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Los tres repositorios del contexto contra Postgres real, escribiendo y releyendo la fila llena y
 * la vacía (docs/06-testing.md: el mapeo JPA contra la migración solo lo ve esta capa).
 */
@SpringBootTest
@Testcontainers
@Transactional
class RepositoriosDeIngestaJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");

  @Autowired private RepositorioProveedoresJpa proveedores;
  @Autowired private RepositorioLotesIngestaJpa lotes;
  @Autowired private RepositorioMensajesProveedorJpa mensajes;
  @Autowired private ProveedorJpaRepository filasDeProveedor;

  private Proveedor proveedorGuardado(BigDecimal factor) {
    Proveedor proveedor =
        Proveedor.crear(
            "Bolsos del Centro", LineaCatalogo.BOLSOS, "+57 300", "Bolsos Centro", factor);
    proveedores.guardar(proveedor);
    return proveedor;
  }

  @Test
  void proveedorLlenoYVacioVanYVuelven() {
    Proveedor conFactor = proveedorGuardado(new BigDecimal("1.350"));
    Proveedor sinFactor = proveedorGuardado(null);

    Proveedor leido = proveedores.buscarPorId(conFactor.id()).orElseThrow();
    assertThat(leido.nombre()).isEqualTo("Bolsos del Centro");
    assertThat(leido.linea()).isEqualTo(LineaCatalogo.BOLSOS);
    assertThat(leido.factorDeMargen()).contains(new BigDecimal("1.350"));
    assertThat(leido.activo()).isTrue();
    assertThat(proveedores.buscarPorId(sinFactor.id()).orElseThrow().factorDeMargen()).isEmpty();
  }

  @Test
  void actualizarConservaLaFechaDeCreacion() {
    Proveedor proveedor = proveedorGuardado(null);
    Instant creadoEn = filasDeProveedor.findById(proveedor.id()).orElseThrow().getCreadoEn();

    proveedor.editar(
        "Meraki",
        LineaCatalogo.ROPA,
        "+57 321",
        "Meraki Cúcuta",
        false,
        true,
        new BigDecimal("1.3"));
    proveedores.actualizar(proveedor);

    Proveedor leido = proveedores.buscarPorId(proveedor.id()).orElseThrow();
    assertThat(leido.nombre()).isEqualTo("Meraki");
    assertThat(leido.activo()).isFalse();
    assertThat(leido.publicacionAutomatica()).isTrue();
    assertThat(filasDeProveedor.findById(proveedor.id()).orElseThrow().getCreadoEn())
        .isEqualTo(creadoEn);
    assertThat(proveedores.listar()).extracting(Proveedor::nombre).containsExactly("Meraki");
  }

  @Test
  void loteRecibidoYLoteTerminadoVanYVuelven() {
    Proveedor proveedor = proveedorGuardado(null);
    LoteIngesta recibido =
        LoteIngesta.recibirExportacion(proveedor.id(), "proveedores/x/exportaciones/a.zip", T);
    lotes.guardar(recibido);

    LoteIngesta leido = lotes.buscarPorId(recibido.id()).orElseThrow();
    assertThat(leido.estado()).isEqualTo(EstadoLote.RECIBIDO);
    assertThat(leido.resumen()).isEmpty();
    assertThat(leido.iniciadoEn()).isEmpty();
    assertThat(leido.referenciaArchivo()).contains("proveedores/x/exportaciones/a.zip");

    leido.iniciar(T.plusSeconds(1));
    leido.terminar(new ResumenIngesta(40, 5, 35, 9, 9, 1, 0, 2, 3), T.plusSeconds(60));
    lotes.actualizar(leido);

    LoteIngesta terminado = lotes.buscarPorId(recibido.id()).orElseThrow();
    assertThat(terminado.estado()).isEqualTo(EstadoLote.TERMINADO);
    assertThat(terminado.resumen()).contains(new ResumenIngesta(40, 5, 35, 9, 9, 1, 0, 2, 3));
    assertThat(terminado.iniciadoEn()).contains(T.plusSeconds(1));
    assertThat(terminado.terminadoEn()).contains(T.plusSeconds(60));
  }

  @Test
  void loteEnErrorGuardaElMotivo() {
    Proveedor proveedor = proveedorGuardado(null);
    LoteIngesta lote =
        LoteIngesta.recibirExportacion(proveedor.id(), "proveedores/x/exportaciones/a.zip", T);
    lotes.guardar(lote);
    lote.fallar("El zip no trae ningún .txt.", T.plusSeconds(5));
    lotes.actualizar(lote);

    LoteIngesta leido = lotes.buscarPorId(lote.id()).orElseThrow();
    assertThat(leido.estado()).isEqualTo(EstadoLote.ERROR);
    assertThat(leido.detalleError()).contains("El zip no trae ningún .txt.");
  }

  /** Lo que un reinicio deja abierto, del más antiguo al más reciente; lo cerrado no cuenta. */
  @Test
  void losLotesAbiertosSonLosRecibidosYLosQueIbanAMedias() {
    Proveedor uno = proveedorGuardado(null);
    LoteIngesta aMedias =
        LoteIngesta.recibirExportacion(uno.id(), "p/exportaciones/1.zip", T.plusSeconds(10));
    aMedias.iniciar(T.plusSeconds(11));
    LoteIngesta enCola = LoteIngesta.recibirExportacion(uno.id(), "p/exportaciones/2.zip", T);
    LoteIngesta cerrado =
        LoteIngesta.recibirExportacion(uno.id(), "p/exportaciones/3.zip", T.plusSeconds(20));
    cerrado.iniciar(T.plusSeconds(21));
    cerrado.terminar(new ResumenIngesta(0, 0, 0, 0, 0, 0, 0, 0, 0), T.plusSeconds(22));
    lotes.guardar(aMedias);
    lotes.guardar(enCola);
    lotes.guardar(cerrado);

    assertThat(lotes.abiertos())
        .extracting(LoteIngesta::estado)
        .containsExactly(EstadoLote.RECIBIDO, EstadoLote.PROCESANDO);
  }

  @Test
  void losLotesSeListanDelMasRecienteAlMasAntiguoYPorProveedor() {
    Proveedor uno = proveedorGuardado(null);
    Proveedor otro = proveedorGuardado(null);
    lotes.guardar(LoteIngesta.recibirExportacion(uno.id(), "p/exportaciones/1.zip", T));
    lotes.guardar(
        LoteIngesta.recibirExportacion(uno.id(), "p/exportaciones/2.zip", T.plusSeconds(10)));
    lotes.guardar(
        LoteIngesta.recibirExportacion(otro.id(), "p/exportaciones/3.zip", T.plusSeconds(20)));

    LotesPaginados deUno = lotes.listar(uno.id(), 0, 10);
    assertThat(deUno.totalLotes()).isEqualTo(2);
    assertThat(deUno.items())
        .extracting(l -> l.referenciaArchivo().orElseThrow())
        .containsExactly("p/exportaciones/2.zip", "p/exportaciones/1.zip");

    LotesPaginados todos = lotes.listar(null, 0, 2);
    assertThat(todos.totalLotes()).isEqualTo(3);
    assertThat(todos.totalPaginas()).isEqualTo(2);
    assertThat(todos.items().get(0).referenciaArchivo()).contains("p/exportaciones/3.zip");
  }

  @Test
  void losMensajesVanYVuelvenConSuTextoIntacto() {
    Proveedor proveedor = proveedorGuardado(null);
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
    lotes.guardar(lote);
    String texto = "*Nueva colección* 😍\nBolso de dama mediano 👜\n💰 *53.000*  ";
    MensajeProveedor deTexto =
        MensajeProveedor.texto(
            proveedor.id(),
            lote.id(),
            IdExternoDeMensaje.deExportacion(proveedor.id(), T, texto),
            T,
            texto);
    MensajeProveedor foto =
        MensajeProveedor.imagen(
            proveedor.id(),
            lote.id(),
            new IdExternoDeMensaje("wamid.foto"),
            T.plusSeconds(30),
            "el vino",
            "proveedores/x/2026/09/abc.jpg");
    MensajeProveedor omitida =
        MensajeProveedor.imagenOmitida(
            proveedor.id(),
            lote.id(),
            new IdExternoDeMensaje("wamid.omitida"),
            T.plusSeconds(40),
            null);
    mensajes.guardarTodos(List.of(foto, deTexto, omitida));

    List<MensajeProveedor> leidos = mensajes.listarDeLote(lote.id());

    assertThat(leidos)
        .extracting(MensajeProveedor::tipo)
        .containsExactly(TipoMensaje.TEXTO, TipoMensaje.IMAGEN, TipoMensaje.IMAGEN);
    assertThat(leidos.get(0).texto()).contains(texto);
    assertThat(leidos.get(1).pieDeFoto()).contains("el vino");
    assertThat(leidos.get(1).referenciaArchivo()).contains("proveedores/x/2026/09/abc.jpg");
    assertThat(leidos.get(2).medioOmitido()).isTrue();
    assertThat(leidos.get(2).referenciaArchivo()).isEmpty();
    assertThat(leidos.get(2).texto()).isEmpty();
  }

  @Test
  void losIdsExistentesSeResponderPorProveedorYLaTablaRechazaElRepetido() {
    Proveedor proveedor = proveedorGuardado(null);
    Proveedor otro = proveedorGuardado(null);
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
    lotes.guardar(lote);
    IdExternoDeMensaje id = new IdExternoDeMensaje("wamid.uno");
    mensajes.guardarTodos(
        List.of(MensajeProveedor.texto(proveedor.id(), lote.id(), id, T, "hola")));

    assertThat(
            mensajes.idsExternosExistentes(
                proveedor.id(), List.of(id, new IdExternoDeMensaje("x"))))
        .isEqualTo(Set.of(id));
    assertThat(mensajes.idsExternosExistentes(otro.id(), List.of(id))).isEmpty();
    assertThat(mensajes.idsExternosExistentes(proveedor.id(), List.of())).isEmpty();

    assertThatThrownBy(
            () -> {
              mensajes.guardarTodos(
                  List.of(MensajeProveedor.texto(proveedor.id(), lote.id(), id, T, "otra vez")));
              filasDeProveedor.flush();
            })
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void unProveedorDesconocidoNoSeEncuentra() {
    assertThat(proveedores.buscarPorId(UUID.randomUUID())).isEqualTo(Optional.empty());
  }
}
