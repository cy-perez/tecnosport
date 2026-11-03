package co.tecnosport.api.bootstrap.compartido;

import static co.tecnosport.api.application.compartido.TareaProgramada.de;

import co.tecnosport.api.application.compartido.TareaProgramada;
import co.tecnosport.api.bootstrap.carrito.TareaPurgaCarritos;
import co.tecnosport.api.bootstrap.correo.TareaBandejaDeSalida;
import co.tecnosport.api.bootstrap.envio.TareaAvisoDeRevisionPendiente;
import co.tecnosport.api.bootstrap.envio.TareaConciliacionEnvios;
import co.tecnosport.api.bootstrap.envio.TareaResolucionEmisiones;
import co.tecnosport.api.bootstrap.envio.TareaVigilanciaDeSaldo;
import co.tecnosport.api.bootstrap.envio.TareaVigilanciaDeSobrecostos;
import co.tecnosport.api.bootstrap.pago.TareaConciliacionSistecredito;
import co.tecnosport.api.bootstrap.pago.TareaConciliacionWompi;
import co.tecnosport.api.bootstrap.pedido.TareaAvisoDePlazoDeEntrega;
import co.tecnosport.api.bootstrap.pedido.TareaComprobantesDeCompra;
import co.tecnosport.api.bootstrap.pedido.TareaVencerContraentregas;
import co.tecnosport.api.bootstrap.pedido.TareaVencerRecogidas;
import co.tecnosport.api.bootstrap.proveedores.TareaExpiracionDisponibilidad;
import co.tecnosport.api.bootstrap.proveedores.TareaIngestasAbandonadas;
import co.tecnosport.api.bootstrap.usuario.TareaPurgaDesafiosSegundoFactor;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Las tareas que corre el tick, con su nombre en la bitácora y su intervalo (ADR-0104). Cada {@code
 * Tarea*} sigue sabiendo hacer su trabajo; aquí solo se dice cómo se llama y cada cuánto corre.
 *
 * <p><b>El nombre es la llave de {@code ejecucion_de_tarea} y no se cambia</b>: una tarea
 * renombrada nace sin historial. {@code TareasProgramadasRegistradasTest} los fija.
 *
 * <p>El orden es el del tick: primero lo que mueve dinero y reservas, después los avisos y al final
 * las purgas. Un tick largo hace esperar a las de abajo, no a las de arriba.
 */
@Configuration
public class CatalogoDeTareasProgramadas {

  // ── Dinero y reservas ─────────────────────────────────────────────────────────────────────────

  @Bean
  @Order(10)
  TareaProgramada conciliacionWompi(
      TareaConciliacionWompi tarea,
      @Value("${tecnosport.wompi.conciliacion.intervalo-minutos}") long minutos) {
    return de("conciliacion-wompi", Duration.ofMinutes(minutos), tarea::conciliar);
  }

  @Bean
  @Order(20)
  TareaProgramada conciliacionSistecredito(
      TareaConciliacionSistecredito tarea,
      @Value("${tecnosport.sistecredito.conciliacion.intervalo-minutos}") long minutos) {
    return de("conciliacion-sistecredito", Duration.ofMinutes(minutos), tarea::conciliar);
  }

  @Bean
  @Order(30)
  TareaProgramada resolucionEmisiones(
      TareaResolucionEmisiones tarea,
      @Value("${tecnosport.skydropx.emision.intervalo-segundos}") long segundos) {
    return de("resolucion-emisiones", Duration.ofSeconds(segundos), tarea::resolver);
  }

  @Bean
  @Order(40)
  TareaProgramada vencerContraentregas(
      TareaVencerContraentregas tarea,
      @Value("${tecnosport.contraentrega.vencimiento.intervalo-minutos}") long minutos) {
    return de("vencer-contraentregas", Duration.ofMinutes(minutos), tarea::vencer);
  }

  @Bean
  @Order(50)
  TareaProgramada vencerRecogidas(
      TareaVencerRecogidas tarea,
      @Value("${tecnosport.retiro-en-punto.vencimiento-intervalo-minutos}") long minutos) {
    return de("vencer-recogidas", Duration.ofMinutes(minutos), tarea::vencer);
  }

  @Bean
  @Order(60)
  TareaProgramada conciliacionEnvios(
      TareaConciliacionEnvios tarea,
      @Value("${tecnosport.skydropx.seguimiento.intervalo-minutos}") long minutos) {
    return de("conciliacion-envios", Duration.ofMinutes(minutos), tarea::conciliar);
  }

