package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.tecnologia.RepositorioBorradoresTecnologia;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoDeBorradorTecnologia;
import co.tecnosport.api.domain.proveedores.ModeloDeLista;
import co.tecnosport.api.infrastructure.proveedores.entidad.BorradorTecnologiaJpaEntity;
import co.tecnosport.api.infrastructure.proveedores.entidad.ConfiguracionTecnologiaJpaEmbeddable;
import co.tecnosport.api.infrastructure.proveedores.entidad.FotoTecnologiaJpaEmbeddable;
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
        ahora,
        b.fotos().stream()
            .map(
                f ->
                    new FotoTecnologiaJpaEmbeddable(
                        f.id(),
                        String.join(
                            SEPARADOR,
                            f.variantes().stream()
                                .map(v -> v.ancho() + ";" + v.bytes() + ";" + v.referencia())
                                .toList()),
                        f.referenciaVistaPrevia(),
                        f.alto(),
                        f.hash(),
                        f.altEs(),
                        f.altEn(),
                        f.subidaEn()))
            .toList(),
        b.descripcionEditada().orElse(null),
        b.fichaNuevaDeLaSkill());
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
        f.getCreadoEn(),
        f.getFotos().stream().map(RepositorioBorradoresTecnologiaJpa::foto).toList(),
        f.getDescripcionEditada(),
        f.isFichaNuevaDeLaSkill());
  }

  private static FotoDeBorradorTecnologia foto(FotoTecnologiaJpaEmbeddable f) {
    List<FotoDeBorradorTecnologia.Variante> variantes =
        separar(f.getVariantes()).stream()
            .map(
                linea -> {
                  // La clave va al final y con límite 3: si algún día trajera un punto y coma,
                  // seguiría entera.
                  String[] partes = linea.split(";", 3);
                  return new FotoDeBorradorTecnologia.Variante(
                      Integer.parseInt(partes[0]), partes[2], Long.parseLong(partes[1]));
                })
            .toList();
    return new FotoDeBorradorTecnologia(
        f.getFotoId(),
        variantes,
        f.getReferenciaVistaPrevia(),
        f.getAlto(),
        f.getHash(),
        f.getAltEs(),
        f.getAltEn(),
        f.getSubidaEn());
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
