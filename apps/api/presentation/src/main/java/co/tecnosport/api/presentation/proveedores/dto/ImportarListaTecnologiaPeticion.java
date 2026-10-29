package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.tecnologia.ImportarListaDeTecnologiaComando;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.ExcepcionDeFotos;
import co.tecnosport.api.domain.proveedores.ModeloDeLista;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * La lista del día tal como la escribe {@code exportar_lista.py} de la skill de listas. Los dineros
 * van como entero de pesos.
 *
 * @param bloques los bloques de la lista que vinieron («ANDROID», «IPHONE»…). Solo informativo: qué
 *     desapareció ya lo decidió la skill con ellos
 * @param modo el inventario de lo que ya se vende o los borradores de lo que no (ADR-0079)
 */
public record ImportarListaTecnologiaPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate fechaLista,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ImportarListaDeTecnologiaComando.Modo modo,
    List<String> bloques,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ModeloPeticion> modelos,
    List<String> configuracionesDesaparecidas,
    List<String> modelosDesaparecidos) {

  public ImportarListaTecnologiaPeticion {
    Objects.requireNonNull(fechaLista, "La lista tiene fecha.");
    Objects.requireNonNull(modo, "La lista dice si es el inventario o los borradores.");
    Objects.requireNonNull(modelos, "La lista trae sus modelos, aunque sean ninguno.");
  }

  public ImportarListaDeTecnologiaComando aComando(UUID proveedorId) {
    return new ImportarListaDeTecnologiaComando(
        proveedorId,
        fechaLista,
        modo,
        modelos.stream().map(ModeloPeticion::aDominio).toList(),
        configuracionesDesaparecidas,
        modelosDesaparecidos,
        coloresMarcados(),
        excepcionesDeFotos());
  }

  /** Por id de modelo, los que la skill de fotos dejó pasar con menos fotos (28/10/2026). */
  private Map<String, ExcepcionDeFotos> excepcionesDeFotos() {
    Map<String, ExcepcionDeFotos> excepciones = new HashMap<>();
    for (ModeloPeticion modelo : modelos) {
      if (modelo.excepcionDeFotos() != null) {
        try {
          excepciones.put(modelo.idModelo().strip(), modelo.excepcionDeFotos().aDominio());
        } catch (ExcepcionDeDominio e) {
          // Una excepción mala rechaza la importación entera: que diga de qué modelo es.
          throw new ExcepcionDeDominio(modelo.idModelo().strip() + ": " + e.getMessage());
        }
      }
    }
    return excepciones;
  }

  /**
   * Por id de configuración, los colores que la lista marca con sus emojis. Solo las que los traen
   * aparte: las demás los deducen de los sugeridos (ADR-0086).
   */
  private Map<String, List<String>> coloresMarcados() {
    Map<String, List<String>> marcados = new HashMap<>();
    for (ModeloPeticion modelo : modelos) {
      for (ConfiguracionPeticion c : modelo.configuraciones()) {
        if (c.coloresMarcados() != null) {
          marcados.put(c.sku().strip(), c.coloresMarcados());
        }
      }
    }
    return marcados;
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
          List<ConfiguracionPeticion> configuraciones,
      @Schema(
              description =
                  "Solo con los borradores: el modelo se aprueba con menos de cuatro fotos porque"
                      + " no hay más tomas oficiales. Nulo si pide las cuatro.")
          ExcepcionDeFotosPeticion excepcionDeFotos) {

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

  /** La que declaró la persona en la skill de fotos ({@code --minimo-fotos}). */
  public record ExcepcionDeFotosPeticion(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer minimo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String motivo) {

    public ExcepcionDeFotosPeticion {
      Objects.requireNonNull(minimo, "La excepción de fotos dice cuántas bastan.");
      Objects.requireNonNull(motivo, "La excepción de fotos dice por qué.");
    }

    ExcepcionDeFotos aDominio() {
      return new ExcepcionDeFotos(minimo, motivo);
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
      List<String> coloresSugeridos,
      @Schema(
              description =
                  "Los colores que la lista marca con sus emojis, sin la paleta que se sugiere"
                      + " cuando no marca ninguno. Nulo si la exportación no los separa.")
          List<String> coloresMarcados) {

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
