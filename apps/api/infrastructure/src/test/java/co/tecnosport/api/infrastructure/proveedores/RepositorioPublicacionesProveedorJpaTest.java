package co.tecnosport.api.infrastructure.proveedores;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.EstadoPublicacionProveedor;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import java.time.Instant;
import java.util.List;
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
class RepositorioPublicacionesProveedorJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");

  @Autowired private RepositorioProveedoresJpa proveedores;
  @Autowired private RepositorioLotesIngestaJpa lotes;
  @Autowired private RepositorioMensajesProveedorJpa mensajes;
  @Autowired private RepositorioPublicacionesProveedorJpa publicaciones;

  @Test
  void unaPublicacionConTextosYMediosVaYVuelveEnOrden() {
    Proveedor proveedor =
        Proveedor.crear("Bolsos", LineaCatalogo.BOLSOS, "+57 300", "Bolsos Centro", null);
    proveedores.guardar(proveedor);
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
    lotes.guardar(lote);
    MensajeProveedor principal =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("p"), T, "Bolso 💰 53.000");
    MensajeProveedor nota =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("n"), T.plusSeconds(10), "Con tira");
    MensajeProveedor foto1 =
        MensajeProveedor.imagen(
            proveedor.id(),
            lote.id(),
            new IdExternoDeMensaje("f1"),
            T.plusSeconds(20),
            null,
            "p/1.jpg");
    MensajeProveedor foto2 =
        MensajeProveedor.imagen(
            proveedor.id(),
            lote.id(),
            new IdExternoDeMensaje("f2"),
            T.plusSeconds(30),
            null,
            "p/2.jpg");
    mensajes.guardarTodos(List.of(principal, nota, foto1, foto2));

    PublicacionProveedor publicacion = PublicacionProveedor.abrir(principal);
    publicacion.anexar(nota);
    publicacion.anexar(foto1);
    publicacion.anexar(foto2);
    PublicacionProveedor vacia =
        PublicacionProveedor.abrir(
            MensajeProveedor.texto(
                proveedor.id(),
                lote.id(),
                new IdExternoDeMensaje("otra"),
                T.plusSeconds(600),
                "Morral 💰 40.000"));
    mensajes.guardarTodos(
        List.of(
            new MensajeProveedor(
                vacia.mensajePrincipalId(),
                proveedor.id(),
                lote.id(),
                new IdExternoDeMensaje("otra"),
                T.plusSeconds(600),
                co.tecnosport.api.domain.proveedores.TipoMensaje.TEXTO,
                "Morral 💰 40.000",
                null,
                null,
                false)));
    publicaciones.guardarTodas(List.of(publicacion, vacia));

    List<PublicacionProveedor> leidas = publicaciones.listarDeLote(lote.id());

    assertThat(leidas).hasSize(2);
    PublicacionProveedor primera = leidas.get(0);
    assertThat(primera.mensajePrincipalId()).isEqualTo(principal.id());
    assertThat(primera.textosAdicionales()).containsExactly(nota.id());
    assertThat(primera.medios()).containsExactly(foto1.id(), foto2.id());
    assertThat(primera.estado()).isEqualTo(EstadoPublicacionProveedor.PENDIENTE_EXTRACCION);
    assertThat(leidas.get(1).textosAdicionales()).isEmpty();
    assertThat(leidas.get(1).medios()).isEmpty();

    primera.descartar("Es un saludo.");
    publicaciones.actualizar(primera);
    PublicacionProveedor releida = publicaciones.buscarPorId(primera.id()).orElseThrow();
    assertThat(releida.estado()).isEqualTo(EstadoPublicacionProveedor.DESCARTADA);
    assertThat(releida.motivo()).contains("Es un saludo.");
    assertThat(releida.medios()).containsExactly(foto1.id(), foto2.id());
  }
}
