package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.RepositorioReferenciasDeEnvio;
import co.tecnosport.api.domain.envio.MedidasDeReferencia;
import co.tecnosport.api.domain.envio.PesoDeReferencia;
import co.tecnosport.api.infrastructure.envio.entidad.MedidasDeReferenciaJpaEntity;
import co.tecnosport.api.infrastructure.envio.entidad.PesoDeReferenciaJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Las referencias de envío en PostgreSQL ({@code V89}). Las dos tablas son tan pequeñas —una fila y
 * unas treinta— que leerlas enteras en cada cotización cuesta menos que pensar en una caché.
 */
@Component
public class RepositorioReferenciasDeEnvioJpa implements RepositorioReferenciasDeEnvio {

  private final MedidasDeReferenciaJpaRepository medidas;
  private final PesoDeReferenciaJpaRepository pesos;

  public RepositorioReferenciasDeEnvioJpa(
      MedidasDeReferenciaJpaRepository medidas, PesoDeReferenciaJpaRepository pesos) {
    this.medidas = Objects.requireNonNull(medidas);
    this.pesos = Objects.requireNonNull(pesos);
  }

  @Override
  public Optional<MedidasDeReferencia> medidas() {
    return medidas
        .findById(MedidasDeReferenciaJpaEntity.UNICA)
        .map(
            fila ->
                new MedidasDeReferencia(fila.getLargoCm(), fila.getAnchoCm(), fila.getAltoCm()));
  }

  /** {@code save} sobre la misma llave: inserta la primera vez y reemplaza las siguientes. */
  @Override
  public void guardarMedidas(MedidasDeReferencia nuevas) {
    medidas.save(
        new MedidasDeReferenciaJpaEntity(nuevas.largoCm(), nuevas.anchoCm(), nuevas.altoCm()));
  }

  @Override
  public List<PesoDeReferencia> pesos() {
    return pesos.findAll().stream()
        .map(fila -> new PesoDeReferencia(fila.getCategoriaId(), fila.getPesoGramos()))
        .toList();
  }

  @Override
  public void guardarPeso(PesoDeReferencia peso) {
    pesos.save(new PesoDeReferenciaJpaEntity(peso.categoriaId(), peso.pesoGramos()));
  }

  @Override
  public void quitarPeso(UUID categoriaId) {
    pesos.deleteById(categoriaId);
  }
}
