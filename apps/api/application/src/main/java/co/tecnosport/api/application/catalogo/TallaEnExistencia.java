package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;

/**
 * Un valor de talla tal como está escrito en una variante que hoy se puede comprar: publicada,
 * activa, no agotada por el proveedor y con saldo disponible. Sin normalizar: «m» y «M» llegan como
 * dos filas y las junta {@link ListarTallasDisponibles}.
 */
public record TallaEnExistencia(LineaCatalogo linea, String valor) {}
