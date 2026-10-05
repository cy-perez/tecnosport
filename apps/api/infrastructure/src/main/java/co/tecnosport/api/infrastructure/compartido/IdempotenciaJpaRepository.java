package co.tecnosport.api.infrastructure.compartido;

import co.tecnosport.api.infrastructure.compartido.entidad.IdempotenciaJpaEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotenciaJpaRepository extends JpaRepository<IdempotenciaJpaEntity, String> {

  /**
   * Reclama la llave en una sola sentencia. Leer y después insertar dejaba una carrera: dos
   * peticiones con la misma llave —el doble clic— leían las dos "no hay", la segunda chocaba contra
   * la llave primaria y, como esto corre en un filtro antes del {@code DispatcherServlet}, salía un
   * 500 en vez del 409 que el filtro promete. Devuelve 1 si esta llamada la reclamó.
   */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          "insert into idempotencia (llave, metodo, ruta, estado, creado_en)"
              + " values (:llave, :metodo, :ruta, 'PENDIENTE', :ahora)"
              + " on conflict (llave) do nothing")
  int reclamarSiEstaLibre(
      @Param("llave") String llave,
      @Param("metodo") String metodo,
      @Param("ruta") String ruta,
      @Param("ahora") Instant ahora);

  /**
   * Borra lo que ya no protege nada: las respuestas completadas que pasaron su vigencia y las
   * reclamaciones que nadie terminó —un proceso que murió a mitad de la petición—. Con {@code
   * llave} nula, todas las que cumplan; con llave, solo esa.
   */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          "delete from idempotencia where (cast(:llave as varchar) is null or llave = :llave) and"
              + " ((estado = 'COMPLETADA' and completado_en < :completadasAntesDe)"
              + " or (estado = 'PENDIENTE' and creado_en < :pendientesAntesDe))")
  int borrarVencidas(
      @Param("llave") String llave,
      @Param("completadasAntesDe") Instant completadasAntesDe,
      @Param("pendientesAntesDe") Instant pendientesAntesDe);
}
