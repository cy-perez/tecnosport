package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.envio.AcuseDeRevision;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import java.util.Objects;

/**
 * Deja escrito que alguien miró una emisión con saldo comprometido, y con eso la saca de la
 * bandeja.
 *
 * <p><strong>No la resuelve, y no es un olvido.</strong> Una {@code INDETERMINADA} sigue abierta
 * después del acuse y sigue bloqueando una emisión nueva de ese pedido, que es justo lo que impide
 * pagar dos veces por lo mismo. Decidir que no hubo cobro y pasarla a {@code FALLIDA} es mover
 * plata: tiene que ser otra puerta, con su propia comprobación de que el envío no existe en la
 * plataforma. Quien tenga prisa despacha a mano, que es la salida que ya existe.
 *
 * <p>Exige que la emisión siga pidiendo ojo humano. Acusar una {@code EMITIDA} no significaría
 * nada, y dejaría una fila que sugiere que ahí hubo un problema que nunca existió.
 */
public final class AcusarRevisionDeEmision {

  private final RepositorioEmisiones emisiones;
  private final RepositorioAcusesDeRevision acuses;
  private final Reloj reloj;

  public AcusarRevisionDeEmision(
      RepositorioEmisiones emisiones, RepositorioAcusesDeRevision acuses, Reloj reloj) {
    this.emisiones = Objects.requireNonNull(emisiones);
    this.acuses = Objects.requireNonNull(acuses);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public AcuseDeRevision ejecutar(AcusarRevisionDeEmisionComando comando) {
    EmisionDeGuia emision =
        emisiones
            .buscarPorId(comando.emisionId())
            .orElseThrow(() -> new EmisionNoEncontradaException(comando.emisionId()));
    if (!emision.estado().exigeOjoHumano()) {
      throw new AcuseNoAplicableException(
          "La emisión " + emision.id() + " está en " + emision.estado() + " y no pide revisión.");
    }

    AcuseDeRevision acuse =
        AcuseDeRevision.deEmision(emision.id(), comando.actor(), comando.nota(), reloj.ahora());
    acuses.guardar(acuse);
    return acuse;
  }
}
