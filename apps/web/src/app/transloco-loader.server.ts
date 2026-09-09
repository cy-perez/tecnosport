import { inject, Injectable, InjectionToken } from '@angular/core';
import { Translation, TranslocoLoader } from '@jsverse/transloco';
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { from, firstValueFrom, Observable } from 'rxjs';
import { rutaDeTraduccion, TranslocoHttpLoader } from './transloco-loader';

/**
 * Los mismos JSON de i18n, leídos del disco en vez de pedidos por HTTP.
 *
 * El cargador del navegador usa una ruta **relativa** (`assets/i18n/es.json`), y en el servidor
 * eso se resuelve contra la cabecera `Host` de la petición que se está renderizando: **cada
 * render salía a la red pública a pedirse a sí mismo sus propios textos**. Funciona mientras el
 * proceso pueda alcanzarse en esa dirección, y falla en silencio cuando no —la página se sirve
 * con las claves de Transloco crudas (`catalogo.seo.ficha.titulo_con_nombre` en el `<title>`),
 * sin un solo error en el registro—. Encontrado corriendo la imagen de contenedor con el puerto
 * de fuera distinto del de dentro.
 *
 * Los archivos ya están en la imagen, al lado del servidor. Leerlos del disco quita la ida y
 * vuelta por la red de cada render y convierte un fallo mudo en uno imposible.
 */

/** `dist/tecnosport-web/server/` -> `dist/tecnosport-web/browser/assets/i18n/`. */
export const CARPETA_I18N = new InjectionToken<string>('CarpetaI18n', {
  providedIn: 'root',
  factory: () => fileURLToPath(new URL('../browser/assets/i18n/', import.meta.url)),
});

@Injectable()
export class TranslocoDiscoLoader implements TranslocoLoader {
  private readonly carpeta = inject(CARPETA_I18N);
  // En `ng serve` no hay carpeta `dist` que leer: el servidor de desarrollo sirve los assets él
  // mismo. Ahí se cae al cargador de HTTP, que en local sí alcanza su propio origen.
  private readonly porHttp = inject(TranslocoHttpLoader);
  private avisado = false;

  getTranslation(langOScope: string): Observable<Translation> {
    return from(this.leer(langOScope));
  }

  private async leer(langOScope: string): Promise<Translation> {
    const archivo = join(this.carpeta, `${rutaDeTraduccion(langOScope)}.json`);
    try {
      return JSON.parse(await readFile(archivo, 'utf8')) as Translation;
    } catch {
      if (!this.avisado) {
        // Una vez y no por petición: en desarrollo es lo esperado y no tiene que ensuciar la
        // consola, pero en un servidor construido significa que la imagen quedó mal armada.
        console.warn(`i18n: no se pudo leer ${archivo}; se piden por HTTP.`);
        this.avisado = true;
      }
      return firstValueFrom(this.porHttp.getTranslation(langOScope));
    }
  }
}
