import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Meta, MetaDefinition, Title } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { filter } from 'rxjs';
import { enlacesAlternativos, rutaCanonica, urlAbsoluta } from './enlaces-alternativos';
import { MetadatosPagina, SeoDeRuta } from './metadatos.model';
import { origenPublico } from './origen-publico';

/** `og:locale` quiere el formato `idioma_REGIÓN`, no el prefijo de la URL. */
const LOCALES: Readonly<Record<string, string>> = { es: 'es_CO', en: 'en_US' };

/**
 * Escribe el `<head>` de cada página: título, descripción, `robots`, canónico,
 * las alternativas de idioma y Open Graph.
 *
 * **Por qué un servicio central y no una llamada por componente.** Hay
 * veinticuatro pantallas y solo una —la ficha de producto— tiene un título que
 * depende de datos. Las otras veintitrés lo tienen fijo, y hacer que cada
 * componente lo declarara serían veintitrés oportunidades de olvidarlo, cada
 * olvido invisible: una pantalla sin título no se ve rota, se ve exactamente
 * como se veían todas hasta hoy —"Tecno Sport"—. Declarado en la ruta, el
 * archivo que ya enumera las pantallas es el mismo que enumera sus metadatos, y
 * el que falte se ve leyendo una pantalla de código.
 *
 * **Dos fuentes, con precedencia clara.** La ruta declara `data.seo`; una
 * pantalla con datos propios llama a {@link aplicar} a través de
 * `usarMetadatos()` y gana, porque su componente se crea *después* de que la
 * navegación termina. La anulación se descarta en la siguiente navegación.
 */
@Injectable({ providedIn: 'root' })
export class MetadatosSeo {
  private readonly documento = inject(DOCUMENT);
  private readonly tituloDocumento = inject(Title);
  private readonly meta = inject(Meta);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly destruccion = inject(DestroyRef);

  private readonly origen = origenPublico();

  private url = '/';
  private deRuta: SeoDeRuta | null = null;
  private anulacion: MetadatosPagina | null = null;

  /**
   * Arranca la escucha. Se llama desde un inicializador de entorno en
   * `app.config.ts` y no desde el componente raíz: los inicializadores corren
   * antes de la primera navegación, y suscribirse después de ella dejaría la
   * primera pantalla —justo la que sirve el SSR, la única que ve un rastreador
   * sin JavaScript— con los metadatos de `index.html`.
   */
  escuchar(): void {
    this.router.events
      .pipe(
        filter((evento): evento is NavigationEnd => evento instanceof NavigationEnd),
        takeUntilDestroyed(this.destruccion),
      )
      .subscribe((evento) => {
        this.url = evento.urlAfterRedirects;
        this.anulacion = null;
        this.deRuta = this.seoDeLaRutaActiva();
        this.escribir(this.resolver(this.deRuta));
      });

    // Mismo motivo que `usarTraductor` (core/i18n/traductor.ts): un scope
    // perezoso puede llegar después del primer render, y el visitante puede
    // cambiar de idioma sin navegar. Sin esto el título se quedaría vacío o en
    // el idioma anterior. Cuando hay una anulación no se toca nada: ese texto lo
    // mantiene al día el efecto del componente que lo puso, y reescribirlo desde
    // aquí sería pisar el título de la ficha con el genérico de su ruta.
    this.transloco.events$
      .pipe(
        filter(
          (evento) => evento.type === 'translationLoadSuccess' || evento.type === 'langChanged',
        ),
        takeUntilDestroyed(this.destruccion),
      )
      .subscribe(() => {
        if (!this.anulacion) {
          this.escribir(this.resolver(this.deRuta));
        }
      });
  }

  /** Metadatos calculados por una pantalla a partir de sus datos. Ver `usarMetadatos()`. */
  aplicar(metadatos: MetadatosPagina): void {
    this.anulacion = metadatos;
    this.escribir(metadatos);
  }

  /**
   * El `data` de una ruta hija **no** se hereda del padre, así que se recorre la
   * rama de arriba abajo y gana la declaración más profunda: una sección entera
   * puede declararlo una sola vez en su ruta raíz —el checkout lo hace— y una
   * pantalla concreta puede afinarlo sin repetir el resto.
   */
  private seoDeLaRutaActiva(): SeoDeRuta | null {
    let nodo: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    let encontrado: SeoDeRuta | null = null;
    while (nodo) {
      const seo = nodo.data['seo'] as SeoDeRuta | undefined;
      if (seo) {
        encontrado = seo;
      }
      nodo = nodo.firstChild;
    }
    return encontrado;
  }

