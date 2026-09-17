package co.tecnosport.api.bootstrap.envio;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.envio.AcusarRevisionDeEmision;
import co.tecnosport.api.application.envio.AcusarRevisionDeGuia;
import co.tecnosport.api.application.envio.AplicarEventoDeEnvio;
import co.tecnosport.api.application.envio.ArmadorDeBultos;
import co.tecnosport.api.application.envio.AvisarRevisionPendiente;
import co.tecnosport.api.application.envio.ConciliarEnvios;
import co.tecnosport.api.application.envio.ConciliarGuia;
import co.tecnosport.api.application.envio.ConsultorDeSeguimiento;
import co.tecnosport.api.application.envio.CotizadorEnvio;
import co.tecnosport.api.application.envio.CotizarEnvio;
import co.tecnosport.api.application.envio.EmisorDeGuias;
import co.tecnosport.api.application.envio.EmitirGuiaDePedido;
import co.tecnosport.api.application.envio.LectorEventoDeEnvio;
import co.tecnosport.api.application.envio.ListarEnviosEnRevision;
import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.RecibirEventoDeEnvio;
import co.tecnosport.api.application.envio.RepositorioAcusesDeRevision;
import co.tecnosport.api.application.envio.RepositorioAvisosDeRevision;
import co.tecnosport.api.application.envio.RepositorioEmisiones;
import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.application.envio.ResolverEmisionIndeterminada;
import co.tecnosport.api.application.envio.ResolverEmisionesEnCurso;
import co.tecnosport.api.application.envio.VerificadorFirmaEnvio;
import co.tecnosport.api.application.pedido.DespacharPedido;
import co.tecnosport.api.application.pedido.MarcarEntregado;
import co.tecnosport.api.application.pedido.RechazarEnEntrega;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.bootstrap.pago.PropiedadesMetodosDeWompi;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import co.tecnosport.api.infrastructure.envio.OrigenDespacho;
import co.tecnosport.api.infrastructure.envio.SkydropxClient;
import co.tecnosport.api.infrastructure.envio.VerificadorFirmaEnvioHmac;
import co.tecnosport.api.infrastructure.envio.siembra.CotizadorEnvioSembrado;
import co.tecnosport.api.infrastructure.envio.siembra.EmisorDeGuiasSembrado;
import co.tecnosport.api.presentation.envio.PropiedadesWebhookEnvio;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Mismo patrón que {@code ConfiguracionCarrito}/{@code ConfiguracionCatalogo}. */
@Configuration
@EnableConfigurationProperties({
  PropiedadesContraentrega.class,
  PropiedadesSkydropx.class,
  PropiedadesSeguimientoEnvios.class,
  PropiedadesVigilanciaRevision.class,
  PropiedadesWebhookEnvio.class,
  PropiedadesOrigen.class
})
public class ConfiguracionEnvio {

  /**
   * El cliente de Skydropx, que sirve dos puertos: cotiza (adr/0021) y consulta el rastreo de una
   * guía (adr/0022). <strong>Un solo bean para los dos</strong>, y el porqué está en la clase: el
   * token en caché y el límite de dos peticiones por segundo son de la cuenta, no de un caso de
   * uso. Se declara con el tipo concreto para que el contenedor pueda inyectarlo por cualquiera de
   * las dos interfaces.
   *
   * <p>{@code @Profile("!e2e")} y no {@code @ConditionalOnMissingBean}: los recorridos de
   * Playwright sustituyen este bean por {@link CotizadorEnvioSembrado}, y de las dos formas de
   * hacerlo esta es la que no se puede leer al revés. Con dos beans registrados y una precedencia,
   * alguien tiene que saber cuál gana; aquí solo existe uno de los dos y el contenedor no arranca
   * si el perfil dice otra cosa.
   */
  @Bean
  @Profile("!e2e")
  public SkydropxClient skydropxClient(
      PropiedadesSkydropx skydropx, PropiedadesOrigen origen, Reloj reloj) {
    return new SkydropxClient(
        URI.create(skydropx.urlBase()),
        skydropx.clientId(),
        skydropx.clientSecret(),
        new OrigenDespacho(
            origen.nombre(),
            origen.telefono(),
            origen.direccion(),
            origen.departamento(),
            origen.ciudad(),
            origen.ciudadDane(),
            origen.codigoPostal(),
            origen.barrio(),
            origen.referencia(),
            origen.correo()),
        Duration.ofSeconds(skydropx.cotizacionTimeoutSegundos()),
        skydropx.cotizacionIntentos(),
        INTERVALO_SONDEO,
        reloj);
  }

