package co.tecnosport.api.infrastructure.envio.siembra;

import co.tecnosport.api.application.envio.EmisorDeGuias;
import co.tecnosport.api.application.envio.LecturaDeEnvioEmitido;
import co.tecnosport.api.application.envio.ResultadoCancelacion;
import co.tecnosport.api.application.envio.ResultadoEmision;
import co.tecnosport.api.application.envio.SolicitudDeEmision;
import java.util.Objects;

/**
 * El emisor de guías bajo el perfil {@code e2e}, que no emite ninguna. Hermano de {@link
 * CotizadorEnvioSembrado}, y con un porqué distinto: aquel existe para que el recorrido pueda
 * comprar a domicilio; este existe para que el contexto arranque.
 *
 * <p><strong>Por qué hace falta.</strong> Bajo {@code e2e} no se registra {@code SkydropxClient}, y
 * con él desaparecen sus tres puertos a la vez. {@code EmitirGuiaDePedido}, {@code
 * ResolverEmisionesEnCurso} y la tarea que la llama piden {@link EmisorDeGuias} sin condición de
 * perfil, así que sin este doble la aplicación no refresca el contexto y el recorrido entero muere
 * en el arranque, lejos de lo que estaba probando. Pasó en el merge de la emisión: cinco minutos de
 * espera para descubrir que faltaba un bean.
 *
 * <p><strong>Por qué rechaza en vez de inventar una guía.</strong> Ningún recorrido de Playwright
 * pide la guía —la emisión se dispara desde el panel, que no se recorre—, así que estos dos métodos
 * no deberían ejecutarse nunca. Devolver un número de guía sintético haría pasar en silencio al
 * recorrido que algún día llegue hasta aquí, y ese silencio es justo lo que no queremos: un pedido
 * despachado contra una guía que no existe. Rechazar deja el escenario detenido donde de verdad
 * está detenido — sin credenciales del proveedor—, que es lo que un despliegue sin credenciales
 * haría. Es el mismo criterio del costo 12.345 del cotizador: el doble se delata solo.
 *
 * <p><strong>{@code cancelar} sí responde que canceló</strong>, y rompe la simetría a propósito.
 * Los otros dos se niegan porque decir que sí sería mentir sobre algo que cuesta plata; aquí es al
 * revés. Bajo este perfil no se emitió ninguna guía, así que no hay ninguna viva: decir "no se
 * pudo" mandaría a la bandeja de revisión un recorrido que cancela un pedido, y ese aviso sería el
 * falso. Cancelar lo que no existe es exactamente lo que ocurrió.
 *
 * <p>{@code consultar} devuelve {@link LecturaDeEnvioEmitido.NoSeSabe} por lo mismo. La tarea de
 * resolución corre cada minuto bajo este perfil igual que en producción; si no hay emisiones en
 * curso no llega a preguntar nada, y si alguna quedara sembrada, "no pudimos preguntar" es la única
 * respuesta cierta: nadie cobró nada y no hay envío del que leer un desenlace.
 */
public final class EmisorDeGuiasSembrado implements EmisorDeGuias {

  private static final String DETALLE =
      "Emisor de escenario (perfil e2e): no hay credenciales de Skydropx y no se emite ninguna"
          + " guía.";

  @Override
  public ResultadoEmision emitir(SolicitudDeEmision solicitud) {
    Objects.requireNonNull(solicitud, "La solicitud de emisión no puede ser nula.");
    return new ResultadoEmision.Rechazada(ResultadoEmision.Motivo.SIN_CREDENCIALES, DETALLE);
  }

  @Override
  public LecturaDeEnvioEmitido consultar(String idEnvioEnPlataforma) {
    Objects.requireNonNull(
        idEnvioEnPlataforma, "El id del envío en la plataforma no puede ser nulo.");
    return new LecturaDeEnvioEmitido.NoSeSabe();
  }

  @Override
  public ResultadoCancelacion cancelar(String idEnvioEnPlataforma) {
    Objects.requireNonNull(
        idEnvioEnPlataforma, "El id del envío en la plataforma no puede ser nulo.");
    return new ResultadoCancelacion.Cancelada();
  }
}
