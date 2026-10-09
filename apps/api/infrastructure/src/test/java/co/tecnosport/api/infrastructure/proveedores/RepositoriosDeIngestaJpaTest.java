package co.tecnosport.api.infrastructure.proveedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.application.proveedores.LotesPaginados;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.PHash;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
  @Autowired private MensajeProveedorJpaRepository filasDeMensaje;
  @Autowired private JdbcTemplate jdbc;

  private Proveedor proveedorGuardado(BigDecimal factor) {
    Proveedor proveedor =
        Proveedor.crear(
            "Bolsos del Centro",
            LineaCatalogo.BOLSOS,
            "+57 300",
            "Bolsos Centro",
            factor,
            OrdenDePublicacion.FOTOS_PRIMERO);
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
    assertThat(leido.ordenDePublicacion()).isEqualTo(OrdenDePublicacion.FOTOS_PRIMERO);
    assertThat(proveedores.buscarPorId(sinFactor.id()).orElseThrow().factorDeMargen()).isEmpty();
  }

  /**
   * El valor por omisión de V82 solo rellenó las filas que ya existían: después se quitó, y un
   * insert a mano que no diga el orden falla en vez de recibir uno que nadie eligió.
   */
  @Test
  void unInsertSinOrdenDePublicacionNoEntra() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into proveedor (id, nombre, linea, telefono_whatsapp,"
                        + " nombre_en_exportacion, activo, publicacion_automatica, creado_en,"
                        + " actualizado_en)"
                        + " values (?, 'Sin orden', 'ROPA', '+57 300', 'Sin orden', true, false,"
                        + " now(), now())",
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
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
        new BigDecimal("1.3"),
        OrdenDePublicacion.TEXTO_PRIMERO);
    proveedores.actualizar(proveedor);

    Proveedor leido = proveedores.buscarPorId(proveedor.id()).orElseThrow();
    assertThat(leido.nombre()).isEqualTo("Meraki");
    assertThat(leido.activo()).isFalse();
    assertThat(leido.publicacionAutomatica()).isTrue();
    assertThat(leido.ordenDePublicacion()).isEqualTo(OrdenDePublicacion.TEXTO_PRIMERO);
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

  /**
   * Una exportación de Android no trae segundos y el lote entero comparte {@code creado_en}: los
   * mensajes del mismo minuto empatan en todo, y el agrupador necesita el orden del archivo para
   * saber de qué precio es cada foto.
   *
   * <p>Recién insertadas, Postgres devuelve las filas en el orden de inserción aunque nada lo pida,
   * y la prueba pasaría sin el arreglo. Por eso se reescriben al revés: así el orden físico de la
   * tabla y el de los índices quedan invertidos, y solo la posición guardada los endereza.
   *
   * <p>Y se apagan los recorridos por índice: {@code ix_mensaje_proveedor_lote} termina en {@code
   * posicion}, y leyendo por él el orden sale bien aunque la consulta no lo pida. Lo que se prueba
   * es el {@code ORDER BY}, que es lo único que el planificador promete respetar.
   */
  @Test
  void losMensajesDelMismoMinutoSeLeenEnElOrdenEnQueSeGuardaron() {
    Proveedor proveedor = proveedorGuardado(null);
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
    lotes.guardar(lote);
    MensajeProveedor foto =
        MensajeProveedor.imagen(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("a"), T, null, "p/m/1.jpg");
    MensajeProveedor caballero =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("b"), T, "Caballero 💰 $115.000");
    MensajeProveedor otraFoto =
        MensajeProveedor.imagen(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("c"), T, null, "p/m/2.jpg");
    MensajeProveedor superstar =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("d"), T, "Superstar 💰 $105.000");
    mensajes.guardarTodos(List.of(foto, caballero, otraFoto, superstar));
    filasDeMensaje.flush();

    jdbc.execute(
        "create temp table copia_de_mensajes on commit drop as"
            + " select * from mensaje_proveedor where lote_id = '"
            + lote.id()
            + "'");
    jdbc.update("delete from mensaje_proveedor where lote_id = ?", lote.id());
    jdbc.update(
        "insert into mensaje_proveedor select * from copia_de_mensajes order by id_externo desc");

    jdbc.execute("set local enable_indexscan = off");
    jdbc.execute("set local enable_indexonlyscan = off");
    jdbc.execute("set local enable_bitmapscan = off");

    assertThat(mensajes.listarDeLote(lote.id()))
        .extracting(MensajeProveedor::id)
        .containsExactly(foto.id(), caballero.id(), otraFoto.id(), superstar.id());
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

  /**
   * Un reinicio se lleva el hilo que retenía a los pausados y a los que se estaban deteniendo: si
   * {@code abiertos} no los devolviera, el arranque no los cerraría y quedarían abiertos —sin poder
   * eliminarse— para siempre.
   */
  @Test
  void losPausadosYLosQueSeDetienenTambienEstanAbiertos() {
    Proveedor uno = proveedorGuardado(null);
    LoteIngesta pausado = LoteIngesta.recibirExportacion(uno.id(), "p/exportaciones/1.zip", T);
    pausado.iniciar(T.plusSeconds(1));
    pausado.pausar();
    LoteIngesta deteniendo =
        LoteIngesta.recibirExportacion(uno.id(), "p/exportaciones/2.zip", T.plusSeconds(5));
    deteniendo.iniciar(T.plusSeconds(6));
    deteniendo.pedirDetencion(T.plusSeconds(7));
    LoteIngesta detenido =
        LoteIngesta.recibirExportacion(uno.id(), "p/exportaciones/3.zip", T.plusSeconds(10));
    detenido.pedirDetencion(T.plusSeconds(11));
    lotes.guardar(pausado);
    lotes.guardar(deteniendo);
    lotes.guardar(detenido);

    assertThat(lotes.abiertos())
        .extracting(LoteIngesta::estado)
        .containsExactly(EstadoLote.PAUSADO, EstadoLote.DETENIENDO);
  }

  /** Detenido a la mitad: vuelve con el resumen parcial; detenido en la cola, sin resumen. */
  @Test
  void unLoteDetenidoVaYVuelveConLoQueAlcanzo() {
    Proveedor uno = proveedorGuardado(null);
    LoteIngesta aMedias = LoteIngesta.recibirExportacion(uno.id(), "p/exportaciones/1.zip", T);
    aMedias.iniciar(T.plusSeconds(1));
    aMedias.pedirDetencion(T.plusSeconds(2));
    ResumenIngesta parcial = new ResumenIngesta(40, 5, 35, 9, 3, 0, 0, 0, 0);
    aMedias.detener(parcial, T.plusSeconds(3));
    LoteIngesta enCola = LoteIngesta.recibirExportacion(uno.id(), "p/exportaciones/2.zip", T);
    enCola.pedirDetencion(T.plusSeconds(1));
    lotes.guardar(aMedias);
    lotes.guardar(enCola);

    LoteIngesta leido = lotes.buscarPorIdParaActualizar(aMedias.id()).orElseThrow();
    assertThat(leido.estado()).isEqualTo(EstadoLote.DETENIDO);
    assertThat(leido.resumen()).contains(parcial);
    assertThat(lotes.buscarPorId(enCola.id()).orElseThrow().resumen()).isEmpty();
  }

  /** La marca del chat de caballero sobrevive a guardar y leer, también después de terminar. */
  @Test
  void elLoteDelChatDeCaballeroVaYVuelveConSuMarca() {
    Proveedor proveedor = proveedorGuardado(null);
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/m.zip", T);
    lote.marcarChatDeCaballero();
    lotes.guardar(lote);
    LoteIngesta otro = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/g.zip", T);
    lotes.guardar(otro);

    assertThat(lotes.buscarPorId(lote.id()).orElseThrow().esChatDeCaballero()).isTrue();
    assertThat(lotes.buscarPorId(otro.id()).orElseThrow().esChatDeCaballero()).isFalse();
  }

  /** El pHash de una foto se guarda con el mensaje y vuelve igual. */
  @Test
  void elPHashDeLaFotoVaYVuelveConElMensaje() {
    Proveedor proveedor = proveedorGuardado(null);
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
    lotes.guardar(lote);
    PHash pHash = PHash.deHex("0123456789abcdef");
    MensajeProveedor foto =
        new MensajeProveedor(
            UUID.randomUUID(),
            proveedor.id(),
            lote.id(),
            new IdExternoDeMensaje("f"),
            T,
            TipoMensaje.IMAGEN,
            null,
            null,
            "proveedores/x/f.jpg",
            false,
            pHash);
    mensajes.guardarTodos(List.of(foto));

    assertThat(mensajes.listarDeLote(lote.id()).getFirst().pHash()).contains(pHash);
  }
}