  /**
   * El cotizador de los recorridos de Playwright, y de nada más. El porqué entero está en {@link
   * CotizadorEnvioSembrado}; lo que importa aquí es que el perfil {@code e2e} no se activa en
   * ningún despliegue: {@code bootRun} fija {@code local}, el jar de Cloud Run no fija ninguno, y
   * el único sitio que pide {@code e2e} es el flujo {@code recorridos} de integración continua.
   */
  @Bean
  @Profile("e2e")
  public CotizadorEnvio cotizadorEnvioSembrado(Reloj reloj) {
    return new CotizadorEnvioSembrado(reloj);
  }

  /**
   * El consultor bajo {@code e2e}, que no consulta nada. Existe solo porque bajo ese perfil no hay
   * {@link SkydropxClient} del que colgarlo, y sin él el contexto no arranca. Devolver lista vacía
   * es lo correcto para un recorrido de Playwright: ahí no hay guía emitida en ninguna parte, y la
   * conciliación tiene que poder correr sin encontrar nada.
   */
  @Bean
  @Profile("e2e")
  public ConsultorDeSeguimiento consultorDeSeguimientoSembrado() {
    return (codigoTransportadora, guia) -> List.of();
  }

  /**
   * El emisor bajo {@code e2e}, que no emite nada, y por el mismo motivo que el consultor: sin
   * {@link SkydropxClient} no hay quien sirva el puerto, y tres beans lo exigen sin mirar el perfil
   * —{@code emitirGuiaDePedido}, {@code resolverEmisionesEnCurso} y la tarea que la llama—. Por qué
   * rechaza en vez de devolver una guía de mentira está en {@link EmisorDeGuiasSembrado}.
   */
  @Bean
  @Profile("e2e")
  public EmisorDeGuias emisorDeGuiasSembrado() {
    return new EmisorDeGuiasSembrado();
  }

  /**
   * Entre sondeo y sondeo. Por debajo del medio segundo no tiene sentido: el limitador de 2
   * peticiones por segundo lo frenaría igual, y el hilo esperaría en otro sitio.
   */
  private static final Duration INTERVALO_SONDEO = Duration.ofMillis(500);

  /**
   * La firma del webhook, con el algoritmo confirmado en la documentación oficial el 14 de
   * septiembre de 2026 (docs/13-skydropx-capacidades.md, sección 6.1).
   *
   * <p>Se cablea aquí, como {@link SkydropxClient}, y no con {@code @Component}: necesita el
   * secreto, y un adaptador que se anota a sí mismo tendría que ir a buscarlo.
   *
   * <p>Que el bean exista no significa que verifique: mientras {@code SKYDROPX_SECRETO_WEBHOOK} sea
   * el marcador de desarrollo, la firma nunca cuadra y todo evento se descarta. Lo que cambió es el
   * motivo — antes faltaba el algoritmo, ahora falta el secreto del panel, y eso es una variable de
   * entorno y no un despliegue.
   */
  @Bean
  public VerificadorFirmaEnvio verificadorFirmaEnvio(PropiedadesWebhookEnvio propiedades) {
    return new VerificadorFirmaEnvioHmac(propiedades.secreto());
  }

  /**
   * Lo que los dos caminos del seguimiento comparten (adr/0022, adr/0032): consultar el rastreo de
   * una guía y aplicar lo que traiga. El webhook y la tarea programada usan este mismo objeto, y no
   * dos parecidos, porque dos caminos con la misma responsabilidad y código distinto se separan el
   * día que alguien arregle uno solo.
   */
  @Bean
  public ConciliarGuia conciliarGuia(
      ConsultorDeSeguimiento consultor, AplicarEventoDeEnvio aplicarEvento) {
    return new ConciliarGuia(consultor, aplicarEvento);
  }

