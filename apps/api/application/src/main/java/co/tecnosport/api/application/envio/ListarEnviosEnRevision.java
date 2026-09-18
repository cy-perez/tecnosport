package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EventoSeguimiento;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import co.tecnosport.api.domain.envio.TipoDeRevision;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * La bandeja: qué paquete necesita que alguien haga algo.
 *
 * <p>Existe porque {@code EstadoEnvio.exigeRevisionManual()} y {@code EstadoEmision.exigeOjoHumano}
 * llevaban desde que se escribieron sin que nada en producción los llamara. Cinco estados de envío
 * dejan el paquete quieto y dos de emisión dejan saldo comprometido; hasta hoy solo aparecían en un
 * {@code warn} del registro, que es tanto como no aparecer. Es la tarea que hace útil el rastreo y
 * la emisión: sin ella, el comprador se entera antes que el negocio (adr/0022, adr/0033).
 *
 * <p><strong>La consulta acota y el dominio decide.</strong> El repositorio trae los envíos que
 * <em>probablemente</em> tienen una guía quieta —un filtro grueso, que en un empate de fechas puede
 * traer alguno de más— y quién pide ojo humano lo dice {@code GuiaEnvio.ultimoEstado()}, que es la
 * autoridad sobre eso. Al revés —la regla escrita en SQL— habría dos definiciones de "último
 * estado" capaces de divergir, y la de la base no la prueba nadie.
 *
 * <p>Ordena por antigüedad del último movimiento, no por fecha de despacho: lo que más urge es lo
 * que lleva más tiempo quieto.
 */
public final class ListarEnviosEnRevision {

  private final RepositorioEnvios envios;
  private final RepositorioEmisiones emisiones;
  private final RepositorioAcusesDeRevision acuses;
  private final RepositorioPedidos pedidos;

  public ListarEnviosEnRevision(
      RepositorioEnvios envios,
      RepositorioEmisiones emisiones,
      RepositorioAcusesDeRevision acuses,
      RepositorioPedidos pedidos) {
    this.envios = Objects.requireNonNull(envios);
    this.emisiones = Objects.requireNonNull(emisiones);
    this.acuses = Objects.requireNonNull(acuses);
    this.pedidos = Objects.requireNonNull(pedidos);
  }

  public BandejaDeRevision ejecutar(int maximo) {
    return new BandejaDeRevision(guiasQuietas(maximo), emisionesSinDesenredar(maximo));
  }

  private List<GuiaEnRevision> guiasQuietas(int maximo) {
    List<Envio> candidatos = envios.buscarConGuiasEnRevision(maximo);

    Map<UUID, EventoSeguimiento> ultimosEventos = new HashMap<>();
    Map<UUID, Envio> envioDeGuia = new HashMap<>();
    for (Envio envio : candidatos) {
      for (GuiaEnvio guia : envio.guias()) {
        guia.ultimoEvento()
            .filter(evento -> evento.estado().exigeRevisionManual())
            .ifPresent(
                evento -> {
                  ultimosEventos.put(guia.id(), evento);
                  envioDeGuia.put(guia.id(), envio);
                });
      }
    }

    Map<UUID, Instant> revisadas =
        acuses.ultimaRevisionDe(TipoDeRevision.GUIA, ultimosEventos.keySet());

    List<GuiaEnRevision> pendientes = new ArrayList<>();
    for (Map.Entry<UUID, EventoSeguimiento> entrada : ultimosEventos.entrySet()) {
      UUID guiaId = entrada.getKey();
      EventoSeguimiento evento = entrada.getValue();
      Instant revisadaEn = revisadas.get(guiaId);
      if (yaSeAtendio(revisadaEn, evento)) {
        continue;
      }
      Envio envio = envioDeGuia.get(guiaId);
      GuiaEnvio guia =
          envio.guias().stream().filter(g -> g.id().equals(guiaId)).findFirst().orElseThrow();
      pendientes.add(
          new GuiaEnRevision(
              guiaId,
              guia.numero(),
              guia.transportadora(),
              envio.pedidoId(),
              numeroDe(envio.pedidoId()),
              evento.estado(),
              evento.descripcion(),
              evento.ocurrioEn(),
              evento.recibidoEn(),
              revisadaEn));
    }
    pendientes.sort(Comparator.comparing(GuiaEnRevision::recibidoEn));
    return pendientes;
  }

