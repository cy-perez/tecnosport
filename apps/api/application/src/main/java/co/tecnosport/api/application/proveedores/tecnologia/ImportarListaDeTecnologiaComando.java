package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.ModeloDeLista;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
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

  /**
   * Lo que identifica el contenido de la lista: el SHA-256 de todo lo que trae. Dos envíos del
   * mismo archivo dan la misma; una lista corregida el mismo día, otra. Sale de los {@code
   * toString} de los records, que son deterministas: cambian si cambia la forma del comando, y
   * entonces una lista vieja se puede volver a importar una vez, que es el lado seguro.
   */
  public String huella() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("La JVM no ofrece SHA-256.", e);
    }
  }

  /** Un modelo con las configuraciones que trae la lista de hoy. */
  public record Modelo(ModeloDeLista modelo, List<ConfiguracionTecnologia> configuraciones) {
    public Modelo {
      Objects.requireNonNull(modelo, "El modelo no puede ser nulo.");
      configuraciones = configuraciones == null ? List.of() : List.copyOf(configuraciones);
    }
  }
}
