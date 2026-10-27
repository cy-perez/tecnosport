package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.application.usuario.RepositorioSegundosFactores;
import co.tecnosport.api.domain.usuario.ClaveTotp;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.infrastructure.usuario.entidad.CodigoDeRespaldoJpaEntity;
import co.tecnosport.api.infrastructure.usuario.entidad.SegundoFactorJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Dos tablas: {@code segundo_factor}, con la clave cifrada ({@link CifradorAesGcm}), y {@code
 * codigo_de_respaldo}, con las huellas. Al guardar, los códigos que el agregado ya no tiene se
 * borran fila a fila —por la entidad, no con un {@code delete} masivo— para que el contexto de
 * persistencia no se quede con entidades que la base ya no tiene.
 */
@Component
public class RepositorioSegundosFactoresJpa implements RepositorioSegundosFactores {

  private final SegundoFactorJpaRepository factores;
  private final CodigoDeRespaldoJpaRepository codigos;
  private final CifradorAesGcm cifrador;

  public RepositorioSegundosFactoresJpa(
      SegundoFactorJpaRepository factores,
      CodigoDeRespaldoJpaRepository codigos,
      CifradorAesGcm cifrador) {
    this.factores = Objects.requireNonNull(factores);
    this.codigos = Objects.requireNonNull(codigos);
    this.cifrador = Objects.requireNonNull(cifrador);
  }

  @Override
  public Optional<SegundoFactor> buscarParaActualizar(UUID usuarioId) {
    return factores.findParaActualizar(usuarioId).map(this::aDominio);
  }

  @Override
  public boolean estaActivo(UUID usuarioId) {
    return factores.existsByUsuarioIdAndActivadoEnIsNotNull(usuarioId);
  }

  @Override
  public void guardar(SegundoFactor factor) {
    UUID usuarioId = factor.usuarioId();
    factores.save(
        new SegundoFactorJpaEntity(
            usuarioId,
            cifrador.cifrar(factor.clave().bytes(), usuarioId),
            factor.creadoEn(),
            factor.activadoEn().orElse(null),
            factor.ultimoPasoUsado().orElse(null)));

    Set<String> vigentes =
        factor.respaldos().stream()
            .map(SegundoFactor.RespaldoGuardado::huella)
            .collect(Collectors.toSet());
    codigos.deleteAll(
        codigos.findByUsuarioId(usuarioId).stream()
            .filter(c -> !vigentes.contains(c.getHuella()))
            .toList());
    codigos.saveAll(
        factor.respaldos().stream()
            .map(
                r ->
                    new CodigoDeRespaldoJpaEntity(r.huella(), usuarioId, r.creadoEn(), r.usadoEn()))
            .toList());
  }

  @Override
  public void eliminar(UUID usuarioId) {
    codigos.deleteAll(codigos.findByUsuarioId(usuarioId));
    factores.deleteById(usuarioId);
  }

  private SegundoFactor aDominio(SegundoFactorJpaEntity entidad) {
    UUID usuarioId = entidad.getUsuarioId();
    List<SegundoFactor.RespaldoGuardado> respaldos =
        codigos.findByUsuarioId(usuarioId).stream()
            .map(
                c ->
                    new SegundoFactor.RespaldoGuardado(
                        c.getHuella(), c.getCreadoEn(), c.getUsadoEn()))
            .toList();
    return new SegundoFactor(
        usuarioId,
        new ClaveTotp(cifrador.descifrar(entidad.getClaveCifrada(), usuarioId)),
        entidad.getCreadoEn(),
        entidad.getActivadoEn(),
        entidad.getUltimoPasoUsado(),
        respaldos);
  }
}