  /**
   * ¿Alguien ya miró <em>esto</em>, y no una versión anterior de esto?
   *
   * <p>La comparación es contra {@code recibidoEn} —cuándo nos enteramos— y nunca contra {@code
   * ocurrioEn}, que lo pone la transportadora. Son dos relojes distintos: un evento fechado con
   * desfase parecería anterior al acuse sin serlo, y el precio de equivocarse aquí es una guía en
   * excepción que desaparece de la bandeja sin que nadie la haya visto.
   *
   * <p>Empate incluido a propósito: un acuse exactamente simultáneo al evento se cuenta como
   * atendido. El caso real que produce ese empate es el acuse que se guarda en el mismo instante en
   * que la conciliación inserta el evento, y ahí quien miró la pantalla estaba viendo ese evento.
   */
  private static boolean yaSeAtendio(Instant revisadaEn, EventoSeguimiento evento) {
    return revisadaEn != null && !revisadaEn.isBefore(evento.recibidoEn());
  }

  /**
   * ¿El acuse puede esconder esta emisión?
   *
   * <p><b>Solo si ya no bloquea nada.</b> Para una guía, acusar es todo lo que se puede hacer: no
   * hay ninguna acción que la resuelva, así que el acuse la oculta y un evento posterior la
   * devuelve. Para una emisión no es igual — una {@code INDETERMINADA} <b>sigue abierta y sigue
   * impidiendo emitir la guía de ese pedido</b>, y existe una acción que sí la resuelve ({@code
   * ResolverEmisionIndeterminada}). Dejar que el acuse la escondiera convertía un "la miro mañana"
   * en un pedido pagado, con saldo posiblemente comprometido, que desaparecía de la única pantalla
   * y del único correo que lo nombraban. Lo levantó una revisión adversarial.
   *
   * <p>Las que no bloquean —{@code PARCIAL} y {@code SIN_ANULAR}— sí se esconden con el acuse: ahí
   * mirar y anotar es de verdad todo lo que hay que hacer.
   */
  private static boolean laEsconde(Map<UUID, Instant> revisadas, EmisionDeGuia emision) {
    return revisadas.containsKey(emision.id()) && !emision.estado().abierta();
  }

  private List<EmisionEnRevision> emisionesSinDesenredar(int maximo) {
    List<EmisionDeGuia> candidatas = emisiones.buscarQueExigenOjoHumano(maximo);
    Map<UUID, Instant> revisadas =
        acuses.ultimaRevisionDe(
            TipoDeRevision.EMISION, candidatas.stream().map(EmisionDeGuia::id).toList());

    return candidatas.stream()
        .filter(emision -> !laEsconde(revisadas, emision))
        .map(
            emision ->
                new EmisionEnRevision(
                    emision.id(),
                    emision.pedidoId(),
                    numeroDe(emision.pedidoId()),
                    emision.transportadora(),
                    emision.idTarifa(),
                    emision.estado(),
                    emision.detalle().orElse(null),
                    emision.enviosEnPlataforma(),
                    emision.solicitadaEn(),
                    emision.actor()))
        .sorted(Comparator.comparing(EmisionEnRevision::solicitadaEn))
        .toList();
  }

  /**
   * El número legible del pedido, o {@code null} si el pedido ya no está.
   *
   * <p>Un acuse no la deja nunca vacía en la práctica —una guía cuelga de un envío que cuelga de un
   * pedido— pero devolver {@code null} en vez de reventar es lo que corresponde en una pantalla de
   * diagnóstico: que falte una etiqueta no puede esconder el resto de la bandeja.
   */
  private String numeroDe(UUID pedidoId) {
    return pedidos
        .buscarPorId(pedidoId)
        .map(Pedido::numeroPedido)
        .map(NumeroPedido::valor)
        .orElse(null);
  }
}
