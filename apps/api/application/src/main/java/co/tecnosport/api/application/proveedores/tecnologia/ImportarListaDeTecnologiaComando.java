package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.ModeloDeLista;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * La lista del día tal como la exporta la skill ({@code exportar_lista.py}).
 *
 * @param configuracionesDesaparecidas ids de configuración que la comparación de la skill declaró
 *     desaparecidos: estaban en la última lista de su bloque y en esta no. Los decide la skill y no
 *     este caso de uso porque solo ella sabe qué bloques trae la lista de hoy —una lista parcial no
 *     dice que lo que falta se acabó—
 * @param modelosDesaparecidos ids de modelo de los que no vino ninguna configuración, con el mismo
 *     criterio
 */
public record ImportarListaDeTecnologiaComando(
    UUID proveedorId,
    LocalDate fechaLista,
    List<Modelo> modelos,
    List<String> configuracionesDesaparecidas,
    List<String> modelosDesaparecidos) {

  public ImportarListaDeTecnologiaComando {
    Objects.requireNonNull(proveedorId, "La lista es de un proveedor.");
    Objects.requireNonNull(fechaLista, "La lista tiene fecha.");
    modelos = modelos == null ? List.of() : List.copyOf(modelos);
    configuracionesDesaparecidas =
        configuracionesDesaparecidas == null
            ? List.of()
            : List.copyOf(configuracionesDesaparecidas);
    modelosDesaparecidos =
        modelosDesaparecidos == null ? List.of() : List.copyOf(modelosDesaparecidos);
  }

  /** Un modelo con las configuraciones que trae la lista de hoy. */
  public record Modelo(ModeloDeLista modelo, List<ConfiguracionTecnologia> configuraciones) {
    public Modelo {
      Objects.requireNonNull(modelo, "El modelo no puede ser nulo.");
      configuraciones = configuraciones == null ? List.of() : List.copyOf(configuraciones);
    }
  }
}