  private resolver(seo: SeoDeRuta | null): MetadatosPagina {
    return {
      titulo: this.traducir(seo && `${seo.clave}.titulo`) || this.nombreDelSitio(),
      descripcion: this.traducir(seo && `${seo.clave}.descripcion`),
      indexable: seo?.indexable ?? false,
    };
  }

  /**
   * Cadena vacía cuando la clave no existe o su scope todavía no llegó.
   * Transloco devuelve la clave misma en ese caso, y una pestaña que dice
   * `catalogo.seo.rejilla.titulo` es peor que una que dice "Tecno Sport".
   */
  private traducir(clave: string | null): string {
    if (!clave) {
      return '';
    }
    const texto = this.transloco.translate<string>(clave);
    return !texto || texto === clave ? '' : texto;
  }

  private nombreDelSitio(): string {
    return this.traducir('app.titulo') || this.tituloDocumento.getTitle();
  }

  private escribir(metadatos: MetadatosPagina): void {
    const ruta = rutaCanonica(this.url);
    const canonica = urlAbsoluta(this.origen, ruta);
    const idioma = this.transloco.getActiveLang();

    this.tituloDocumento.setTitle(metadatos.titulo);
    this.fijar({ name: 'description' }, metadatos.descripcion);
    this.meta.updateTag({
      name: 'robots',
      content: metadatos.indexable ? 'index,follow' : 'noindex,nofollow',
    });

    this.meta.updateTag({ property: 'og:type', content: 'website' });
    this.fijar({ property: 'og:site_name' }, this.nombreDelSitio());
    this.fijar({ property: 'og:title' }, metadatos.titulo);
    this.fijar({ property: 'og:description' }, metadatos.descripcion);
    this.meta.updateTag({ property: 'og:url', content: canonica });
    this.meta.updateTag({ property: 'og:locale', content: LOCALES[idioma] ?? idioma });
    this.fijar({ property: 'og:image' }, this.absoluta(metadatos.imagen));
    this.meta.updateTag({
      name: 'twitter:card',
      content: metadatos.imagen ? 'summary_large_image' : 'summary',
    });

    this.escribirEnlaces(canonica, ruta, metadatos.indexable);
  }

  /**
   * `og:image` tiene que ser absoluta: quien la lee es un servidor ajeno —WhatsApp, Facebook,
   * Slack— que no tiene contra qué resolver una ruta. Las imágenes de producto ya llegan absolutas
   * desde el bucket, pero la pantalla que las pasa no tiene por qué saberlo, y aquí el origen ya
   * está.
   */
  private absoluta(url: string | undefined): string {
    if (!url) {
      return '';
    }
    return /^https?:\/\//i.test(url) ? url : urlAbsoluta(this.origen, url);
  }

  /** Una etiqueta vacía no informa nada y ensucia el `<head>`: si no hay texto, se quita. */
  private fijar(etiqueta: MetaDefinition, contenido: string): void {
    const selector = etiqueta.name ? `name='${etiqueta.name}'` : `property='${etiqueta.property}'`;
    if (contenido) {
      this.meta.updateTag({ ...etiqueta, content: contenido });
    } else {
      this.meta.removeTag(selector);
    }
  }

  /**
   * `Meta` solo sabe de `<meta>`; el canónico y las alternativas son `<link>` y
   * hay que ponerlos a mano. Se marcan con `data-seo` y se borran los anteriores
   * en cada escritura: son los únicos `<link>` del documento que cambian al
   * navegar, y sin borrarlos una sesión de tres páginas dejaría tres canónicos,
   * que para un rastreador es lo mismo que no declarar ninguno.
   */
  private escribirEnlaces(canonica: string, ruta: string, indexable: boolean): void {
    const cabeza = this.documento.head;
    for (const previo of Array.from(cabeza.querySelectorAll('link[data-seo]'))) {
      previo.remove();
    }

    cabeza.appendChild(this.enlace({ rel: 'canonical', href: canonica }));

    // Sin `hreflang` en lo que no se indexa: declarar las alternativas de una
    // página marcada `noindex` es pedirle al rastreador que relacione dos URL
    // que ya le dijimos que no mirara.
    if (!indexable) {
      return;
    }
    for (const alternativa of enlacesAlternativos(this.origen, ruta)) {
      cabeza.appendChild(
        this.enlace({
          rel: 'alternate',
          hreflang: alternativa.hreflang,
          href: alternativa.href,
        }),
      );
    }
  }

  private enlace(atributos: Record<string, string>): HTMLLinkElement {
    const elemento = this.documento.createElement('link');
    for (const [nombre, valor] of Object.entries(atributos)) {
      elemento.setAttribute(nombre, valor);
    }
    elemento.setAttribute('data-seo', '');
    return elemento;
  }
}
