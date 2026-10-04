package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.RepositorioPaletaDeColores;
import co.tecnosport.api.domain.catalogo.ColorDePaleta;
import co.tecnosport.api.domain.catalogo.PatronDeColor;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/** La paleta la siembra V75; el panel solo la lee. */
@Repository
public class RepositorioPaletaDeColoresJpa implements RepositorioPaletaDeColores {

  private final ColorPaletaJpaRepository jpa;

  public RepositorioPaletaDeColoresJpa(ColorPaletaJpaRepository jpa) {
    this.jpa = Objects.requireNonNull(jpa);
  }

  @Override
  public List<ColorDePaleta> listarTodos() {
    return jpa.findAllByOrderByOrdenAsc().stream()
        .map(
            c ->
                new ColorDePaleta(
                    c.getId(),
                    c.getNombre(),
                    c.getNombreEn(),
                    c.getHex(),
                    c.getOrden(),
                    c.getPatron() == null ? null : PatronDeColor.valueOf(c.getPatron()),
                    c.getColoresPatron() == null
                        ? List.of()
                        : List.of(c.getColoresPatron().split(","))))
        .toList();
  }
}
