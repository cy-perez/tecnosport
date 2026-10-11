package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/**
 * Mientras un tramo trabaja, algo tiene que avisar cada pocos segundos que el trabajador sigue vivo
 * ({@link RegistrarLatidoDeIngesta}), y desde otro hilo: entre dos puntos de control del trabajo
 * —subir dos mil fotos, una extracción con reintentos— pueden pasar minutos.
 */
public interface MarcapasosDeIngesta {

  /** Empieza a latir por el lote; deja de hacerlo al cerrar lo que devuelve. */
  Latido latirPor(UUID loteId);

  /** El latido en curso. Cerrarlo dos veces no hace nada. */
  interface Latido extends AutoCloseable {

    @Override
    void close();
  }
}
