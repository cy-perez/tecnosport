package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.envio.PesoDeReferencia;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Las referencias de envío para la pantalla del panel ({@code adr/0071}).
 *
 * <p>Lista las <strong>hojas</strong> de las líneas que admiten promedio, porque un producto solo
 * cuelga de una hoja: un peso puesto en "Ropa › Dama" no lo leería ningún producto. Ordenadas por
 * línea, rama y nombre, que es como se recorre el árbol en el menú.
 */
public final class ConsultarReferenciasDeEnvio {

  private final RepositorioReferenciasDeEnvio referencias;
  private final RepositorioCategorias categorias;

  public ConsultarReferenciasDeEnvio(
      RepositorioReferenciasDeEnvio referencias, RepositorioCategorias categorias) {
    this.referencias =
        Objects.requireNonNull(referencias, "El repositorio de referencias no puede ser nulo.");
    this.categorias =
        Objects.requireNonNull(categorias, "El repositorio de categorías no puede ser nulo.");
  }

  public ReferenciasDeEnvio ejecutar() {
    List<Categoria> todas = categorias.listarTodas();
    Map<UUID, Categoria> porId =
        todas.stream().collect(Collectors.toMap(Categoria::id, Function.identity()));
    Set<UUID> conHijas =
        todas.stream()
            .flatMap(categoria -> categoria.padreId().stream())
            .collect(Collectors.toSet());
    Map<UUID, Integer> pesos =
        referencias.pesos().stream()
            .collect(Collectors.toMap(PesoDeReferencia::categoriaId, PesoDeReferencia::pesoGramos));

    Comparator<ReferenciasDeEnvio.CategoriaConPeso> orden =
        Comparator.comparing((ReferenciasDeEnvio.CategoriaConPeso fila) -> fila.categoria().linea())
            .thenComparing(
                fila -> fila.rama() == null ? "" : fila.rama(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(fila -> fila.categoria().nombre(), String.CASE_INSENSITIVE_ORDER);

    List<ReferenciasDeEnvio.CategoriaConPeso> hojas =
        todas.stream()
            .filter(categoria -> PesoDeReferencia.admiteLaLinea(categoria.linea()))
            .filter(categoria -> !conHijas.contains(categoria.id()))
            .map(
                categoria ->
                    new ReferenciasDeEnvio.CategoriaConPeso(
                        categoria,
                        categoria.padreId().map(porId::get).map(Categoria::nombre).orElse(null),
                        pesos.get(categoria.id())))
            .sorted(orden)
            .toList();
    return new ReferenciasDeEnvio(referencias.medidas(), hojas);
  }
}
