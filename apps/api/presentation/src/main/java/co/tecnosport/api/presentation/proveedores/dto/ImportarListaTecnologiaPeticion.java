package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.tecnologia.ImportarListaDeTecnologiaComando;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.ModeloDeLista;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * La lista del día tal como la escribe {@code exportar_lista.py} de la skill de listas. Los dineros
 * van como entero de pesos.
 *
 * @param bloques los bloques de la lista que vinieron («ANDROID», «IPHONE»…). Solo informativo: qué
 *     desapareció ya lo decidió la skill con ellos
 */
public record ImportarListaTecnologiaPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate fechaLista,
    List<String> bloques,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ModeloPeticion> modelos,
    List<String> configuracionesDesaparecidas,
    List<String> modelosDesaparecidos) {

  public ImportarListaTecnologiaPeticion {
    Objects.requireNonNull(fechaLista, "La lista tiene fecha.");
    Objects.requireNonNull(modelos, "La lista trae sus modelos, aunque sean ninguno.");
  }

  public ImportarListaDeTecnologiaComando aComando(UUID proveedorId) {
    return new ImportarListaDeTecnologiaComando(
        proveedorId,
        fechaLista,
        modelos.stream().map(ModeloPeticion::aDominio).toList(),
        configuracionesDesaparecidas,
        modelosDesaparecidos);
  }

  public record ModeloPeticion(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String idModelo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String titulo,
      String marca,
      String categoria,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String descripcion,
      String metaDescripcion,
      List<String> paleta,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          List<ConfiguracionPeticion> configuraciones) {

    public ModeloPeticion {
      Objects.requireNonNull(idModelo, "El modelo tiene su id.");
      Objects.requireNonNull(titulo, "El modelo tiene título.");
      Objects.requireNonNull(descripcion, "El modelo tiene descripción.");
      Objects.requireNonNull(configuraciones, "El modelo trae sus configuraciones.");
    }

    ImportarListaDeTecnologiaComando.Modelo aDominio() {
      return new ImportarListaDeTecnologiaComando.Modelo(
          new ModeloDeLista(
              idModelo, titulo, marca, categoria, descripcion, metaDescripcion, paleta),
          configuraciones.stream().map(ConfiguracionPeticion::aDominio).toList());
    }
  }

  public record ConfiguracionPeticion(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sku,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String titulo,
      String ram,
      String almacenamiento,
      String sim,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long costoProveedor,
      Long precioMercado,
      List<String> coloresSugeridos) {

    public ConfiguracionPeticion {
      Objects.requireNonNull(sku, "La configuración tiene su id.");
      Objects.requireNonNull(titulo, "La configuración tiene título.");
      Objects.requireNonNull(costoProveedor, "La configuración trae su costo.");
    }

    ConfiguracionTecnologia aDominio() {
      return ConfiguracionTecnologia.deLista(
          sku,
          titulo,
          ram,
          almacenamiento,
          sim,
          Dinero.deCop(costoProveedor),
          precioMercado == null ? null : Dinero.deCop(precioMercado),
          coloresSugeridos);
    }
  }
}
