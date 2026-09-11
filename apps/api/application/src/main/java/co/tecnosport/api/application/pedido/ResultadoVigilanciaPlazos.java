package co.tecnosport.api.application.pedido;

/**
 * Qué hizo una vuelta del vigilante del plazo de entrega.
 *
 * <p>{@code revisados} son los que la consulta trajo —vivos, sin aviso y con más de treinta días
 * encima— y {@code avisados} los que de verdad habían vencido. Los dos, y no solo el segundo,
 * porque la diferencia entre ellos es lo que dice si el filtro grueso está trayendo de más.
 */
public record ResultadoVigilanciaPlazos(int revisados, int avisados) {}
