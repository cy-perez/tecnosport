package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor;
import co.tecnosport.api.domain.catalogo.EstadoDisponibilidad;
import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.OrigenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.infrastructure.catalogo.MapeadorCatalogo;
import co.tecnosport.api.infrastructure.catalogo.ProductoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.VarianteJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteJpaEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * Las consultas sobre {@code producto} que solo el contexto de proveedores hace, y el SKU del
 * proveedor en las variantes de un álbum.
 *
 * <p>El SKU y la marca de agotada de una variante se escriben por la entidad, no con un {@code
 * update} por SQL: la transacción de la ingesta ya puede tener la variante cargada, y la vería con
 * el valor viejo. Cuál de dos productos con el mismo SKU manda lo decide la aplicación: aquí se
 * devuelven todos.
 */
@Repository
public class RepositorioProductosDeProveedorJpa implements RepositorioProductosDeProveedor {

  private static final String ACTIVA = EstadoVariante.ACTIVA.name();

  private final ProductoJpaRepository productos;
  private final VarianteJpaRepository variantes;
  private final MapeadorCatalogo mapeador;

  public RepositorioProductosDeProveedorJpa(
      ProductoJpaRepository productos, VarianteJpaRepository variantes, MapeadorCatalogo mapeador) {
    this.productos = Objects.requireNonNull(productos);
    this.variantes = Objects.requireNonNull(variantes);
    this.mapeador = Objects.requireNonNull(mapeador);
  }

  @Override
  public Optional<Producto> buscarPorHuella(UUID proveedorId, HuellaProveedor huella) {
    return productos
        .findByProveedorIdAndHuellaProveedor(proveedorId, huella.valor())
        .flatMap(p -> mapeador.hidratar(List.of(p.getId())).stream().findFirst());
  }

  @Override
  public List<Producto> disponiblesVistosAntesDe(Instant limite) {
    List<UUID> ids =
        productos
            .findByOrigenAndEstadoDisponibilidadAndVistoPorUltimaVezBefore(
                OrigenProducto.PROVEEDOR.name(), EstadoDisponibilidad.DISPONIBLE.name(), limite)
            .stream()
            .map(ProductoJpaEntity::getId)
            .toList();
    return ids.isEmpty() ? List.of() : mapeador.hidratar(ids);
  }

  @Override
  public List<PrendaDeProveedor> buscarPrendasPorReferencia(UUID proveedorId, String referencia) {
    Map<UUID, List<VarianteJpaEntity>> porProducto =
        variantes.buscarPorReferenciaDeProveedor(proveedorId, referencia).stream()
            .filter(v -> ACTIVA.equals(v.getEstado()))
            .collect(Collectors.groupingBy(VarianteJpaEntity::getProductoId));
    List<PrendaDeProveedor> prendas = new ArrayList<>();
    porProducto.forEach(
        (productoId, suyas) -> {
          long suyasALaVenta = suyas.stream().filter(v -> !v.isAgotadaPorProveedor()).count();
          long delProductoALaVenta =
              variantes.countByProductoIdAndEstadoAndAgotadaPorProveedorFalse(productoId, ACTIVA);
          prendas.add(
              new PrendaDeProveedor(
                  productoId,
                  suyas.stream().map(VarianteJpaEntity::getId).toList(),
                  suyasALaVenta == 0,
                  (int) (delProductoALaVenta - suyasALaVenta)));
        });
    return List.copyOf(prendas);
  }

  @Override
  public void asignarReferencias(Map<UUID, String> referenciaPorVariante) {
    List<VarianteJpaEntity> cambiadas = new ArrayList<>();
    variantes
        .findAllById(referenciaPorVariante.keySet())
        .forEach(
            v -> cambiadas.add(v.conReferenciaProveedor(referenciaPorVariante.get(v.getId()))));
    variantes.saveAll(cambiadas);
  }

  @Override
  public void cambiarDisponibilidadDeVariantes(List<UUID> varianteIds, boolean aLaVenta) {
    List<VarianteJpaEntity> cambiadas = new ArrayList<>();
    variantes
        .findAllById(varianteIds)
        // La agotada por el margen no vuelve con el SKU: volver a anunciarlo no cambia el costo.
        .forEach(v -> cambiadas.add(v.conAgotadaPorProveedor(!aLaVenta || v.isAgotadaPorMargen())));
    variantes.saveAll(cambiadas);
  }

  @Override
  public void marcarMargenDeVariantes(Map<UUID, Boolean> alcanzaPorVariante) {
    if (alcanzaPorVariante.isEmpty()) {
      return;
    }
    List<VarianteJpaEntity> cambiadas = new ArrayList<>();
    for (VarianteJpaEntity v : variantes.findAllById(alcanzaPorVariante.keySet())) {
      boolean alcanza = alcanzaPorVariante.get(v.getId());
      if (alcanza && v.isAgotadaPorMargen()) {
        cambiadas.add(v.conAgotadaPorProveedor(false).conAgotadaPorMargen(false));
      } else if (!alcanza && !v.isAgotadaPorProveedor()) {
        // La que ya estaba agotada por su SKU se queda como está: si se marcara por el margen, el
        // margen que vuelve la devolvería a la venta sin que el proveedor la haya vuelto a ofrecer.
        cambiadas.add(v.conAgotadaPorProveedor(true).conAgotadaPorMargen(true));
      }
    }
    variantes.saveAll(cambiadas);
  }

  @Override
  public List<VarianteSinMargen> variantesSinMargen() {
    return variantes.agotadasPorMargen().stream()
        .map(
            fila ->
                new VarianteSinMargen(
                    (UUID) fila[0], (String) fila[1], (UUID) fila[2], (String) fila[3]))
        .toList();
  }

  @Override
  public void marcarDisponibilidadesPorConfirmar(Map<UUID, Boolean> porConfirmarPorVariante) {
    if (porConfirmarPorVariante.isEmpty()) {
      return;
    }
    List<VarianteJpaEntity> cambiadas = new ArrayList<>();
    variantes
        .findAllById(porConfirmarPorVariante.keySet())
        .forEach(
            v ->
                cambiadas.add(
                    v.conDisponibilidadPorConfirmar(porConfirmarPorVariante.get(v.getId()))));
    variantes.saveAll(cambiadas);
  }
}
