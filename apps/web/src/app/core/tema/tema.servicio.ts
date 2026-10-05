import { isPlatformBrowser } from '@angular/common';
import { afterNextRender, DOCUMENT, inject, Injectable, PLATFORM_ID } from '@angular/core';
import { leerToken } from '../tokens/leer-token';

/**
 * Dueño único de la preferencia de tema: la persiste y la aplica al documento.
 * Antes esto vivía dentro de `shared/ts-selector-tema`, que así cargaba con
 * tres responsabilidades a la vez —dibujar un select, escribir una cookie y
 * tocar el DOM del documento—. Un componente compartido no decide la política
 * de persistencia del sitio.
 *
 * **Dos temas y no tres.** Hubo una tercera opción, "sistema", que guardaba la
 * intención de seguir al sistema operativo y se resolvía tarde. Se quitó: el
 * control es un botón de alternar de dos estados. El `prefers-color-scheme`
 * **no desapareció con ella** — sigue decidiendo qué ve quien llega sin cookie,
 * en el script en línea de `index.html`. La diferencia es que ahora es un valor
 * inicial y no una preferencia que se persiste.
 *
 * Reparto con el SSR, que es el motivo de que el servicio exista:
 *   - con cookie, el servidor escribe `data-tema` en el HTML que sirve
 *     (`tema-ssr.ts`), así que el tema correcto llega en el primer byte;
 *   - sin cookie, lo escribe el script en línea de `index.html` antes del
 *     primer pintado, leyendo `prefers-color-scheme`.
 * En los dos casos, cuando este servicio corre **ya hay un tema aplicado**.
 *
 * Por eso no guarda ninguna señal con el tema actual: `data-tema` en `<html>`
 * *es* el estado aplicado, y lo escriben tres sitios distintos. Una señal sería
 * una segunda copia que puede separarse de la primera, y hoy nadie la leería —
 * los tokens se redefinen bajo `[data-tema="oscuro"]`, así que el color cambia
 * sin que un solo componente se entere (`docs/04-ui-marca.md`, "Modo oscuro").
 */

const TEMAS = ['claro', 'oscuro'] as const;

export type Tema = (typeof TEMAS)[number];

const COOKIE = 'ts-tema';

/** Un año: la preferencia de tema no caduca sola (docs/04-ui-marca.md). */
const UN_ANIO_EN_SEGUNDOS = 31536000;

/**
 * El token con el que se pinta la barra del navegador: el fondo del encabezado, que es lo que queda
 * pegado a ella. Se lee del estilo computado y no se copia: con `[data-tema="oscuro"]` el mismo
 * nombre devuelve otro color, que es justo lo que hace falta.
 */
const TOKEN_BARRA_DEL_NAVEGADOR = '--color-superficie';

export function esTema(valor: unknown): valor is Tema {
  return typeof valor === 'string' && (TEMAS as readonly string[]).includes(valor);
}

@Injectable({ providedIn: 'root' })
export class ServicioTema {
  private readonly documento = inject(DOCUMENT);
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));

  constructor() {
    // Al hidratar ya hay un tema aplicado —lo puso el servidor o el script de `index.html`—, y la
    // `<meta name="theme-color">` todavía dice lo que diga `index.html`, que es uno solo para los
    // dos temas.
    if (this.esNavegador) {
      afterNextRender(() => this.sincronizarBarraDelNavegador());
    }
  }

  /**
   * El tema que el documento tiene puesto ahora mismo. No lee la cookie: el
   * atributo ya la refleja —lo escribió el servidor— y además cubre el caso en
   * que no hay cookie, donde la cookie no diría nada y el atributo sí.
   *
   * `claro` cuando el atributo falta o trae basura. Que falte solo puede pasar
   * si el script en línea no corrió; devolver un tema válido deja el botón
   * usable en vez de dejarlo sin efecto.
   */
  temaAplicado(): Tema {
    const aplicado = this.documento.documentElement.getAttribute('data-tema');
    return esTema(aplicado) ? aplicado : 'claro';
  }

  /** Lo que hace el botón: pasa al otro tema y lo persiste. */
  alternar(): void {
    this.elegir(this.temaAplicado() === 'oscuro' ? 'claro' : 'oscuro');
  }

  /** Persiste la elección y la aplica. La única vía para cambiar de tema. */
  elegir(tema: Tema): void {
    if (!this.esNavegador) {
      return;
    }
    this.documento.cookie = `${COOKIE}=${tema}; path=/; max-age=${UN_ANIO_EN_SEGUNDOS}; samesite=lax`;
    this.documento.documentElement.setAttribute('data-tema', tema);
    this.sincronizarBarraDelNavegador();
  }

  /**
   * Pone en la `<meta name="theme-color">` el color del tema aplicado. Fue `#0E1217` fijo hasta el 4
   * de octubre de 2026: la barra del navegador en el teléfono salía grafito casi negro también con el
   * sitio en claro, una franja oscura pegada a un encabezado blanco.
   *
   * Si el token no se puede leer no toca nada: dejar el valor de `index.html` es mejor que vaciarlo.
   */
  private sincronizarBarraDelNavegador(): void {
    const color = leerToken(this.documento, TOKEN_BARRA_DEL_NAVEGADOR);
    if (!color) {
      return;
    }
    let meta = this.documento.querySelector('meta[name="theme-color"]');
    if (!meta) {
      meta = this.documento.createElement('meta');
      meta.setAttribute('name', 'theme-color');
      this.documento.head.appendChild(meta);
    }
    meta.setAttribute('content', color);
  }
}