  /**
   * De los tres puertos del seguimiento ya no falla cerrado ninguno por falta de conocimiento: la
   * firma se resolvió el 14 de septiembre de 2026, el rastreo el 16 midiendo con {@code
   * tools/sonda-rastreo.mjs} sobre una guía emitida, y el lector del webhook con los ejemplos de la
   * documentación oficial (docs/13-skydropx-capacidades.md §6.8). Lo que falta para que el webhook
   * aplique algo es el secreto del panel, que es una variable de entorno.
   */
  @Bean
  public ConciliarEnvios conciliarEnvios(
      RepositorioEnvios repositorioEnvios,
      ConciliarGuia conciliarGuia,
      Reloj reloj,
      PropiedadesSeguimientoEnvios propiedades) {
    return new ConciliarEnvios(
        repositorioEnvios,
        conciliarGuia,
        reloj,
        Duration.ofHours(propiedades.antiguedadMinimaHoras()),
        propiedades.maximoPorCorrida());
  }

  @Bean
  public RecibirEventoDeEnvio recibirEventoDeEnvio(
      VerificadorFirmaEnvio verificadorFirma,
      LectorEventoDeEnvio lector,
      RepositorioEnvios repositorioEnvios,
      ConciliarGuia conciliarGuia) {
    return new RecibirEventoDeEnvio(verificadorFirma, lector, repositorioEnvios, conciliarGuia);
  }

  @Bean
  public AplicarEventoDeEnvio aplicarEventoDeEnvio(
      RepositorioEnvios repositorioEnvios,
      RepositorioPedidos repositorioPedidos,
      MarcarEntregado marcarEntregado,
      RechazarEnEntrega rechazarEnEntrega,
      Reloj reloj) {
    return new AplicarEventoDeEnvio(
        repositorioEnvios, repositorioPedidos, marcarEntregado, rechazarEnEntrega, reloj);
  }

  /**
   * Cómo se empaca un pedido, en un solo sitio. Lo usan la cotización y la emisión, y tienen que
   * armar los bultos <strong>en el mismo orden</strong>: la plataforma empareja los paquetes del
   * envío con los bultos de la cotización por posición.
   */
  @Bean
  public ArmadorDeBultos armadorDeBultos(RepositorioProductos repositorioProductos) {
    return new ArmadorDeBultos(repositorioProductos);
  }

  @Bean
  public CotizarEnvio cotizarEnvio(
      ArmadorDeBultos armadorDeBultos, CotizadorEnvio cotizadorEnvio, Reloj reloj) {
    return new CotizarEnvio(armadorDeBultos, cotizadorEnvio, reloj);
  }

  // `emitirGuiaDePedido` y `resolverEmisionesEnCurso` reciben `EnTransaccionPropia` porque las dos
  // escriben a los lados de una llamada que cobra: la fila tiene que estar confirmada antes, y el
  // desenlace tiene que sobrevivir a la excepción que sale después (adr/0033).

  @Bean
  public EmitirGuiaDePedido emitirGuiaDePedido(
      RepositorioPedidos repositorioPedidos,
      RepositorioEmisiones repositorioEmisiones,
      ArmadorDeBultos armadorDeBultos,
      CotizarEnvio cotizarEnvio,
      EmisorDeGuias emisorDeGuias,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj) {
    return new EmitirGuiaDePedido(
        repositorioPedidos,
        repositorioEmisiones,
        armadorDeBultos,
        cotizarEnvio,
        emisorDeGuias,
        enTransaccionPropia,
        reloj);
  }

  /**
   * Reusa el tope del seguimiento a propósito: las dos tareas hacen lo mismo contra el mismo límite
   * de dos peticiones por segundo, y tener dos números que significan lo mismo es tener dos números
   * que se van a desincronizar.
   */
  @Bean
  public ResolverEmisionesEnCurso resolverEmisionesEnCurso(
      RepositorioEmisiones repositorioEmisiones,
      EmisorDeGuias emisorDeGuias,
      DespacharPedido despacharPedido,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj,
      PropiedadesSeguimientoEnvios propiedades) {
    return new ResolverEmisionesEnCurso(
        repositorioEmisiones,
        emisorDeGuias,
        despacharPedido,
        enTransaccionPropia,
        reloj,
        propiedades.maximoPorCorrida());
  }

