import { isPlatformBrowser } from '@angular/common';
import { DOCUMENT, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';

/**
 * Dueño único de la preferencia de tema: la lee, la persiste y la aplica al
 * documento. Antes esto vivía dentro de `shared/ts-selector-tema`, que así
 * cargaba con tres responsabilidades a la vez —dibujar un select, escribir una
 * cookie y tocar el DOM del documento—. Un componente compartido no decide la
 * política de persistencia del sitio.
 *
 * Tres opciones para el usuario y no dos (docs/04-ui-marca.md): "sistema" es
 * una preferencia distinta de "claro", no un sinónimo del valor por omisión.
 *
 * Reparto con el SSR, que es el motivo de que el servicio exista y no sea una
 * simple señal:
 *   - `claro` y `oscuro` los resuelve el servidor leyendo la cookie
 *     (`tema-ssr.ts`), así que el HTML ya llega con `data-tema` puesto.
 *   - `sistema` no se puede resolver ahí —el servidor no conoce el
 *     `prefers-color-scheme` del visitante— y lo termina el script en línea de
 *     `index.html` antes del primer pintado.
 * En los dos casos, cuando este servicio corre ya hay un tema aplicado: su
 * trabajo al arrancar es *reflejarlo*, no volver a decidirlo.
 */

const TEMAS = ['claro', 'oscuro', 'sistema'] as const;

export type Tema = (typeof TEMAS)[number];

/** Lo único que `data-tema` entiende: "sistema" se resuelve antes de escribir. */
export type TemaVisual = Exclude<Tema, 'sistema'>;

const COOKIE = 'ts-tema';

/** Un año: la preferencia de tema no caduca sola (docs/04-ui-marca.md). */
const UN_ANIO_EN_SEGUNDOS = 31536000;

export function esTema(valor: unknown): valor is Tema {
  return typeof valor === 'string' && (TEMAS as readonly string[]).includes(valor);
}

@Injectable({ providedIn: 'root' })
export class ServicioTema {
  private readonly documento = inject(DOCUMENT);
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));

  /** La intención del usuario, que no es lo mismo que lo que se ve. */
  private readonly _preferencia = signal<Tema>('sistema');
  readonly preferencia = this._preferencia.asReadonly();

  readonly opciones: readonly Tema[] = TEMAS;

  /**
   * Alinea la señal con lo que el servidor ya aplicó. Se llama desde un
   * `afterNextRender`: la cookie vive en `document`, que en el servidor no
   * existe. No reescribe la cookie ni `data-tema` a propósito — volver a
   * fijarlos con el mismo valor no cambiaría nada y sí arriesga un repintado.
   */
  sincronizarConCookie(): Tema {
    const guardada = this.leerCookie();
    if (guardada) {
      this._preferencia.set(guardada);
    }
    return this._preferencia();
  }

  /** Persiste la elección y la aplica. La única vía para cambiar de tema. */
  elegir(tema: Tema): void {
    this._preferencia.set(tema);
    if (!this.esNavegador) {
      return;
    }
    this.documento.cookie = `${COOKIE}=${tema}; path=/; max-age=${UN_ANIO_EN_SEGUNDOS}; samesite=lax`;
    this.documento.documentElement.setAttribute('data-tema', this.resolver(tema));
  }

  private leerCookie(): Tema | undefined {
    if (!this.esNavegador) {
      return undefined;
    }
    const valor = this.documento.cookie
      .split('; ')
      .find((fragmento) => fragmento.startsWith(`${COOKIE}=`))
      ?.split('=')[1];
    return esTema(valor) ? valor : undefined;
  }

  /**
   * `defaultView` en vez de la global `window`: el servicio se instancia
   * también en el servidor (`providedIn: 'root'`) y la regla de SSR del
   * proyecto es que ninguna API del navegador se toque sin guardia.
   */
  private resolver(tema: Tema): TemaVisual {
    if (tema !== 'sistema') {
      return tema;
    }
    const prefiereOscuro =
      this.documento.defaultView?.matchMedia('(prefers-color-scheme: dark)').matches ?? false;
    return prefiereOscuro ? 'oscuro' : 'claro';
  }
}
