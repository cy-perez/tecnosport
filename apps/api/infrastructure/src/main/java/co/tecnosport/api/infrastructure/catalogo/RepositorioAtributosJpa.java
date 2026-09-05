package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.infrastructure.catalogo.entidad.AtributoJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class RepositorioAtributosJpa implements RepositorioAtributos {

  private final AtributoJpaRepository atributoJpaRepository;

  public RepositorioAtributosJpa(AtributoJpaRepository atributoJpaRepository) {
    this.atributoJpaRepository = Objects.requireNonNull(atributoJpaRepository);
  }

  @Override
  public List<Atributo> listarTodas() {
    return atributoJpaRepository.findAll(Sort.by("nombre")).stream().map(this::aAtributo).toList();
  }

  @Override
  public Optional<Atributo> buscarPorId(UUID id) {
    return atributoJpaRepository.findById(id).map(this::aAtributo);
  }

  private Atributo aAtributo(AtributoJpaEntity a) {
    return new Atributo(
        a.getId(), a.getNombre(), TipoAtributo.valueOf(a.getTipo()), a.getValoresPermitidos());
  }
}
