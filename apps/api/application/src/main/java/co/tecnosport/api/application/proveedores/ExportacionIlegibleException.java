package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.FalloDeLote;
import co.tecnosport.api.domain.proveedores.MotivoDeFalloDeLote;
import java.util.Objects;

/**
 * La exportación no se puede leer, y leerla otra vez no lo va a cambiar.
 *
 * <p>Lleva dos cosas aparte: el {@link FalloDeLote} que queda en el lote y el panel traduce, y un
 * mensaje en español para el registro, que es lo que hace falta para diagnosticarlo y no sale al
 * panel (regla dura 4).
 */
public final class ExportacionIlegibleException extends RuntimeException {

  private final FalloDeLote fallo;

  public ExportacionIlegibleException(MotivoDeFalloDeLote motivo, String paraElRegistro) {
    this(new FalloDeLote(motivo, null), paraElRegistro, null);
  }

  public ExportacionIlegibleException(
      MotivoDeFalloDeLote motivo, String paraElRegistro, Throwable causa) {
    this(new FalloDeLote(motivo, null), paraElRegistro, causa);
  }

  public ExportacionIlegibleException(FalloDeLote fallo, String paraElRegistro, Throwable causa) {
    super(paraElRegistro, causa);
    this.fallo = Objects.requireNonNull(fallo);
  }

  public FalloDeLote fallo() {
    return fallo;
  }
}
