package co.tecnosport.api.presentation.proveedores.dto;

import java.util.UUID;

/**
 * La marca y la categoría del catálogo. Obligatorias para un modelo nuevo —el caso de uso lo exige—
 * e ignoradas cuando el borrador completa un producto que ya existe, que conserva las suyas.
 */
public record AprobarBorradorTecnologiaPeticion(UUID marcaId, UUID categoriaId) {}
