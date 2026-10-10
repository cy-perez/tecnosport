package co.tecnosport.api.infrastructure.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.application.pedido.BorradoDePedidos.Compromiso;
import co.tecnosport.api.application.pedido.PedidoNoEliminableException;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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

/**
 * Contra Postgres porque lo que se prueba son las tablas: que cada compromiso mire las suyas y que
 * el borrado no tropiece con ninguna llave foránea.
 */
@SpringBootTest
@Testcontainers
@Transactional
class BorradoDePedidosJdbcTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant T = Instant.parse("2026-10-10T12:00:00Z");

  @Autowired private BorradoDePedidosJdbc borrado;
  @Autowired private RepositorioPedidosJpa pedidos;
  @Autowired private JdbcTemplate jdbc;
  @PersistenceContext private EntityManager em;

  private int secuencia;

  /**
   * Con un intento rechazado y su aviso: los dos se van con él; las líneas y el historial, igual.
   */
  @Test
  void unPedidoConSoloIntentosFallidosNoTieneCompromisosYSeBorraEntero() {
    Pedido pedido = pedidoFallido();
    UUID pago = pago(pedido, "RECHAZADO");
    jdbc.update(
        "insert into evento_pago (id, pago_id, id_evento, estado, recibido_en) values (?,?,?,?,?)",
        UUID.randomUUID(),
        pago,
        "evt-1",
        "RECHAZADO",
        Timestamp.from(T));

    assertThat(borrado.compromisosDe(pedido.id())).isEmpty();
    borrado.eliminar(pedido.id());

    assertThat(contar("pedido", "id", pedido.id())).isZero();
    assertThat(contar("linea_pedido", "pedido_id", pedido.id())).isZero();
    assertThat(contar("historial_pedido", "pedido_id", pedido.id())).isZero();
    assertThat(contar("pago", "pedido_id", pedido.id())).isZero();
    assertThat(contar("evento_pago", "pago_id", pago)).isZero();
  }

  /**
   * La carrera con "reintentar pago": un pago pendiente que entró después de la consulta. El
   * borrado no lo toca, la llave hacia el pedido hace fallar el segundo `delete`, y sale como la
   * negativa de siempre en vez de un 500.
   */
  @Test
  void unPagoPendienteQueEntroDespuesNoSeBorraYElPedidoTampoco() {
    Pedido pedido = pedidoFallido();
    pago(pedido, "PENDIENTE");

    assertThatThrownBy(() -> borrado.eliminar(pedido.id()))
        .isInstanceOf(PedidoNoEliminableException.class);
  }

  /** Aprobado —un pago que llegó tarde a un pedido cancelado— y pendiente cuentan los dos. */
  @Test
  void unPagoAprobadoOPendienteEsUnCompromiso() {
    Pedido aprobado = pedidoFallido();
    pago(aprobado, "APROBADO");
    Pedido pendiente = pedidoFallido();
    pago(pendiente, "PENDIENTE");

    assertThat(borrado.compromisosDe(aprobado.id())).containsExactly(Compromiso.PAGO);
    assertThat(borrado.compromisosDe(pendiente.id())).containsExactly(Compromiso.PAGO);
  }

  @Test
  void unEnvioYUnaPqrSonCompromisos() {
    Pedido conEnvio = pedidoFallido();
    jdbc.update(
        "insert into envio (id, pedido_id, despachado_en) values (?,?,?)",
        UUID.randomUUID(),
        conEnvio.id(),
        Timestamp.from(T));
    Pedido conPqr = pedidoFallido();
    jdbc.update(
        "insert into solicitud_atencion (id, numero_radicado, tipo, correo, pedido_id,"
            + " recibida_en, radicada_en, radicada_por, asunto, estado)"
            + " values (?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        "PQR-2026-" + conPqr.id().toString().substring(0, 8),
        "PETICION",
        "cliente@tecnosport.co",
        conPqr.id(),
        Timestamp.from(T),
        Timestamp.from(T),
        "admin:1",
        "Una pregunta",
        "ABIERTA");

    assertThat(borrado.compromisosDe(conEnvio.id())).containsExactly(Compromiso.ENVIO);
    assertThat(borrado.compromisosDe(conPqr.id())).containsExactly(Compromiso.TRAMITE);
  }

  private Pedido pedidoFallido() {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 900 + ++secuencia),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.00"),
                    "https://cdn.tecnosport.co/img.jpg",
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            MetodoPago.WOMPI,
            "cliente@tecnosport.co",
            T);
    pedido.transicionar(EstadoPedido.PAGO_FALLIDO, "webhook-wompi", "rechazado", T);
    pedidos.guardar(pedido);
    em.flush();
    return pedido;
  }

  private UUID pago(Pedido pedido, String estado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into pago (id, pedido_id, referencia, metodo_pago, monto, estado, creado_en,"
            + " actualizado_en) values (?,?,?,?,?,?,?,?)",
        id,
        pedido.id(),
        "ref-" + id,
        "WOMPI",
        new BigDecimal("50000"),
        estado,
        Timestamp.from(T),
        Timestamp.from(T));
    return id;
  }

  private long contar(String tabla, String columna, UUID valor) {
    Long n =
        jdbc.queryForObject(
            "select count(*) from " + tabla + " where " + columna + " = ?", Long.class, valor);
    return n == null ? 0 : n;
  }
}
