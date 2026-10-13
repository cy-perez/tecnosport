package co.tecnosport.api.presentation.pedido.dto;

/**
 * Qué talla pedirle al proveedor por una variante que la nombra distinto de como se vende
 * (ADR-0085).
 *
 * @param talla la del proveedor; nula si no se sabe —una talla agregada después de aprobar que el
 *     ajuste no alcanza a contar—, y entonces hay que preguntársela
 */
public record TallaAlProveedorRespuesta(String talla) {}
