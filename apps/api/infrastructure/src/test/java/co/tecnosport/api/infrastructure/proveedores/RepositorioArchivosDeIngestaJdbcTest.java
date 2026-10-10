package co.tecnosport.api.infrastructure.proveedores;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.proveedores.ArchivoDeIngestaEnLista;
import co.tecnosport.api.application.proveedores.ArchivosDeIngestaPaginados;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.ArchivoDeIngesta;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** El historial de zips contra Postgres real: el agrupado por key, el upsert y la cascada. */
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioArchivosDeIngestaJdbcTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant T = Instant.parse("2026-10-10T15:00:00Z");

  @Autowired private RepositorioArchivosDeIngestaJdbc archivos;
  @Autowired private RepositorioProveedoresJpa proveedores;
  @Autowired private RepositorioLotesIngestaJpa lotes;
  @Autowired private JdbcTemplate jdbc;

  /** Lo que se guarda con JPA tiene que estar en la base antes de que el SQL lo lea o lo nombre. */
  @PersistenceContext private EntityManager em;

  @Test
  void vaYVuelveEnteroYElBorradoSeReescribe() {
    Proveedor proveedor = proveedor();
    ArchivoDeIngesta archivo =
        ArchivoDeIngesta.recibir(clave(proveedor, "a"), proveedor.id(), "Chat.zip", 4_096, T);
    archivos.guardar(archivo);

    ArchivoDeIngesta leido = archivos.buscarPorId(archivo.id()).orElseThrow();
    assertThat(leido.referencia()).isEqualTo(archivo.referencia());
    assertThat(leido.nombreOriginal()).contains("Chat.zip");
    assertThat(leido.tamanoBytes()).contains(4_096L);
    assertThat(leido.subidoEn()).isEqualTo(T);
    assertThat(leido.borradoEn()).isEmpty();

    leido.marcarBorrado(T.plusSeconds(60));
    archivos.guardar(leido);

    assertThat(archivos.buscarPorId(archivo.id()).orElseThrow().borradoEn())
        .contains(T.plusSeconds(60));
  }

  /**
   * El panel avisó dos veces la misma subida: dos lotes sobre un objeto, y un solo archivo. Antes
   * de corregirlo, la segunda violaba la unicidad de la key y se llevaba el lote con ella.
   */
  @Test
  void laMismaKeyDosVecesDejaElPrimeroSinFallar() {
    Proveedor proveedor = proveedor();
    String key = clave(proveedor, "repetida");
    ArchivoDeIngesta primero = ArchivoDeIngesta.recibir(key, proveedor.id(), "uno.zip", 1, T);
    archivos.guardar(primero);

    archivos.guardar(ArchivoDeIngesta.recibir(key, proveedor.id(), "dos.zip", 1, T.plusSeconds(5)));

    Long filas =
        jdbc.queryForObject(
            "select count(*) from archivo_ingesta where referencia = ?", Long.class, key);
    assertThat(filas).isEqualTo(1);
    assertThat(archivos.buscarPorId(primero.id()).orElseThrow().nombreOriginal())
        .contains("uno.zip");
  }

  /** Los de antes de V97 no tienen nombre ni tamaño: vuelven vacíos, no en cero. */
  @Test
  void sinNombreNiTamanoVuelvenVacios() {
    Proveedor proveedor = proveedor();
    ArchivoDeIngesta archivo =
        new ArchivoDeIngesta(
            UUID.randomUUID(), clave(proveedor, "b"), proveedor.id(), null, null, T, null);
    archivos.guardar(archivo);

    ArchivoDeIngesta leido = archivos.buscarPorId(archivo.id()).orElseThrow();
    assertThat(leido.nombreOriginal()).isEmpty();
    assertThat(leido.tamanoBytes()).isEmpty();
  }

  /**
   * Dos lotes sobre un zip son una fila con dos lotes; un zip sin lotes —su ingesta se eliminó— no
   * sale; y basta un lote abierto para que esté en uso.
   */
  @Test
  void elHistorialAgrupaPorArchivoYSabeSiEstaEnUso() {
    Proveedor proveedor = proveedor();
    String dosChats = clave(proveedor, "dos");
    String cerrado = clave(proveedor, "cerrado");
    String sinLotes = clave(proveedor, "huerfano");
    archivos.guardar(ArchivoDeIngesta.recibir(dosChats, proveedor.id(), "dos.zip", 1, T));
    archivos.guardar(
        ArchivoDeIngesta.recibir(cerrado, proveedor.id(), "cerrado.zip", 1, T.minusSeconds(60)));
    archivos.guardar(ArchivoDeIngesta.recibir(sinLotes, proveedor.id(), "nada.zip", 1, T));
    LoteIngesta deCaballero = LoteIngesta.recibirExportacion(proveedor.id(), dosChats, T);
    terminar(deCaballero);
    lotes.guardar(deCaballero);
    lotes.guardar(LoteIngesta.recibirExportacion(proveedor.id(), dosChats, T.plusMillis(1)));
    LoteIngesta delCerrado = LoteIngesta.recibirExportacion(proveedor.id(), cerrado, T);
    terminar(delCerrado);
    lotes.guardar(delCerrado);
    em.flush();

    ArchivosDeIngestaPaginados pagina = archivos.listar(proveedor.id(), 0, 20);

    assertThat(pagina.totalArchivos()).isEqualTo(2);
    assertThat(pagina.items())
        .extracting(a -> a.archivo().referencia())
        .containsExactly(dosChats, cerrado);
    ArchivoDeIngestaEnLista primero = pagina.items().getFirst();
    assertThat(primero.lotes()).isEqualTo(2);
    assertThat(primero.enUso()).isTrue();
    assertThat(pagina.items().get(1).enUso()).isFalse();
    assertThat(archivos.enUso(dosChats)).isTrue();
    assertThat(archivos.enUso(cerrado)).isFalse();
    assertThat(archivos.listar(UUID.randomUUID(), 0, 20).totalArchivos()).isZero();
    assertThat(archivos.listar(proveedor.id(), 0, 1).totalPaginas()).isEqualTo(2);
  }

  @Test
  void elProveedorQueSeBorraSeLlevaSusArchivos() {
    Proveedor proveedor = proveedor();
    ArchivoDeIngesta archivo =
        ArchivoDeIngesta.recibir(clave(proveedor, "c"), proveedor.id(), null, 1, T);
    archivos.guardar(archivo);

    jdbc.update("delete from proveedor where id = ?", proveedor.id());

    assertThat(archivos.buscarPorId(archivo.id())).isEmpty();
  }

  private Proveedor proveedor() {
    Proveedor proveedor =
        Proveedor.crear(
            "Bolsos del Centro",
            LineaCatalogo.BOLSOS,
            "+57 300",
            "Bolsos Centro",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    proveedores.guardar(proveedor);
    em.flush();
    return proveedor;
  }

  private static String clave(Proveedor proveedor, String nombre) {
    return "proveedores/" + proveedor.id() + "/exportaciones/" + nombre + ".zip";
  }

  private static void terminar(LoteIngesta lote) {
    lote.iniciar(T);
    lote.terminar(new ResumenIngesta(1, 0, 1, 1, 1, 0, 0, 0, 0), T.plusSeconds(60));
  }
}
