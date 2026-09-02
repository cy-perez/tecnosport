import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Translation, TranslocoLoader } from '@jsverse/transloco';

@Injectable({ providedIn: 'root' })
export class TranslocoHttpLoader implements TranslocoLoader {
  private readonly http = inject(HttpClient);

  // Transloco pide "es" para el archivo raíz, y "catalogo/es" para un scope
  // cargado con provideTranslocoScope — docs/05-i18n.md:
  // assets/i18n/scopes/<scope>/{es,en}.json.
  getTranslation(langOScope: string) {
    const partes = langOScope.split('/');
    const ruta = partes.length === 2 ? `scopes/${partes[0]}/${partes[1]}` : langOScope;
    return this.http.get<Translation>(`assets/i18n/${ruta}.json`);
  }
}
