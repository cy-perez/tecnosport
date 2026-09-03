package co.tecnosport.api.presentation.pago;

import co.tecnosport.api.application.pago.IntentoDePago;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import co.tecnosport.api.presentation.pago.dto.IntentoDePagoRespuesta;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class MapeadorRespuestasPago {

  private final PropiedadesWompiPublicas propiedadesWompi;

  public MapeadorRespuestasPago(PropiedadesWompiPublicas propiedadesWompi) {
    this.propiedadesWompi = Objects.requireNonNull(propiedadesWompi);
  }

  public IntentoDePagoRespuesta aRespuesta(IntentoDePago intento) {
    return new IntentoDePagoRespuesta(
        intento.referencia().valor(),
        aRespuesta(intento.monto()),
        intento.firmaIntegridad(),
        propiedadesWompi.llavePublica(),
        propiedadesWompi.ambiente());
  }

  private DineroRespuesta aRespuesta(Dinero dinero) {
    return new DineroRespuesta(dinero.valor().longValueExact(), Dinero.MONEDA);
  }
}
