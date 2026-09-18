package co.tecnosport.api.infrastructure.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.infrastructure.pedido.RepositorioPedidosJpa;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Que guardar una emisión sea atómico, comprobado de verdad.
 *
 * <p><b>Sin {@code @Transactional} de clase, y esa es la razón de que sea un archivo aparte.</b> Lo
 * que aquí se comprueba es qué queda comprometido cuando la segunda escritura falla, y con una
 * transacción de prueba envolviéndolo todo eso no se puede observar: la de {@code guardar} se
 * uniría a la de la prueba y el desenlace lo decidiría el rollback del final.
 *
 * <p>El defecto que cierra lo levantó una revisión adversarial: {@code guardar} escribía la emisión
 * y sus envíos en dos transacciones distintas, así que una instancia que muriera en medio dejaba
 * una emisión {@code EN_CURSO} con cero envíos — un estado que el agregado rechaza al
 * reconstruirse—, y esa sola fila detenía el despacho automático de todos los pedidos.
 */
@SpringBootTest
@Testcontainers
class RepositorioEmisionesJpaAtomicidadTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioEmisionesJpa repositorio;
  @Autowired private RepositorioPedidosJpa pedidos;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transacciones;

  private static final Direccion MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null);
  private static final Instant AHORA = Instant.parse("2026-09-18T15:00:00Z");

  private UUID sembrarPedido(int secuencial) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, secuencial),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CEL-1"),
                    "Celular de prueba",
                    1,
                    Dinero.deCop(120_000),
                    BigDecimal.ZERO,
                    null,
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.guardar(pedido);
    return pedido.id();
  }

  private static EmisionDeGuia enCurso(UUID pedidoId, String idTarifa, String... envios) {
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedidoId, "Servientrega", idTarifa, "admin:test", AHORA);
    emision.aceptada(List.of(envios), AHORA);
    return emision;
  }

  private long filasDeEmision(UUID emisionId) {
    return ((Number)
            entityManager
                .createNativeQuery("select count(*) from emision_de_guia where id = :id")
                .setParameter("id", emisionId)
                .getSingleResult())
        .longValue();
  }

  /**
   * Si la segunda escritura falla, la primera no queda comprometida.
   *
   * <p>Se fuerza el fallo con un identificador de envío ya usado, que es lo que el índice único de
   * {@code envio_en_plataforma} prohíbe. Lo que importa no es el error —ese ya se traducía— sino
   * que <b>la fila de la emisión no exista después</b>: si existiera, sería justo la emisión
   * ilegible que detiene el despacho de todos los pedidos.
   *
   * <p>Se cuenta con SQL directo y no con {@code buscarDePedido}, y la diferencia es todo el
   * sentido de la prueba: esa consulta ahora se salta las filas ilegibles, así que devolvería vacío
   * tanto si la fila no está como si está y no se puede leer. Contra la tabla no hay ambigüedad.
   */
  @Test
  void siFallaLaSegundaEscrituraNoQuedaLaPrimera() {
    UUID pedidoUno = sembrarPedido(900);
    UUID pedidoDos = sembrarPedido(901);
    repositorio.guardar(enCurso(pedidoUno, "rate-900", "envio-compartido"));

    EmisionDeGuia chocara = enCurso(pedidoDos, "rate-901", "envio-compartido");

    assertThatThrownBy(() -> repositorio.guardar(chocara)).isInstanceOf(RuntimeException.class);

    assertThat(filasDeEmision(chocara.id())).isZero();
  }

  /**
   * Y una fila ilegible que ya existiera no puede seguir tumbando la consulta entera.
   *
   * <p>Se siembra a mano lo que el defecto producía —{@code EN_CURSO} con cero envíos— y se
   * comprueba que la tarea sigue viendo las demás. Segunda mitad de la defensa: lo de arriba impide
   * crearlas, esto impide que una que ya exista detenga el despacho de todos los demás pedidos.
   */
  @Test
  void unaEmisionIlegibleNoTumbaLaConsultaDeLasDemas() {
    UUID pedidoSano = sembrarPedido(902);
    EmisionDeGuia sana = enCurso(pedidoSano, "rate-902", "envio-902");
    repositorio.guardar(sana);

    UUID pedidoRoto = sembrarPedido(903);
    UUID emisionRota = UUID.randomUUID();
    // Una sentencia de escritura necesita transacción propia, y esta clase no tiene una de prueba
    // envolviéndolo todo — que es justamente el punto de que sea una clase aparte.
    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado ->
                entityManager
                    .createNativeQuery(
                        """
            insert into emision_de_guia
              (id, pedido_id, transportadora, id_tarifa, actor, estado, detalle, solicitada_en,
               resuelta_en)
            values (:id, :pedido, 'Servientrega', 'rate-903', 'admin:test', 'EN_CURSO', null,
                    :ahora, null)
            """)
                    .setParameter("id", emisionRota)
                    .setParameter("pedido", pedidoRoto)
                    .setParameter("ahora", AHORA)
                    .executeUpdate());

    List<EmisionDeGuia> enCurso = repositorio.buscarEnCurso(10);

    assertThat(enCurso)
        .extracting(EmisionDeGuia::id)
        .contains(sana.id())
        .doesNotContain(emisionRota);
  }
}
