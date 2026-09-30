package co.tecnosport.api.domain.proveedores;

/**
 * En qué punto está una publicación: armada, ya extraída, descartada por no ser producto, o rota.
 */
public enum EstadoPublicacionProveedor {
  PENDIENTE_EXTRACCION,
  EXTRAIDA,
  DESCARTADA,
  ERROR
}
