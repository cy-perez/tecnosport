package co.tecnosport.api.bootstrap.difusion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code META_*}: lo que hace falta para publicar en la página de Facebook y en la cuenta de
 * Instagram del negocio.
 *
 * <p><b>{@code urlBase} es configuración y no una constante</b>, al contrario que en {@code
 * WompiClient}, por una razón distinta de la de Skydropx: Meta no tiene dos ambientes —no hay un
 * sandbox de la Graph API— sino <em>versiones</em>, y la versión va dentro de la ruta ({@code
 * /v26.0/...}). Congelarla en el código obligaría a un despliegue cada vez que Meta retire una
 * versión, que es algo que hace con calendario publicado y sin preguntar.
 *
 * <p><b>{@code pageId} e {@code igUserId} no son secretos</b> y aun así entran por variable de
 * entorno, como manda la regla dura #5: son identificadores de un ambiente concreto, y el día que
 * exista una cuenta de pruebas no se quiere recompilar para apuntar a ella. Los de producción se
 * midieron contra la cuenta el 29 de septiembre de 2026 y están en {@code application.yml} como
 * valores por omisión; el token no, que ese sí es secreto y vive en Secret Manager.
 *
 * <p>El sondeo del contenedor de Instagram son parámetros técnicos, no datos de negocio: la
 * publicación es asíncrona y hay que decidir cuánto se espera antes de darla por no confirmada.
 *
 * <h2>{@code publicarDeVerdad} viene en {@code false}, y es lo importante de esta clase</h2>
 *
 * <p>No hay un sandbox de la Graph API: la única cuenta de Instagram que existe es la real del
 * negocio. Eso significa que {@code bootRun} en la máquina de quien desarrolla apunta a la cuenta
 * de verdad, y que pulsar el botón del panel mientras se prueba publicaría un post que nadie puede
 * deshacer.
 *
 * <p>Por eso el interruptor es <b>opt-in</b> y no {@code @Profile("!e2e")} como en envíos. Allí
 * equivocarse cuesta una guía de sandbox; aquí cuesta un post en la cuenta del negocio. Cuando el
 * valor por omisión de una bandera decide entre «no pasa nada» y «no tiene vuelta atrás», el valor
 * por omisión es el primero, y encenderla es un acto deliberado de quien despliega.
 */
@ConfigurationProperties(prefix = "tecnosport.meta")
public record PropiedadesMeta(
    String urlBase,
    String pageId,
    String igUserId,
    String token,
    String urlBaseDelSitio,
    String hashtagsDeMarca,
    boolean publicarDeVerdad,
    int timeoutSegundos,
    int sondeosDelContenedor,
    int esperaEntreSondeosSegundos) {

  public PropiedadesMeta {
    exigir(urlBase, "tecnosport.meta.url-base");
    exigir(pageId, "tecnosport.meta.page-id");
    exigir(igUserId, "tecnosport.meta.ig-user-id");
    exigir(token, "tecnosport.meta.token");
    exigir(urlBaseDelSitio, "tecnosport.meta.url-base-del-sitio");
    if (timeoutSegundos <= 0) {
      throw new IllegalStateException("tecnosport.meta.timeout-segundos debe ser mayor que cero.");
    }
    if (sondeosDelContenedor <= 0) {
      throw new IllegalStateException(
          "tecnosport.meta.sondeos-del-contenedor debe ser mayor que cero: sin sondear, publicar un"
              + " contenedor a medias devuelve un error que parece de permisos y no lo es.");
    }
    if (esperaEntreSondeosSegundos <= 0) {
      throw new IllegalStateException(
          "tecnosport.meta.espera-entre-sondeos-segundos debe ser mayor que cero.");
    }
  }

  private static void exigir(String valor, String clave) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException("Falta " + clave + ".");
    }
  }
}
