package co.tecnosport.api.domain.envio;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Una guía: un paquete, una transportadora, un cobro y su propio rastro de eventos (adr/0031).
 *
 * <p>Existe porque <strong>ninguna transportadora colombiana admite multipaquete</strong> —los
 * siete servicios de la cuenta dicen {@code multi_packages_enabled: false}
 * (docs/13-skydropx-capacidades.md §6.3)— y la regla de un bulto por variante ya está tomada. Un
 * pedido de dos variantes son dos guías, y cada una se mueve sola: se recoge, se entrega o se
 * devuelve por su cuenta, y se cobra aparte.
 *
 * <p>El costo va aquí y no en el {@code Envio} por eso mismo: con dos bultos la cotización cambia a
 * {@code multishipment} y cobra el doble. Sumar los costos en el envío es leer el margen del
 * pedido; repartirlo a mano sería inventarlo.
 */
public final class GuiaEnvio {

  private final UUID id;
  private final String transportadora;
  private final String codigoTransportadora;
  private final String numero;
  private final Dinero costo;
  private final String urlEtiqueta;
  private final List<EventoSeguimiento> eventos;

  public GuiaEnvio(
      UUID id,
      String transportadora,
      String codigoTransportadora,
      String numero,
      Dinero costo,
      String urlEtiqueta,
      List<EventoSeguimiento> eventos) {
    this.id = Objects.requireNonNull(id, "El id de la guía no puede ser nulo.");
    if (transportadora == null || transportadora.isBlank()) {
      throw new ExcepcionDeDominio("La transportadora no puede estar vacía.");
    }
    this.transportadora = transportadora;
    this.codigoTransportadora =
        codigoTransportadora == null || codigoTransportadora.isBlank()
            ? null
            : codigoTransportadora.trim();
    if (numero == null || numero.isBlank()) {
      throw new ExcepcionDeDominio("La guía no puede estar vacía.");
    }
    this.numero = numero;
    this.costo = Objects.requireNonNull(costo, "El costo de la guía no puede ser nulo.");
    this.urlEtiqueta = urlEtiqueta == null || urlEtiqueta.isBlank() ? null : urlEtiqueta.trim();
    this.eventos = new ArrayList<>(Objects.requireNonNullElse(eventos, List.of()));
  }

  /**
   * Una guía que alguien tecleó en el panel: sabemos con qué transportadora va porque lo escribió
   * una persona, y no con qué código la conoce la plataforma. No se puede consultar su rastreo, y
   * su rótulo lo imprimió quien la emitió por fuera.
   */
  public static GuiaEnvio crear(String transportadora, String numero, Dinero costo) {
    return new GuiaEnvio(
        GeneradorIdentificador.nuevo(), transportadora, null, numero, costo, null, List.of());
  }

  /**
   * Una guía emitida por la plataforma, que sí sabe con qué código consultarla y de la que suele
   * haber rótulo.
   *
   * <p>{@code urlEtiqueta} admite nulo <strong>a propósito</strong>: la etiqueta no está
   * garantizada. Dos guías de Servientrega emitidas por el mismo camino, una trajo {@code
   * label_url} y la otra no la trajo nunca, ni con el envío ya entregado
   * (docs/13-skydropx-capacidades.md §6.7). Qué lo decide sigue sin saberse, así que quien despache
   * no puede dar por hecho el rótulo.
   */
  public static GuiaEnvio emitida(
      String transportadora,
      String codigoTransportadora,
      String numero,
      Dinero costo,
      String urlEtiqueta) {
    return new GuiaEnvio(
        GeneradorIdentificador.nuevo(),
        transportadora,
        codigoTransportadora,
        numero,
        costo,
        urlEtiqueta,
        List.of());
  }

