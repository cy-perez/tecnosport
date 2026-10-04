package co.tecnosport.api.infrastructure.proveedores;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.proveedores.BorradoresPaginados;
import co.tecnosport.api.application.proveedores.DependenciasDeProveedor;
import co.tecnosport.api.application.proveedores.HuellaVisual;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoDeTalla;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@Transactional
class RepositorioBorradoresJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");

  @Autowired private RepositorioProveedoresJpa proveedores;
  @Autowired private RepositorioLotesIngestaJpa lotes;
  @Autowired private RepositorioMensajesProveedorJpa mensajes;
  @Autowired private RepositorioPublicacionesProveedorJpa publicaciones;
  @Autowired private RepositorioBorradoresJpa borradores;
  @PersistenceContext private EntityManager em;

  private Proveedor proveedor;
  private PublicacionProveedor publicacion;

  private void unaPublicacion() {
    proveedor = Proveedor.crear("Bolsos", LineaCatalogo.BOLSOS, "+57 300", "Bolsos Centro", null);
    proveedores.guardar(proveedor);
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
    lotes.guardar(lote);
    MensajeProveedor principal =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("p"), T, "Bolso 💰 53.000");
    mensajes.guardarTodos(List.of(principal));
    publicacion = PublicacionProveedor.abrir(principal);
    publicaciones.guardarTodas(List.of(publicacion));
  }

  private static ProductoExtraido extraido(String titulo, String descripcion) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.ROPA,
        TipoProductoProveedor.CONJUNTO_PANTALON,
        Dinero.deCop(60000),
        Tallas.lista(List.of("M", "L", "XL")),
        3,
        List.of("negro", "vino"),
        "burda strech",
        descripcion,
        descripcion == null ? null : "Pants set",
        false,
        new BigDecimal("0.88"),
        "una nota");
  }

  @Test
  void elBorradorLlenoVaYVuelveEntero() {
    unaPublicacion();
    PHash pHash = PHash.deHex("a1b2c3d4e5f60718");
    BorradorProducto borrador =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            extraido("Conjunto pantalón", "Conjunto en tela burda strech con camiseta oversize."),
            "{\"es_producto\":true}",
            Dinero.deCop(60000),
            Dinero.deCop(78000),
            HuellaProveedor.calcular(proveedor.id(), "Conjunto pantalón", Dinero.deCop(60000)),
            pHash,
            Set.of(AlertaBorrador.CONFIANZA_BAJA, AlertaBorrador.SIN_FOTOS),
            T);
    borradores.guardar(borrador);

    UUID descartada = UUID.randomUUID();
    borrador.descartarFoto(descartada);
    borradores.actualizar(borrador);
    BorradorProducto leido = borradores.buscarPorId(borrador.id()).orElseThrow();

    assertThat(leido.fotosDescartadas()).containsExactly(descartada);
    assertThat(leido.titulo()).contains("Conjunto pantalón");
    assertThat(leido.linea()).contains(LineaCatalogo.ROPA);
    assertThat(leido.tipo()).isEqualTo(TipoProductoProveedor.CONJUNTO_PANTALON);
    assertThat(leido.precioProveedor()).contains(Dinero.deCop(60000));
    assertThat(leido.precioVentaSugerido()).contains(Dinero.deCop(78000));
    assertThat(leido.tallas().tipo()).isEqualTo(TipoDeTalla.LISTA);
    assertThat(leido.tallas().valores()).containsExactly("M", "L", "XL");
    assertThat(leido.cantidadTonos()).contains(3);
    assertThat(leido.tonosNombrados()).containsExactly("negro", "vino");
    assertThat(leido.material()).contains("burda strech");
    assertThat(leido.descripcion())
        .contains("Conjunto en tela burda strech con camiseta oversize.");
    assertThat(leido.altEn()).contains("Pants set");
    assertThat(leido.huella()).isEqualTo(borrador.huella());
    assertThat(leido.pHash()).contains(pHash);
    assertThat(leido.alertas())
        .containsExactlyInAnyOrder(AlertaBorrador.CONFIANZA_BAJA, AlertaBorrador.SIN_FOTOS);
    assertThat(leido.estado()).isEqualTo(EstadoBorrador.EN_REVISION);
    assertThat(leido.extraccionCruda()).isEqualTo("{\"es_producto\":true}");
    assertThat(leido.creadoEn()).isEqualTo(T);
  }

  @Test
  void elBorradorVacioVaYVuelveVacio() {
    unaPublicacion();
    BorradorProducto borrador =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            new ProductoExtraido(
                true,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                BigDecimal.ZERO,
                null),
            "{}",
            null,
            null,
            null,
            null,
            Set.of(),
            T);
    borradores.guardar(borrador);

    BorradorProducto leido = borradores.buscarPorId(borrador.id()).orElseThrow();

    assertThat(leido.titulo()).isEmpty();
    assertThat(leido.linea()).isEmpty();
    assertThat(leido.tipo()).isEqualTo(TipoProductoProveedor.OTRO);
    assertThat(leido.precioProveedor()).isEmpty();
    assertThat(leido.tallas().tipo()).isEqualTo(TipoDeTalla.DESCONOCIDA);
    assertThat(leido.tallas().valores()).isEmpty();
    assertThat(leido.tonosNombrados()).isEmpty();
    assertThat(leido.descripcion()).isEmpty();
    assertThat(leido.altEn()).isEmpty();
    assertThat(leido.fotosDescartadas()).isEmpty();
    assertThat(leido.huella()).isEmpty();
    assertThat(leido.pHash()).isEmpty();
    assertThat(leido.alertas()).isEmpty();
  }

  @Test
  void seListaPorEstadoYProveedorYLasHuellasVisualesSolo_DeLoQueYaEsProducto() {
    unaPublicacion();
    BorradorProducto enRevision =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            extraido("Uno", null),
            "{}",
            Dinero.deCop(1000),
            null,
            null,
            PHash.deHex("0000000000000001"),
            Set.of(),
            T);
    BorradorProducto rechazado =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            extraido("Dos", null),
            "{}",
            Dinero.deCop(1000),
            null,
            null,
            PHash.deHex("0000000000000002"),
            Set.of(),
            T.plusSeconds(1));
    rechazado.rechazar("No.");
    borradores.guardar(enRevision);
    borradores.guardar(rechazado);

    BorradoresPaginados pendientes =
        borradores.listar(EstadoBorrador.EN_REVISION, proveedor.id(), 0, 10);
    assertThat(pendientes.totalBorradores()).isEqualTo(1);
    assertThat(pendientes.items().get(0).id()).isEqualTo(enRevision.id());
    assertThat(borradores.listar(null, null, 0, 10).totalBorradores()).isEqualTo(2);
    assertThat(borradores.listar(null, UUID.randomUUID(), 0, 10).totalBorradores()).isZero();
    assertThat(borradores.listar(null, null, 0, 10).items().get(0).id())
        .as("el más reciente primero")
        .isEqualTo(rechazado.id());

    // Sin producto todavía, ninguna huella visual cuenta.
    assertThat(borradores.huellasVisualesDelProveedor(proveedor.id())).isEmpty();

    BorradorProducto leido = borradores.buscarPorId(rechazado.id()).orElseThrow();
    assertThat(leido.estado()).isEqualTo(EstadoBorrador.RECHAZADO);
    assertThat(leido.motivoRechazo()).contains("No.");
    List<HuellaVisual> ninguna = borradores.huellasVisualesDelProveedor(UUID.randomUUID());
    assertThat(ninguna).isEmpty();
  }

  /** El anuncio repetido: solo cuenta el que sigue en revisión, y solo el de ese proveedor. */
  @Test
  void sabeSiYaHayUnBorradorEnRevisionConLaMismaHuella() {
    unaPublicacion();
    HuellaProveedor huella = HuellaProveedor.calcular(proveedor.id(), "Uno", Dinero.deCop(1000));
    BorradorProducto enRevision =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            extraido("Uno", null),
            "{}",
            Dinero.deCop(1000),
            null,
            huella,
            null,
            Set.of(),
            T);
    borradores.guardar(enRevision);

    assertThat(borradores.existeEnRevisionConHuella(proveedor.id(), huella)).isTrue();
    assertThat(borradores.existeEnRevisionConHuella(UUID.randomUUID(), huella)).isFalse();

    enRevision.rechazar("Repetido.");
    borradores.actualizar(enRevision);
    assertThat(borradores.existeEnRevisionConHuella(proveedor.id(), huella)).isFalse();
  }

  /**
   * Lo que {@code EliminarBorrador} hace con las filas, contra Postgres y con {@code flush}: el
   * orden de los {@code delete} tiene que respetar las llaves foráneas, y eso solo lo dice la base.
   */
  @Test
  void seBorraLaPublicacionConSusMensajesYSeSabeCualesUsanOtras() {
    proveedor = Proveedor.crear("Bolsos", LineaCatalogo.BOLSOS, "+57 300", "Bolsos Centro", null);
    proveedores.guardar(proveedor);
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
    lotes.guardar(lote);
    MensajeProveedor principal =
        MensajeProveedor.texto(proveedor.id(), lote.id(), new IdExternoDeMensaje("p"), T, "Bolso");
    MensajeProveedor nota =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("n"), T.plusSeconds(5), "Con tira");
    MensajeProveedor foto =
        MensajeProveedor.imagen(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("f"), T.plusSeconds(10), null, "f");
    MensajeProveedor otro =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("x"), T.plusSeconds(600), "Morral");
    mensajes.guardarTodos(List.of(principal, nota, foto, otro));
    publicacion = PublicacionProveedor.abrir(principal);
    publicacion.anexar(nota);
    publicacion.anexar(foto);
    // Una reagrupación que usa la foto como principal, y otra que lleva la nota en su composición.
    PublicacionProveedor conLaFoto = PublicacionProveedor.abrir(foto);
    PublicacionProveedor conLaNota = PublicacionProveedor.abrir(otro);
    conLaNota.anexar(nota);
    publicaciones.guardarTodas(List.of(publicacion, conLaFoto, conLaNota));
    BorradorProducto uno =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            extraido("Uno", null),
            "{}",
            null,
            null,
            null,
            null,
            Set.of(),
            T);
    BorradorProducto dos =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            extraido("Dos", null),
            "{}",
            null,
            null,
            null,
            null,
            Set.of(),
            T);
    borradores.guardar(uno);
    borradores.guardar(dos);
    em.flush();

    assertThat(borradores.contarDePublicacion(publicacion.id())).isEqualTo(2);
    assertThat(
            publicaciones.mensajesUsadosPorOtras(
                publicacion.id(), List.of(principal.id(), nota.id(), foto.id())))
        .containsExactlyInAnyOrder(nota.id(), foto.id());

    borradores.eliminar(uno.id());
    borradores.eliminar(dos.id());
    publicaciones.eliminar(publicacion.id());
    mensajes.eliminarTodos(List.of(principal.id()));
    em.flush();
    em.clear();

    assertThat(borradores.contarDePublicacion(publicacion.id())).isZero();
    assertThat(publicaciones.buscarPorId(publicacion.id())).isEmpty();
    assertThat(publicaciones.buscarPorId(conLaNota.id()).orElseThrow().textosAdicionales())
        .containsExactly(nota.id());
    assertThat(mensajes.listarDeLote(lote.id()))
        .extracting(MensajeProveedor::id)
        .containsExactly(nota.id(), foto.id(), otro.id());
  }

  /**
   * Lo que el caso de uso pregunta antes de eliminar un proveedor, y que el borrado se lleve todo
   * su historial en el orden de las llaves foráneas sin tocar el de otro.
   */
  @Test
  void elProveedorSeVaConSuHistorialYSoloElSuyo() {
    unaPublicacion();
    LoteIngesta lote = lotes.buscarPorId(publicacion.loteId()).orElseThrow();
    mensajes.guardarTodos(
        List.of(
            MensajeProveedor.imagen(
                proveedor.id(),
                lote.id(),
                new IdExternoDeMensaje("foto"),
                T.plusSeconds(5),
                null,
                "p/fotos/1.jpg")));
    borradores.guardar(
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            extraido("Bolso", "Bolso de dama."),
            "{}",
            Dinero.deCop(53000),
            Dinero.deCop(70000),
            HuellaProveedor.calcular(proveedor.id(), "Bolso", Dinero.deCop(53000)),
            null,
            Set.of(),
            T));
    Proveedor ajeno = Proveedor.crear("Otro", LineaCatalogo.ROPA, "+57 301", "Otro", null);
    proveedores.guardar(ajeno);
    lotes.guardar(LoteIngesta.recibirExportacion(ajeno.id(), "o/exportaciones/b.zip", T));

    DependenciasDeProveedor abiertas = proveedores.dependenciasDe(proveedor.id());
    assertThat(abiertas.productos()).isZero();
    assertThat(abiertas.ingestaEnCurso()).isTrue();
    assertThat(abiertas.archivos()).containsExactly("p/exportaciones/a.zip", "p/fotos/1.jpg");

    lote.fallar("se cayó", T.plusSeconds(10));
    lotes.actualizar(lote);
    assertThat(proveedores.dependenciasDe(proveedor.id()).ingestaEnCurso()).isFalse();

    proveedores.eliminarConSuHistorial(proveedor.id());

    assertThat(proveedores.buscarPorId(proveedor.id())).isEmpty();
    for (String tabla :
        List.of(
            "borrador_producto", "publicacion_proveedor", "mensaje_proveedor", "lote_ingesta")) {
      Number filas =
          (Number)
              em.createNativeQuery("select count(*) from " + tabla + " where proveedor_id = ?1")
                  .setParameter(1, proveedor.id())
                  .getSingleResult();
      assertThat(filas.longValue()).as(tabla).isZero();
    }
    assertThat(proveedores.buscarPorId(ajeno.id())).isPresent();
    assertThat(proveedores.dependenciasDe(ajeno.id()).archivos())
        .containsExactly("o/exportaciones/b.zip");
  }
}