  @Bean
  public CriteriosContraentrega criteriosContraentrega(PropiedadesContraentrega propiedades) {
    Set<LineaCatalogo> categoriasExcluidas =
        propiedades.categoriasExcluidas().stream()
            .map(nombre -> LineaCatalogo.valueOf(nombre.trim().toUpperCase(Locale.ROOT)))
            .collect(Collectors.toSet());
    return new CriteriosContraentrega(
        propiedades.habilitada(),
        Dinero.deCop(propiedades.montoMinimo()),
        Dinero.deCop(propiedades.montoMaximo()),
        categoriasExcluidas);
  }

  /**
   * {@link PropiedadesMetodosDeWompi} la activa {@code ConfiguracionWompi} —es suya— y aquí solo se
   * inyecta el bean ya resuelto: el caso de uso vive en {@code application.envio} por la
   * contraentrega, pero lo que la pasarela tenga activado no es asunto del envío.
   */
  @Bean
  public MetodosDePagoDisponibles metodosDePagoDisponibles(
      RepositorioProductos repositorioProductos,
      CotizarEnvio cotizarEnvio,
      RepositorioPedidos repositorioPedidos,
      CriteriosContraentrega criteriosContraentrega,
      PropiedadesMetodosDeWompi metodosDeWompi) {
    return new MetodosDePagoDisponibles(
        repositorioProductos,
        cotizarEnvio,
        repositorioPedidos,
        criteriosContraentrega,
        metodosDeWompi.comoMetodosDePago());
  }

  /**
   * La bandeja de revisión y sus dos acuses. Son de solo lectura y de solo rastro: ninguno mueve
   * dinero ni estado, y por eso ninguno necesita {@code EnTransaccionPropia} como sí la necesita la
   * emisión.
   */
  @Bean
  public ListarEnviosEnRevision listarEnviosEnRevision(
      RepositorioEnvios repositorioEnvios,
      RepositorioEmisiones repositorioEmisiones,
      RepositorioAcusesDeRevision repositorioAcuses,
      RepositorioPedidos repositorioPedidos) {
    return new ListarEnviosEnRevision(
        repositorioEnvios, repositorioEmisiones, repositorioAcuses, repositorioPedidos);
  }

  @Bean
  public AcusarRevisionDeGuia acusarRevisionDeGuia(
      RepositorioEnvios repositorioEnvios,
      RepositorioAcusesDeRevision repositorioAcuses,
      Reloj reloj) {
    return new AcusarRevisionDeGuia(repositorioEnvios, repositorioAcuses, reloj);
  }

  @Bean
  public AcusarRevisionDeEmision acusarRevisionDeEmision(
      RepositorioEmisiones repositorioEmisiones,
      RepositorioAcusesDeRevision repositorioAcuses,
      Reloj reloj) {
    return new AcusarRevisionDeEmision(repositorioEmisiones, repositorioAcuses, reloj);
  }

  /**
   * La salida de una emisión indeterminada. Tampoco necesita {@code EnTransaccionPropia}: no llama
   * a la plataforma ni gasta saldo — registra lo que una persona vio en el panel de la plataforma.
   */
  @Bean
  public ResolverEmisionIndeterminada resolverEmisionIndeterminada(
      RepositorioEmisiones repositorioEmisiones,
      RepositorioAcusesDeRevision repositorioAcuses,
      Reloj reloj) {
    return new ResolverEmisionIndeterminada(repositorioEmisiones, repositorioAcuses, reloj);
  }

  /**
   * El vigilante de la bandeja. Reusa el caso de uso de la bandeja tal cual —no hay una segunda
   * definición de "qué está pendiente"— y el mismo tope del lote que el seguimiento: si lo que hay
   * que mirar no cabe ahí, el problema ya no es el correo.
   */
  @Bean
  public AvisarRevisionPendiente avisarRevisionPendiente(
      ListarEnviosEnRevision listarEnviosEnRevision,
      RepositorioAvisosDeRevision repositorioAvisos,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textosDeCorreo,
      Reloj reloj,
      PropiedadesVigilanciaRevision propiedades,
      PropiedadesSeguimientoEnvios seguimiento) {
    return new AvisarRevisionPendiente(
        listarEnviosEnRevision,
        repositorioAvisos,
        enviadorDeCorreo,
        textosDeCorreo,
        reloj,
        Duration.ofHours(propiedades.horasUmbral()),
        new CorreoElectronico(propiedades.destinatario()),
        seguimiento.maximoPorCorrida());
  }
}
