package co.tecnosport.api.application.envio;

/**
 * El puerto de la emisión de la guía (adr/0033). La única implementación real es el cliente de
 * Skydropx, en {@code infrastructure}; aquí no aparece su nombre por ninguna parte.
 *
 * <p><strong>Dos métodos y no uno, porque crear no es emitir.</strong> {@link #emitir} pide los
 * envíos y la plataforma cobra ahí mismo, pero devuelve la guía en {@code null}: el número aparece
 * después, y hay que ir a buscarlo con {@link #consultar}. Un puerto de un solo método —"emíteme la
 * guía"— obligaría al adaptador a sondear por dentro y a bloquear a quien llame durante minutos, o
 * a mentir devolviendo una guía vacía. Ninguna de las dos es aceptable con dinero de por medio.
 *
 * <p>A diferencia de {@link CotizadorEnvio}, este puerto <strong>no falla abierto</strong>. El
 * cotizador puede devolver "sin tarifas" y el checkout sigue vendiendo con recogida en el punto;
 * aquí no hay equivalente: si no se sabe qué pasó con un envío que ya se cobró, decirlo es la única
 * respuesta honesta, y por eso {@link LecturaDeEnvioEmitido} tiene un caso para eso.
 */
public interface EmisorDeGuias {

  /** Pide las guías. Que responda {@code Aceptada} significa que se cobró, no que haya guía. */
  ResultadoEmision emitir(SolicitudDeEmision solicitud);

  /** Relee un envío que ya se aceptó, para ver si al fin tiene guía o si murió. */
  LecturaDeEnvioEmitido consultar(String idEnvioEnPlataforma);
}
