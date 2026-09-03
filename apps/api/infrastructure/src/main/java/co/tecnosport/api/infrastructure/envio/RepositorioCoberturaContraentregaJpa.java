package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.RepositorioCoberturaContraentrega;
import co.tecnosport.api.infrastructure.envio.entidad.CoberturaContraentregaJpaEntity;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class RepositorioCoberturaContraentregaJpa implements RepositorioCoberturaContraentrega {

  private final CoberturaContraentregaJpaRepository repositorio;

  public RepositorioCoberturaContraentregaJpa(CoberturaContraentregaJpaRepository repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  @Override
  public boolean estaCubierta(String codigoDaneCiudad) {
    return repositorio.existsById(codigoDaneCiudad);
  }

  @Override
  public void agregar(String codigoDaneCiudad) {
    repositorio.save(new CoberturaContraentregaJpaEntity(codigoDaneCiudad));
  }

  /** Idempotente a propósito: quitar una ciudad que ya no está cubierta no es un error. */
  @Override
  public void quitar(String codigoDaneCiudad) {
    if (repositorio.existsById(codigoDaneCiudad)) {
      repositorio.deleteById(codigoDaneCiudad);
    }
  }

  @Override
  public List<String> listar() {
    return repositorio.findAll().stream()
        .map(CoberturaContraentregaJpaEntity::getCodigoDaneCiudad)
        .toList();
  }
}