  /**
   * Registra un movimiento del paquete. <strong>Append-only</strong>: nada se sobrescribe y nada se
   * borra (adr/0022).
   *
   * <p>Idempotente por el identificador del evento en la plataforma, que es lo que hace inofensivo
   * un reintento del webhook: el mismo evento dos veces se guarda una. Devuelve si el evento era
   * nuevo, porque de eso depende que el caso de uso mueva o no el pedido — aplicar dos veces un
   * {@code ENTREGADO} reabriría plazos legales que ya estaban corriendo.
   *
   * <p>No valida el orden. Las transportadoras mandan eventos desordenados y con retraso, y
   * rechazar uno "viejo" sería perder justo el que faltaba para entender qué pasó.
   */
  public boolean registrarEvento(EventoSeguimiento evento) {
    Objects.requireNonNull(evento, "El evento no puede ser nulo.");
    boolean yaEstaba = eventos.stream().anyMatch(e -> e.idExterno().equals(evento.idExterno()));
    if (yaEstaba) {
      return false;
    }
    eventos.add(evento);
    return true;
  }

  /** En el orden en que ocurrieron, no en el que llegaron. */
  public List<EventoSeguimiento> eventos() {
    return eventos.stream().sorted(Comparator.comparing(EventoSeguimiento::ocurrioEn)).toList();
  }

  /**
   * El último estado conocido de este paquete, o vacío si todavía no hay eventos — una guía recién
   * emitida, o una cuyo webhook no ha llegado.
   */
  public Optional<EstadoEnvio> ultimoEstado() {
    return ultimoEvento().map(EventoSeguimiento::estado);
  }

  /**
   * El último movimiento entero, no solo su estado. Lo pide la bandeja de revisión: para decidir si
   * una guía quieta sigue pidiendo ojo humano hace falta <em>cuándo nos enteramos</em> —{@link
   * EventoSeguimiento#recibidoEn()}, que es nuestro reloj— y para mostrarla hace falta lo que la
   * transportadora dijo.
   *
   * <p>"Último" es por {@code ocurrioEn}, igual que {@link #ultimoEstado()} y por la misma razón:
   * las transportadoras mandan eventos desordenados, y el orden de llegada no es el de los hechos.
   */
  public Optional<EventoSeguimiento> ultimoEvento() {
    return eventos().stream().reduce((primero, siguiente) -> siguiente);
  }

  /**
   * ¿La historia de este paquete terminó? De un entregado, devuelto, cancelado o destruido no va a
   * llegar nada más, y la conciliación deja de preguntar por él.
   *
   * <p>Se pregunta por guía y no por envío a propósito: con dos guías, una entregada y otra en
   * tránsito, dar el envío por terminado dejaría la segunda sin conciliar para siempre.
   */
  public boolean terminada() {
    return ultimoEstado().map(EstadoEnvio::esTerminal).orElse(false);
  }

  public UUID id() {
    return id;
  }

  public String transportadora() {
    return transportadora;
  }

  /**
   * El nombre con el que la plataforma conoce a la transportadora, que no es el que se le muestra a
   * nadie: {@code ninetynineminutes} para "99 minutes", {@code servientrega} para "Servientrega".
   * Medido contra la cuenta el 16 de septiembre de 2026 — el rastreo exige ese código y responde
   * 404 con el nombre visible, así que no se puede derivar de {@link #transportadora()}.
   *
   * <p>Vacío cuando la guía la tecleó una persona en el panel. No es un dato que falte por
   * descuido: una guía escrita a mano <strong>puede no existir en la plataforma</strong>, porque
   * quien despacha pudo emitirla en la web de la transportadora. Lo que decide si se puede
   * conciliar no es quién la lleva, es si la emitimos nosotros.
   */
  public Optional<String> codigoTransportadora() {
    return Optional.ofNullable(codigoTransportadora);
  }

  /**
   * ¿Se le puede preguntar a la plataforma por esta guía? Solo si sabemos con qué código la conoce.
   * La conciliación salta las demás y lo cuenta, en vez de preguntar con un código inventado y
   * recibir un 404 que parecería "sin novedad".
   */
  public boolean conciliable() {
    return codigoTransportadora != null;
  }

  public String numero() {
    return numero;
  }

  public Dinero costo() {
    return costo;
  }

  /**
   * El rótulo que se pega a la caja, cuando la plataforma lo devolvió. Vacío en las guías tecleadas
   * a mano —que se imprimieron por fuera— y también en algunas emitidas por nosotros: ver {@link
   * #emitida}.
   */
  public Optional<String> urlEtiqueta() {
    return Optional.ofNullable(urlEtiqueta);
  }
}
