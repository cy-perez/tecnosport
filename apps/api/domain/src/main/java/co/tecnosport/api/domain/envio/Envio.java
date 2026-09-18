package co.tecnosport.api.domain.envio;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * El despacho de un pedido: sus guías y el costo real (docs/02-modelo-datos.md). Nace en el
 * despacho, no antes. El recaudo de contraentrega (comisión de la transportadora, fecha de
 * conciliación) se registra aparte, con {@link #conciliarRecaudo}: la comisión es un costo real,
 * separado del flete, para que el margen del pedido sea verdadero (docs/11-pagos-y-envios.md).
 *
 * <p><strong>Un envío, varias guías</strong> (adr/0031). El envío es la unidad de dinero del pedido
 * —la comisión de recaudo es una por pedido, no una por paquete— y la {@link GuiaEnvio} es la
 * unidad de rastreo: cada paquete se mueve solo y trae su propio hilo de eventos. Nunca hay cero
 * guías: un despacho sin guía no es un despacho.
 */
public final class Envio {

  private final UUID id;
  private final UUID pedidoId;
  private final List<GuiaEnvio> guias;
  private final Instant despachadoEn;
  private Dinero comisionRecaudo;
  private Instant recaudoConciliadoEn;
  private ModalidadRecaudo modalidadRecaudo;

  public Envio(
      UUID id,
      UUID pedidoId,
      List<GuiaEnvio> guias,
      Instant despachadoEn,
      Dinero comisionRecaudo,
      Instant recaudoConciliadoEn,
      ModalidadRecaudo modalidadRecaudo) {
    this.id = Objects.requireNonNull(id, "El id del envío no puede ser nulo.");
    this.pedidoId = Objects.requireNonNull(pedidoId, "El id del pedido no puede ser nulo.");
    if (guias == null || guias.isEmpty()) {
      throw new ExcepcionDeDominio("Un envío tiene que llevar al menos una guía.");
    }
    if (guias.stream().map(GuiaEnvio::numero).distinct().count() != guias.size()) {
      throw new ExcepcionDeDominio("Un envío no puede repetir el número de una guía.");
    }
    this.guias = List.copyOf(guias);
    this.despachadoEn =
        Objects.requireNonNull(despachadoEn, "La fecha de despacho no puede ser nula.");
    this.comisionRecaudo = comisionRecaudo;
    this.recaudoConciliadoEn = recaudoConciliadoEn;
    this.modalidadRecaudo = modalidadRecaudo;
  }

  public static Envio crear(UUID pedidoId, List<GuiaEnvio> guias, Instant ahora) {
    return new Envio(GeneradorIdentificador.nuevo(), pedidoId, guias, ahora, null, null, null);
  }

  /**
   * Registra un movimiento en la guía a la que pertenece. Devuelve si el evento era nuevo, que es
   * de lo que depende que el caso de uso mueva o no el pedido.
   *
   * <p>Una guía que no es de este envío devuelve {@code false} en vez de lanzar, por lo mismo que
   * una guía desconocida no es un error en {@code AplicarEventoDeEnvio}: el webhook responde 200
   * igual, porque reintentarlo no lo va a arreglar.
   */
  public boolean registrarEvento(String numeroGuia, EventoSeguimiento evento) {
    Objects.requireNonNull(evento, "El evento no puede ser nulo.");
    return guiaDe(numeroGuia).map(guia -> guia.registrarEvento(evento)).orElse(false);
  }

  public List<GuiaEnvio> guias() {
    return guias;
  }

  public Optional<GuiaEnvio> guiaDe(String numero) {
    return guias.stream().filter(guia -> guia.numero().equals(numero)).findFirst();
  }

  /**
   * Lo que las transportadoras nos cobraron por este despacho: la suma de las guías. Con dos bultos
   * son dos guías y dos cobros (adr/0031), y es contra esta suma que se lee el margen del pedido.
   */
  public Dinero costoEnvio() {
    return Dinero.deCop(
        guias.stream().map(guia -> guia.costo().valor()).reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  /**
   * El último estado conocido del despacho: el evento más reciente de cualquiera de sus guías, o
   * vacío si ninguna se ha movido todavía.
   *
   * <p>Con varias guías no describe el pedido entero —una entregada y otra en tránsito dan {@code
   * ENTREGADO}— y por eso no decide nada: quien necesite el detalle mira {@link
   * GuiaEnvio#ultimoEstado()} guía por guía.
   */
  public Optional<EstadoEnvio> ultimoEstado() {
    return guias.stream()
        .flatMap(guia -> guia.eventos().stream())
        .max(Comparator.comparing(EventoSeguimiento::ocurrioEn))
        .map(EventoSeguimiento::estado);
  }

  /**
   * Idempotente por diseño: un envío ya conciliado rechaza un segundo intento.
   *
   * <p>La modalidad no la elige el sistema: se registra la que quien concilia vio en el panel de la
   * plataforma (ver {@link ModalidadRecaudo}). Lo único que el dominio comprueba es la consecuencia
   * que sí es suya — <b>los créditos no cobran comisión</b>, así que una conciliación a créditos
   * con un número encima está mal en una de las dos cosas y no se guarda a medias.
   */
  /**
   * ¿Todas las guías de este envío llegaron a {@code destino}?
   *
   * <p>Existe porque el pedido tiene <b>un</b> estado y el envío puede tener <b>varias</b> guías
   * (`adr/0031`: ninguna transportadora colombiana admite multipaquete, así que un pedido de dos
   * variantes son dos guías que se mueven solas). Antes, el primer evento que llegara movía el
   * pedido entero: con dos bultos, la entrega del primero arrancaba los cinco días hábiles del
   * retracto y el año de garantía sobre mercancía que el comprador todavía no tenía, y en
   * contraentrega daba por vendido y por cobrado un bulto en camino. Lo levantó una revisión
   * adversarial.
   *
   * <p>Un envío sin ninguna guía devuelve {@code false}: no hay nada que haya llegado.
   */
  public boolean todasLasGuiasEn(EstadoEnvio destino) {
    Objects.requireNonNull(destino, "El estado de destino no puede ser nulo.");
    return !guias.isEmpty()
        && guias.stream().allMatch(guia -> guia.ultimoEstado().filter(destino::equals).isPresent());
  }

  public void conciliarRecaudo(
      ModalidadRecaudo modalidadRecaudo, Dinero comisionRecaudo, Instant ahora) {
    Objects.requireNonNull(modalidadRecaudo, "La modalidad de recaudo no puede ser nula.");
    Objects.requireNonNull(comisionRecaudo, "La comisión de recaudo no puede ser nula.");
    Objects.requireNonNull(ahora, "La fecha de conciliación no puede ser nula.");
    if (recaudoConciliadoEn != null) {
      throw new ExcepcionDeDominio("El recaudo de este envío ya fue conciliado.");
    }
    if (!modalidadRecaudo.admiteComision() && comisionRecaudo.valor().signum() != 0) {
      throw new ExcepcionDeDominio(
          "El recaudo a créditos no cobra comisión, y este declara "
              + comisionRecaudo.valor()
              + ".");
    }
    this.modalidadRecaudo = modalidadRecaudo;
    this.comisionRecaudo = comisionRecaudo;
    this.recaudoConciliadoEn = ahora;
  }

  public Optional<ModalidadRecaudo> modalidadRecaudo() {
    return Optional.ofNullable(modalidadRecaudo);
  }

  public UUID id() {
    return id;
  }

  public UUID pedidoId() {
    return pedidoId;
  }

  public Instant despachadoEn() {
    return despachadoEn;
  }

  public Optional<Dinero> comisionRecaudo() {
    return Optional.ofNullable(comisionRecaudo);
  }

  public Optional<Instant> recaudoConciliadoEn() {
    return Optional.ofNullable(recaudoConciliadoEn);
  }
}
