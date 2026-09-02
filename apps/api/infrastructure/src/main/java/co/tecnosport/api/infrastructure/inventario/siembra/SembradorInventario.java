package co.tecnosport.api.infrastructure.inventario.siembra;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.infrastructure.catalogo.VarianteJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteJpaEntity;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Crea un {@link Inventario} con una ENTRADA inicial igual a la existencia ya sembrada de cada
 * variante — sin esto, las variantes de Fase 1 no tendrían inventario que reservar y el {@code
 * bootRun} local quedaría inconsistente con lo que ya muestra el catálogo.
 *
 * <p>{@code @Order(2)}: corre después de {@code SembradorCatalogo}, que tiene que sembrar las
 * variantes primero.
 */
@Component
@Profile("local")
@Order(2)
public class SembradorInventario implements ApplicationRunner {

  private final VarianteJpaRepository variantes;
  private final RepositorioInventario inventarios;

  public SembradorInventario(VarianteJpaRepository variantes, RepositorioInventario inventarios) {
    this.variantes = variantes;
    this.inventarios = inventarios;
  }

  @Override
  public void run(ApplicationArguments args) {
    Instant ahora = Instant.now();
    for (VarianteJpaEntity variante : variantes.findAll()) {
      if (inventarios.buscarPorVarianteId(variante.getId()).isPresent()) {
        continue;
      }
      Inventario inventario = Inventario.crear(variante.getId());
      if (variante.getExistencia() > 0) {
        inventario.registrarEntrada(variante.getExistencia(), "siembra inicial", ahora);
      }
      inventarios.guardar(inventario);
    }
  }
}
