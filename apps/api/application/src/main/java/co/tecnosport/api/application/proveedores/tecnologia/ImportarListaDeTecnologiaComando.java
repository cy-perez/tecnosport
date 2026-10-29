package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.ConfirmacionDeColor;
import co.tecnosport.api.domain.proveedores.ExcepcionDeFotos;
import co.tecnosport.api.domain.proveedores.ModeloDeLista;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * La lista del día tal como la exporta la skill ({@code exportar_lista.py}).
 *
 * @param configuracionesDesaparecidas ids de configuración que la comparación de la skill declaró
 *     desaparecidos: estaban en la última lista de su bloque y en esta no. Los decide la skill y no
 *     este caso de uso porque solo ella sabe qué bloques trae la lista de hoy —una lista parcial no
 *     dice que lo que falta se acabó—
 * @param modo qué parte de la lista se aplica: el inventario de lo que ya se vende, o los
 *     borradores de lo que todavía no
 * @param modelosDesaparecidos ids de modelo de los que no vino ninguna configuración, con el mismo
 *     criterio
 * @param excepcionesDeFotos por id de modelo, los que la skill de fotos dejó pasar con menos de
 *     cuatro fotos (28/10/2026). Solo los usan los borradores
 */
public record ImportarListaDeTecnologiaComando(
    UUID proveedorId,
    LocalDate fechaLista,
    Modo modo,
    List<Modelo> modelos,
    List<String> configuracionesDesaparecidas,
    List<String> modelosDesaparecidos,
    Map<String, List<String>> coloresMarcados,
    Map<String, ExcepcionDeFotos> excepcionesDeFotos) {

  /** Sin excepciones de fotos: el inventario no las usa. */
  public ImportarListaDeTecnologiaComando(
      UUID proveedorId,
      LocalDate fechaLista,
      Modo modo,
      List<Modelo> modelos,
      List<String> configuracionesDesaparecidas,
      List<String> modelosDesaparecidos,
      Map<String, List<String>> coloresMarcados) {
    this(
        proveedorId,
        fechaLista,
        modo,
        modelos,
        configuracionesDesaparecidas,
        modelosDesaparecidos,
        coloresMarcados,
        Map.of());
  }

  /** Sin los colores marcados aparte: se deducen de los sugeridos (ADR-0086). */
  public ImportarListaDeTecnologiaComando(
      UUID proveedorId,
      LocalDate fechaLista,
      Modo modo,
      List<Modelo> modelos,
      List<String> configuracionesDesaparecidas,
      List<String> modelosDesaparecidos) {
    this(
        proveedorId,
        fechaLista,
        modo,
        modelos,
        configuracionesDesaparecidas,
        modelosDesaparecidos,
        Map.of(),
        Map.of());
  }

  public ImportarListaDeTecnologiaComando {
    coloresMarcados = coloresMarcados == null ? Map.of() : Map.copyOf(coloresMarcados);
    excepcionesDeFotos = excepcionesDeFotos == null ? Map.of() : Map.copyOf(excepcionesDeFotos);
    Objects.requireNonNull(proveedorId, "La lista es de un proveedor.");
    Objects.requireNonNull(fechaLista, "La lista tiene fecha.");
    Objects.requireNonNull(modo, "La lista dice qué parte se aplica.");
    modelos = modelos == null ? List.of() : List.copyOf(modelos);
    configuracionesDesaparecidas =
        configuracionesDesaparecidas == null
            ? List.of()
            : List.copyOf(configuracionesDesaparecidas);
    modelosDesaparecidos =
        modelosDesaparecidos == null ? List.of() : List.copyOf(modelosDesaparecidos);
    // Retirar es inventario. Una importación de borradores que lo trajera lo perdería sin decirlo,
    // y quien la mandó creería que lo desaparecido ya dejó de ofrecerse.
    if (modo == Modo.BORRADORES
        && !(configuracionesDesaparecidas.isEmpty() && modelosDesaparecidos.isEmpty())) {
      throw new IllegalArgumentException(
          "Una importación de borradores no retira nada: lo desaparecido va con el inventario.");
    }
  }

  /**
   * La lista entra en dos tiempos (ADR-0079). El inventario no puede esperar a las fotos de lo
   * nuevo —vendería lo que el proveedor ya no tiene, o con el costo de ayer—, y un borrador sin
   * fotos no es una ficha que se pueda revisar.
   */
  public enum Modo {
    /**
     * Lo que ya se vende: renueva el costo y la existencia, y retira lo desaparecido. No propone
     * nada; dice qué quedó esperando borrador.
     */
    INVENTARIO,
    /**
     * Lo que todavía no se vende: entra o se actualiza su borrador. No toca costo ni existencia,
     * así que puede llegar días después del inventario de la misma lista, y más de una vez.
     */
    BORRADORES
  }

  /**
   * Lo que identifica el contenido de la lista: el SHA-256 de todo lo que trae. Dos envíos del
   * mismo archivo dan la misma; una lista corregida el mismo día, otra.
   *
   * <p><b>Sin el modo, y con el texto exacto de antes de que existiera</b> (ADR-0079). La huella
   * salía del {@code toString} del record, y agregarle {@code modo} cambiaba todas: el día del
   * despliegue, la lista de ese día habría vuelto a entrar y repuesto lo vendido desde la primera
   * vez. Reponer no es nunca «el lado seguro». Si algún día cambia la forma de las configuraciones,
   * las listas ya registradas tienen que seguir coincidiendo.
   */
  public String huella() {
    String contenido =
        "ImportarListaDeTecnologiaComando[proveedorId="
            + proveedorId
            + ", fechaLista="
            + fechaLista
            + ", modelos="
            + modelos
            + ", configuracionesDesaparecidas="
            + configuracionesDesaparecidas
            + ", modelosDesaparecidos="
            + modelosDesaparecidos
            + "]";
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(contenido.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("La JVM no ofrece SHA-256.", e);
    }
  }

  /**
   * Los colores que la lista marca de esta configuración con sus emojis (ADR-0086): solo esos se
   * venden sin confirmar con el proveedor. Si la exportación no los mandó aparte, se deducen de los
   * sugeridos.
   *
   * <p>No entran en la {@link #huella()}, por lo mismo que el modo: cambiarían la de las listas ya
   * registradas. Lo que marcan los emojis ya está en los sugeridos, que sí entran.
   */
  public List<String> marcadosDe(ConfiguracionTecnologia configuracion, List<String> paleta) {
    List<String> marcados = coloresMarcados.get(configuracion.sku());
    return marcados != null
        ? marcados
        : ConfirmacionDeColor.marcadosSegunLosSugeridos(paleta, configuracion.coloresSugeridos());
  }

  /**
   * La excepción de fotos de ese modelo, o nulo si pide las cuatro. Tampoco entra en la {@link
   * #huella()}: no es de la lista sino de la tanda de fotos, y llega después que el inventario.
   */
  public ExcepcionDeFotos excepcionDeFotosDe(String idModelo) {
    return excepcionesDeFotos.get(idModelo);
  }

  /** Un modelo con las configuraciones que trae la lista de hoy. */
  public record Modelo(ModeloDeLista modelo, List<ConfiguracionTecnologia> configuraciones) {
    public Modelo {
      Objects.requireNonNull(modelo, "El modelo no puede ser nulo.");
      configuraciones = configuraciones == null ? List.of() : List.copyOf(configuraciones);
    }
  }
}
