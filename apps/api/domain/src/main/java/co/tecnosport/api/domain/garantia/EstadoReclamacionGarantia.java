package co.tecnosport.api.domain.garantia;

/**
 * Una reclamación se radica y se resuelve. No hay "rechazada": rechazar es un desenlace de la
 * solicitud de atención que la contiene, con su respuesta y su plazo; aquí lo que se registra es
 * qué se hizo con el producto cuando sí hubo algo que hacer.
 */
public enum EstadoReclamacionGarantia {
  RADICADA,
  RESUELTA
}
