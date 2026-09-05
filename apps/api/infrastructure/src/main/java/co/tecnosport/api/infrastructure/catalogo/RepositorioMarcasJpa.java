package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class RepositorioMarcasJpa implements RepositorioMarcas {

  private final MarcaJpaRepository marcaJpaRepository;

  public RepositorioMarcasJpa(MarcaJpaRepository marcaJpaRepository) {
    this.marcaJpaRepository = Objects.requireNonNull(marcaJpaRepository);
  }

  @Override
  public List<Marca> listarTodas() {
    return marcaJpaRepository.findAll(Sort.by("nombre")).stream().map(this::aMarca).toList();
  }

  @Override
  public Optional<Marca> buscarPorId(UUID id) {
    return marcaJpaRepository.findById(id).map(this::aMarca);
  }

  private Marca aMarca(MarcaJpaEntity m) {
    return new Marca(m.getId(), m.getNombre());
  }
}
