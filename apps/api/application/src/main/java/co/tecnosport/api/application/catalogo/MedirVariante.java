package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import java.util.Objects;

/**
 * Pone —o corrige— el peso y las dimensiones de una variante ya creada.
 *
 * <p>Es la otra mitad de {@link ListarVariantesSinMedir}, y sin ella el vigilante avisaría de algo
 * que no se puede arreglar: hasta hoy {@code AdminVarianteControlador} solo sabía crear, así que
 * una variante dada de alta sin medir —lo que {@code adr/0046} permite— únicamente se podía medir
 * escribiendo en la base a mano.
 *
 * <p><b>Corregir está permitido y es deliberado.</b> Se evaluó restringirlo a rellenar lo que falta
 * y se descartó: una medida mal tomada no se nota al guardarla, se nota en el margen de cada pedido
 * a domicilio de esa variante, y cerrar la puerta solo consigue que la enmienda ocurra por fuera
 * del sistema y sin rastro. El {@link Paquete} sigue exigiendo las cuatro cifras mayores que cero,
 * así que lo que no se puede es cambiar una medida por una inválida.
 */
public final class MedirVariante {

  private final RepositorioProductos repositorioProductos;

  public MedirVariante(RepositorioProductos repositorioProductos) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
  }

  public ResultadoDeMedicion ejecutar(MedirVarianteComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorVarianteId(comando.varianteId())
            .orElseThrow(() -> new VarianteNoEncontradaPorIdException(comando.varianteId()));

    // Antes de tocar nada: si ya tenía medidas, esto es una corrección y no un alta, y quien mire
    // el registro tiene que poder distinguirlas. Se lee del estado anterior porque después de
    // `medirVariante` ya no hay estado anterior que leer.
    boolean yaEstabaMedida =
        producto.variantes().stream()
            .filter(variante -> variante.id().equals(comando.varianteId()))
            .findFirst()
            .flatMap(Variante::paquete)
            .isPresent();

    Paquete paquete =
        new Paquete(comando.pesoGramos(), comando.largoCm(), comando.anchoCm(), comando.altoCm());
    Variante medida = producto.medirVariante(comando.varianteId(), paquete);
    repositorioProductos.actualizarPaquete(medida.id(), paquete);

    return new ResultadoDeMedicion(producto, medida, yaEstabaMedida);
  }
}
