package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioBorradoresTecnologia;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.ModeloDeLista;
import co.tecnosport.api.infrastructure.proveedores.entidad.BorradorTecnologiaJpaEntity;
import co.tecnosport.api.infrastructure.proveedores.entidad.ConfiguracionTecnologiaJpaEmbeddable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioBorradoresTecnologiaJpa implements RepositorioBorradoresTecnologia {

  /** Los colores van uno por línea; ningún nombre de color trae un salto de línea. */
  private static final String SEPARADOR = "\n";

  private final BorradorTecnologiaJpaRepository jpa;
  private final Reloj reloj;

  public RepositorioBorradoresTecnologiaJpa(BorradorTecnologiaJpaRepository jpa, Reloj reloj) {
    this.jpa = Objects.requireNonNull(jpa);
    this.reloj = Objects.requireNonNull(reloj);
  }

  @Override
  public void guardar(BorradorTecnologia borrador) {
    jpa.save(aFila(borrador, reloj.ahora()));
  }

  @Override
  public void actualizar(BorradorTecnologia borrador) {
    if (!jpa.existsById(borrador.id())) {
      throw new IllegalStateException(
          "No se actualiza un borrador que no existe: " + borrador.id());
    }
    jpa.save(aFila(borrador, reloj.ahora()));
  }

  @Override
  public Optional<BorradorTecnologia> buscarPorId(UUID id) {
    return jpa.findById(id).map(RepositorioBorradoresTecnologiaJpa::aDominio);
  }

  @Override
  public Optional<BorradorTecnologia> buscarPorIdParaActualizar(UUID id) {
    return jpa.buscarConBloqueo(id).map(RepositorioBorradoresTecnologiaJpa::aDominio);
  }

  @Override
  public Optional<BorradorTecnologia> buscarEnRevisionParaActualizar(
      UUID proveedorId, String idModelo) {
    return jpa.buscarEnRevisionConBloqueo(proveedorId, idModelo)
        .map(RepositorioBorradoresTecnologiaJpa::aDominio);
  }

  @Override
  public List<BorradorTecnologia> listarResueltos(UUID proveedorId, String idModelo) {
    return jpa
        .findByProveedorIdAndIdModeloAndEstadoNot(
            proveedorId, idModelo, EstadoBorrador.EN_REVISION.name())
        .stream()
        .map(RepositorioBorradoresTecnologiaJpa::aDominio)
        .toList();
  }

  @Override
  public List<BorradorTecnologia> listarPorEstado(EstadoBorrador estado) {
    return jpa.findByEstadoOrderByCreadoEnDesc(estado.name()).stream()
        .map(RepositorioBorradoresTecnologiaJpa::aDominio)
        .toList();
  }

  private static BorradorTecnologiaJpaEntity aFila(BorradorTecnologia b, Instant ahora) {
    ModeloDeLista m = b.modelo();
    return new BorradorTecnologiaJpaEntity(
        b.id(),
        b.proveedorId(),
        m.idModelo(),
        b.huella().valor(),
        m.titulo(),
        m.marca(),
        m.categoria(),
        m.descripcion(),
        m.metaDescripcion(),
        unir(m.paleta()),
        b.configuraciones().stream()
            .map(
                c ->
                    new ConfiguracionTecnologiaJpaEmbeddable(
                        c.sku(),
                        c.titulo(),
                        c.ram(),
                        c.almacenamiento(),
                        c.sim(),
                        c.costoProveedor().valor(),
                        valor(c.precioMercado()),
                        unir(c.coloresSugeridos()),
                        unir(c.coloresElegidos()),
                        valor(c.precioVenta())))
            .toList(),
        b.productoId().orElse(null),
        b.estado().name(),
        b.motivoRechazo().orElse(null),
        b.vistoEn(),
        b.creadoEn(),
        ahora);
  }

  private static BorradorTecnologia aDominio(BorradorTecnologiaJpaEntity f) {
    return new BorradorTecnologia(
        f.getId(),
        f.getProveedorId(),
        new ModeloDeLista(
            f.getIdModelo(),
            f.getTitulo(),
            f.getMarcaSugerida(),
            f.getCategoriaSugerida(),
            f.getDescripcion(),
            f.getMetaDescripcion(),
            separar(f.getPaleta())),
        f.getConfiguraciones().stream()
            .map(
                c ->
                    new ConfiguracionTecnologia(
                        c.getSku(),
                        c.getTitulo(),
                        c.getRam(),
                        c.getAlmacenamiento(),
                        c.getSim(),
                        Dinero.deCop(c.getCostoProveedor()),
                        dinero(c.getPrecioMercado()),
                        separar(c.getColoresSugeridos()),
                        separar(c.getColoresElegidos()),
                        dinero(c.getPrecioVenta())))
            .toList(),
        f.getProductoId(),
        EstadoBorrador.valueOf(f.getEstado()),
        f.getMotivoRechazo(),
        f.getVistoEn(),
        f.getCreadoEn());
  }

  private static BigDecimal valor(Dinero dinero) {
    return dinero == null ? null : dinero.valor();
  }

  private static Dinero dinero(BigDecimal valor) {
    return valor == null ? null : Dinero.deCop(valor);
  }

  private static String unir(List<String> valores) {
    return valores.isEmpty() ? null : String.join(SEPARADOR, valores);
  }

  private static List<String> separar(String texto) {
    return texto == null || texto.isBlank() ? List.of() : Arrays.asList(texto.split(SEPARADOR));
  }
}
