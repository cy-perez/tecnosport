package co.tecnosport.api.application.inventario;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.inventario.Inventario;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Le pregunta al libro de movimientos qué se puede vender, para que lo pregunte el catálogo.
 *
 * <p>No es un caso de uso con endpoint propio: es el colaborador que {@code BuscarProductos} y
 * {@code VerFichaDeProducto} usan para no tener que conocer el inventario por dentro.
 *
 * <p><b>Por qué se calcula al leer y no se guarda en una columna</b> (adr/0050): el disponible
 * depende de {@code ahora}, porque una reserva vence sola. Una columna se queda vieja sin que nadie
 * la toque y sin que nada avise — que es exactamente el defecto del que se sale. El precio es el
 * que adr/0049 ya dejó escrito para el panel: la consulta trae el histórico de movimientos de las
 * variantes de la página.
 */
public final class DisponibilidadDeVariantes {

  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public DisponibilidadDeVariantes(RepositorioInventario repositorioInventario, Reloj reloj) {
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public VariantesDisponibles de(Collection<UUID> varianteIds) {
    if (varianteIds == null || varianteIds.isEmpty()) {
      return VariantesDisponibles.ninguna();
    }
    Instant ahora = reloj.ahora();
    Set<UUID> disponibles =
        repositorioInventario.buscarPorVarianteIds(varianteIds).stream()
            .filter(libro -> libro.saldoDisponible(ahora) > 0)
            .map(Inventario::varianteId)
            .collect(Collectors.toUnmodifiableSet());
    return new VariantesDisponibles(disponibles);
  }
}
