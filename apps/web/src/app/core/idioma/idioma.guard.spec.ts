import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, convertToParamMap, provideRouter, UrlTree } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { Observable, Subject, throwError } from 'rxjs';
import { idiomaGuard } from './idioma.guard';

/** Un Transloco que entrega el JSON cuando la prueba lo decide. */
class TranslocoFalso {
  activo = 'es';
  readonly llegada = new Subject<Record<string, string>>();
  cargar: (lang: string) => Observable<Record<string, string>> = () => this.llegada;
  setActiveLang(lang: string): void {
    this.activo = lang;
  }
  load(lang: string): Observable<Record<string, string>> {
    return this.cargar(lang);
  }
}

function ejecutar(lang: string) {
  const ruta = { paramMap: convertToParamMap({ lang }) } as ActivatedRouteSnapshot;
  return TestBed.runInInjectionContext(() => idiomaGuard(ruta, {} as never));
}

describe('idiomaGuard', () => {
  let transloco: TranslocoFalso;

  beforeEach(() => {
    transloco = new TranslocoFalso();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: TranslocoService, useValue: transloco }],
    });
  });

  // Es el defecto que se vio en dev: el servidor serializaba `/es/carrito` sin un texto, porque
  // nada esperaba al JSON del idioma.
  it('no deja pasar hasta que llega la traducción del idioma', async () => {
    let paso: unknown = 'pendiente';
    void (ejecutar('en') as Promise<unknown>).then((valor) => (paso = valor));

    await Promise.resolve();
    expect(paso).toBe('pendiente');
    expect(transloco.activo).toBe('en');
    expect(document.documentElement.lang).toBe('en');

    transloco.llegada.next({ 'tema.alternar': 'Toggle theme' });
    transloco.llegada.complete();
    await vi.waitFor(() => expect(paso).toBe(true));
  });

  it('si la traducción no llega, deja pasar igual', async () => {
    transloco.cargar = () => throwError(() => new Error('sin red'));

    await expect(ejecutar('es')).resolves.toBe(true);
  });

  it('un prefijo que no es un idioma lleva a /es', () => {
    const resultado = ejecutar('fr');

    expect(resultado).toBeInstanceOf(UrlTree);
    expect(String(resultado)).toBe('/es');
  });
});
