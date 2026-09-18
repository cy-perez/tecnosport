package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Optional;

/**
 * Cuánto crédito le queda a la cuenta de la plataforma de envíos.
 *
 * <p>Existe porque <strong>sin saldo no hay guías</strong>, y hasta hoy nadie se enteraría: el
 * desenlace era un despacho que falla con un pedido ya cobrado esperándolo. El saldo no es un dato
 * que se deduzca de nuestro lado —la plataforma cobra cada guía, reembolsa las que mueren y admite
 * recargas que nadie registra aquí— así que hay que preguntárselo.
 *
 * <p><strong>Vacío es "no se pudo preguntar", no "no hay saldo".</strong> Los dos llevarían a mirar
 * la cuenta, pero solo uno significa que el despacho está detenido, y un proveedor caído que se
 * cuente como cuenta vacía convierte cada rato de indisponibilidad en una alarma de dinero. Por eso
 * el puerto no falla abierto ni cerrado: no concluye.
 */
public interface ConsultorDeSaldo {

  /** El crédito disponible, o vacío si la plataforma no contestó. */
  Optional<Dinero> saldo();
}
