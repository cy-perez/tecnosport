package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.tecnologia.AgregarFotoABorradorTecnologiaComando;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Los archivos de una foto ya subidos al bucket privado, como los deja {@code
 * tools/subida-de-imagenes.mjs}: el mismo cuerpo que la confirmación de una imagen de galería.
 */
public record AgregarFotoBorradorTecnologiaPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<VariantePeticion> variantes,
    String objectKeyVistaPrevia,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer alto,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String hash,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String altEs,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String altEn) {

  public AgregarFotoBorradorTecnologiaPeticion {
    Objects.requireNonNull(variantes, "La foto trae sus anchos.");
    Objects.requireNonNull(alto, "La foto dice su alto.");
    Objects.requireNonNull(hash, "La foto dice el hash de su contenido.");
    Objects.requireNonNull(altEs, "La foto trae su texto alternativo en español.");
    Objects.requireNonNull(altEn, "La foto trae su texto alternativo en inglés.");
  }

  public AgregarFotoABorradorTecnologiaComando aComando(UUID borradorId) {
    return new AgregarFotoABorradorTecnologiaComando(
        borradorId,
        variantes.stream()
            .map(v -> new AgregarFotoABorradorTecnologiaComando.Variante(v.ancho(), v.objectKey()))
            .toList(),
        objectKeyVistaPrevia,
        alto,
        hash,
        altEs,
        altEn);
  }

  public record VariantePeticion(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer ancho,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String objectKey) {

    public VariantePeticion {
      Objects.requireNonNull(ancho, "Cada ancho dice cuánto mide.");
      Objects.requireNonNull(objectKey, "Cada ancho dice a qué clave se subió.");
    }
  }
}
