import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Translation, TranslocoLoader } from '@jsverse/transloco';

/**
 * Transloco pide "es" para el archivo raíz y "catalogo/es" para un scope cargado con
 * `provideTranslocoScope` — docs/05-i18n.md: `assets/i18n/scopes/<scope>/{es,en}.json`.
 *
 * Vive aparte porque hay **dos** cargadores que tienen que coincidir: este, que pide por HTTP en
 * el navegador, y el del servidor, que lee el mismo archivo del disco. Si cada uno armara la ruta
 * por su cuenta, el día que cambie la convención uno de los dos se quedaría atrás y el fallo
 * sería mudo: la página sale con las claves crudas en vez de los textos.
 */
export function rutaDeTraduccion(langOScope: string): string {
  const partes = langOScope.split('/');
  return partes.length === 2 ? `scopes/${partes[0]}/${partes[1]}` : langOScope;
}

@Injectable({ providedIn: 'root' })
export class TranslocoHttpLoader implements TranslocoLoader {
  private readonly http = inject(HttpClient);

  getTranslation(langOScope: string) {
    return this.http.get<Translation>(`assets/i18n/${rutaDeTraduccion(langOScope)}.json`);
  }
}
