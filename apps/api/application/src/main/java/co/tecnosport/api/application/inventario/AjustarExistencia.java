package co.tecnosport.api.application.inventario;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.VarianteNoEncontradaPorIdException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.inventario.Inventario;
import java.time.Instant;
import java.util.Objects;

/**
 * Corrige la existencia de una variante contra un conteo físico, dejando el motivo en el histórico
 * (adr/0049).
 *
 * <p>Existía desde la Fase 2 el dominio para hacerlo —{@code Inventario.registrarAjuste}— y no
 * existía ninguna forma de llegar hasta él: {@code application/inventario} tenía un solo archivo,
 * el puerto. El resultado práctico es que los doce primeros productos reales se cargaron con
 * existencia 5, un número inventado, y la única manera de corregirlo era escribir en la base a
 * mano.
 *
 * <p><b>Escribe en dos sitios y eso es deliberado.</b> El movimiento va al libro, que es la verdad,
 * y el conteo se copia además a {@code variante.existencia}, que es la columna que la vitrina lee
 * para decidir si algo está agotado. Mientras las dos existan hay que dejarlas iguales en el
 * momento de contar; que se vuelvan a separar con cada venta es el defecto que adr/0049 documenta y
 * no resuelve.
 *
 * <p>Sin {@code @Transactional}, igual que {@code CrearPedido}: el bloqueo pesimista que toma
 * {@code RepositorioInventario.buscarPorVarianteId} solo sirve si la carga, la mutación y el
 * guardado corren en una transacción que abre quien llama. Es lo que impide que un conteo y una
 * reserva simultánea se pisen.
 */
public final class AjustarExistencia {

  private final RepositorioProductos repositorioProductos;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public AjustarExistencia(
      RepositorioProductos repositorioProductos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public ResultadoDeAjuste ejecutar(AjustarExistenciaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorVarianteId(comando.varianteId())
            .orElseThrow(() -> new VarianteNoEncontradaPorIdException(comando.varianteId()));
    Variante variante =
        producto.variantes().stream()
            .filter(candidata -> candidata.id().equals(comando.varianteId()))
            .findFirst()
            .orElseThrow(() -> new VarianteNoEncontradaPorIdException(comando.varianteId()));

    // Una variante sin libro no bloquea la corrección: `SembradorCatalogo` escribe entidades JPA
    // directo, así que puede haber variantes sin fila de inventario. Negarse aquí dejaría el dato
    // malo en pie y obligaría a arreglarlo con SQL, que es de lo que se está saliendo.
    Inventario inventario =
        repositorioInventario
            .buscarPorVarianteId(comando.varianteId())
            .orElseGet(() -> Inventario.crear(comando.varianteId()));

    Instant ahora = reloj.ahora();
    int saldoAnterior = inventario.saldoTotal();
    int reservadas = saldoAnterior - inventario.saldoDisponible(ahora);
    int diferencia = comando.cantidadContada() - saldoAnterior;

    ResultadoDeAjuste resultado =
        new ResultadoDeAjuste(
            variante.id(),
            variante.sku().valor(),
            producto.nombre(),
            saldoAnterior,
            comando.cantidadContada(),
            reservadas);

    if (diferencia == 0) {
      return resultado;
    }

    inventario.registrarAjuste(diferencia, comando.motivo(), ahora);
    repositorioInventario.guardar(inventario);
    repositorioProductos.actualizarExistencia(variante.id(), comando.cantidadContada());

    return resultado;
  }
}