  // ── Correo e ingesta ──────────────────────────────────────────────────────────────────────────

  @Bean
  @Order(110)
  TareaProgramada comprobantesDeCompra(
      TareaComprobantesDeCompra tarea,
      @Value("${tecnosport.pedido.comprobantes.intervalo-minutos}") long minutos) {
    return de("comprobantes-de-compra", Duration.ofMinutes(minutos), tarea::enviar);
  }

  @Bean
  @Order(120)
  TareaProgramada bandejaDeSalida(
      TareaBandejaDeSalida tarea,
      @Value("${tecnosport.correo.bandeja.intervalo-minutos}") long minutos) {
    return de("bandeja-de-salida", Duration.ofMinutes(minutos), tarea::drenar);
  }

  /** Cada minuto y sin variable, como cuando era {@code @Scheduled}: es la red de seguridad. */
  @Bean
  @Order(130)
  TareaProgramada ingestasAbandonadas(TareaIngestasAbandonadas tarea) {
    return de("ingestas-abandonadas", Duration.ofMinutes(1), tarea::recuperarAbandonadas);
  }

  // ── Vigilantes ────────────────────────────────────────────────────────────────────────────────

  @Bean
  @Order(210)
  TareaProgramada avisoPlazoDeEntrega(
      TareaAvisoDePlazoDeEntrega tarea,
      @Value("${tecnosport.pedido.vigilancia-plazo.intervalo-minutos}") long minutos) {
    return de("aviso-plazo-de-entrega", Duration.ofMinutes(minutos), tarea::vigilar);
  }

  @Bean
  @Order(220)
  TareaProgramada avisoRevisionEnvios(
      TareaAvisoDeRevisionPendiente tarea,
      @Value("${tecnosport.revision-envios.intervalo-minutos}") long minutos) {
    return de("aviso-revision-envios", Duration.ofMinutes(minutos), tarea::vigilar);
  }

  @Bean
  @Order(230)
  TareaProgramada vigilanciaSaldoEnvios(
      TareaVigilanciaDeSaldo tarea,
      @Value("${tecnosport.saldo-envios.intervalo-minutos}") long minutos) {
    return de("vigilancia-saldo-envios", Duration.ofMinutes(minutos), tarea::vigilar);
  }

  @Bean
  @Order(240)
  TareaProgramada vigilanciaSobrecostosEnvios(
      TareaVigilanciaDeSobrecostos tarea,
      @Value("${tecnosport.sobrecostos-envios.intervalo-minutos}") long minutos) {
    return de("vigilancia-sobrecostos-envios", Duration.ofMinutes(minutos), tarea::vigilar);
  }

  /** Con la misma bandera que la tarea: apagada, la tarea no existe y esto tampoco. */
  @Bean
  @Order(250)
  @ConditionalOnProperty(
      name = "tecnosport.proveedores.job-expiracion.habilitado",
      havingValue = "true",
      matchIfMissing = true)
  TareaProgramada expiracionDisponibilidad(
      TareaExpiracionDisponibilidad tarea,
      @Value("${tecnosport.proveedores.job-expiracion.intervalo}") Duration intervalo) {
    return de("expiracion-disponibilidad", intervalo, tarea::ocultarVencidos);
  }

  // ── Purgas ────────────────────────────────────────────────────────────────────────────────────

  @Bean
  @Order(310)
  TareaProgramada purgaIdempotencia(
      TareaPurgaIdempotencia tarea,
      @Value("${tecnosport.idempotencia.purga.intervalo-minutos}") long minutos) {
    return de("purga-idempotencia", Duration.ofMinutes(minutos), tarea::purgar);
  }

  @Bean
  @Order(320)
  TareaProgramada purgaCarritos(
      TareaPurgaCarritos tarea,
      @Value("${tecnosport.carrito.purga.intervalo-minutos}") long minutos) {
    return de("purga-carritos", Duration.ofMinutes(minutos), tarea::purgar);
  }

  @Bean
  @Order(330)
  TareaProgramada purgaDesafiosSegundoFactor(
      TareaPurgaDesafiosSegundoFactor tarea,
      @Value("${tecnosport.segundo-factor.purga.intervalo-minutos}") long minutos) {
    return de("purga-desafios-segundo-factor", Duration.ofMinutes(minutos), tarea::purgar);
  }
}
