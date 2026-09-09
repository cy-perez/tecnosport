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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Crea un {@link Inventario} con una ENTRADA inicial igual a la existencia ya sembrada de cada
 * variante — sin esto, las variantes de Fase 1 no tendrían inventario que reservar y el {@code
 * bootRun} local quedaría inconsistente con lo que ya muestra el catálogo.
 *
 * <p>{@code @Order(2)}: corre después de {@code SembradorCatalogo}, que tiene que sembrar las
 * variantes primero.
 *
 * <p>{@code buscarPorVarianteId} usa bloqueo pesimista (@Lock): Spring Data no envuelve esas
 * consultas en una transacción implícita por su cuenta —a propósito, un lock que se suelta apenas
 * termina la consulta no sirve de nada— así que quien llama tiene que abrir la transacción, igual
 * que hará el checkout de Fase 3 y como ya hace {@code RepositorioInventarioJpaTest}. Sin esto,
 * {@code bootRun} real falla con {@code TransactionRequiredException} al arrancar (encontrado
 * ejecutando el bootRun, no en las pruebas: Testcontainers usa la ruta correcta desde el
 * principio).
 */
@Component
@Profile({"local", "dev"})
@Order(2)
public class SembradorInventario implements ApplicationRunner {

  private final VarianteJpaRepository variantes;
  private final RepositorioInventario inventarios;
  private final TransactionTemplate transaccion;

  public SembradorInventario(
      VarianteJpaRepository variantes,
      RepositorioInventario inventarios,
      PlatformTransactionManager transactionManager) {
    this.variantes = variantes;
    this.inventarios = inventarios;
    this.transaccion = new TransactionTemplate(transactionManager);
  }

  @Override
  public void run(ApplicationArguments args) {
    Instant ahora = Instant.now();
    for (VarianteJpaEntity variante : variantes.findAll()) {
      transaccion.executeWithoutResult(
          estado -> {
            if (inventarios.buscarPorVarianteId(variante.getId()).isPresent()) {
              return;
            }
            Inventario inventario = Inventario.crear(variante.getId());
            if (variante.getExistencia() > 0) {
              inventario.registrarEntrada(variante.getExistencia(), "siembra inicial", ahora);
            }
            inventarios.guardar(inventario);
          });
    }
  }
}
