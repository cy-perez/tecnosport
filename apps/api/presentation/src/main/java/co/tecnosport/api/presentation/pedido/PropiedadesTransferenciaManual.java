package co.tecnosport.api.presentation.pedido;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Las cuentas para transferencia manual (docs/11-pagos-y-envios.md), de docs/07-infra-gcp.md.
 *
 * <p><b>Eran cuatro campos sueltos y ahora son una lista</b> (28 de septiembre de 2026). Hasta ese
 * día el sitio enseñaba <b>una sola cuenta</b> —{@code banco}, {@code tipoCuenta}, {@code
 * numeroCuenta}, {@code titular}, todo en singular— y el pie ya nombraba tres medios de
 * transferencia, así que prometía a la vista algo que el checkout no podía dar. Hoy acepta Nequi,
 * Daviplata y una cuenta de ahorros de BBVA.
 *
 * <p><b>Qué cuentas hay se decide aquí y qué dicen se decide fuera.</b> La entidad y el tipo van
 * fijos en {@code application.yml} —no son secreto ni cambian: Nequi y Daviplata son billeteras,
 * BBVA es ahorros— y el número y el titular entran por variable de entorno, con nombres legibles
 * ({@code TRANSFERENCIA_NEQUI_NUMERO}) en vez de los índices que Spring exigiría para una lista
 * entera ({@code ..._CUENTAS_0_NUMERO}). Quien opera el despliegue tiene que poder leer lo que
 * escribe.
 *
 * <p><b>La lista no puede estar vacía.</b> Si lo estuviera, el checkout ofrecería "transferencia
 * bancaria" y la pantalla siguiente no tendría a dónde mandar a nadie — un método de pago que no se
 * puede completar. Falla al arrancar, que es donde un despliegue mal configurado tiene que fallar,
 * y no en el primer pedido.
 */
@ConfigurationProperties(prefix = "tecnosport.transferencia-manual")
public record PropiedadesTransferenciaManual(List<CuentaDeTransferencia> cuentas) {

  public PropiedadesTransferenciaManual {
    if (cuentas == null || cuentas.isEmpty()) {
      throw new IllegalStateException(
          "tecnosport.transferencia-manual.cuentas no puede estar vacío: el checkout ofrecería"
              + " transferencia bancaria sin ninguna cuenta a la que transferir.");
    }
    cuentas = List.copyOf(cuentas);
  }
}
