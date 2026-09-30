package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.PHash;
import java.util.Optional;

/**
 * Quien decodifica una foto y la reduce a la luminancia que {@link PHash} necesita. Vacío cuando
 * los bytes no son una imagen que se pueda leer: eso no es un error del lote, es una foto sin
 * huella.
 */
public interface CalculadorDePHash {

  Optional<PHash> de(byte[] imagen);
}
