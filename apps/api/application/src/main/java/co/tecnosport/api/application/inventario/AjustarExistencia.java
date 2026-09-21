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
 * <p><b>Escribe en un solo sitio desde adr/0050</b>, y esa es toda la diferencia. Hasta entonces
 * copiaba además el conteo a {@code variante.existencia}, porque la vitrina leía esa columna para
 * decidir si algo estaba agotado; las dos quedaban iguales en el momento de contar y se separaban
 * otra vez con la siguiente venta. Ya no hay columna: el movimiento va al libro y el libro es lo
 * que la vitrina lee.
 *
 * <p>Sin {@code @Transactional}, igual que {@code CrearPedido}: el bloqueo pesimista que toma
 * {@code RepositorioInventario.abrirLibroConBloqueo} solo sirve si la carga, la mutación y el
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

    // Una variante sin libro no bloquea la corrección: negarse dejaría el dato malo en pie y
    // obligaría a arreglarlo con SQL, que es de lo que se está saliendo — y además la pantalla de
    // existencias enseña precisamente esas variantes, así que serían filas imposibles de corregir
    // desde el sitio que existe para corregirlas.
    //
    // Lo que no puede es abrirlo aquí. Un `orElseGet(() -> Inventario.crear(...))` deja esa rama
    // sin ningún bloqueo: el `select … for update` no encuentra fila, así que no bloquea nada, y
    // dos conteos simultáneos escribían dos libros contra `ux_inventario_variante`. Quien abre el
    // libro es el repositorio, de forma idempotente, y devuelve el bloqueo tomado en las dos ramas.
    Inventario inventario = repositorioInventario.abrirLibroConBloqueo(comando.varianteId());

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

    return resultado;
  }
}
