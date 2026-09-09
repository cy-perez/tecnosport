import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { mkdtemp, mkdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { firstValueFrom } from 'rxjs';
import { TranslocoHttpLoader } from './transloco-loader';
import { CARPETA_I18N, TranslocoDiscoLoader } from './transloco-loader.server';

async function carpetaConTraducciones(): Promise<string> {
  const carpeta = await mkdtemp(join(tmpdir(), 'i18n-'));
  await writeFile(join(carpeta, 'es.json'), JSON.stringify({ saludo: 'Hola' }), 'utf8');
  await mkdir(join(carpeta, 'scopes', 'catalogo'), { recursive: true });
  await writeFile(
    join(carpeta, 'scopes', 'catalogo', 'es.json'),
    JSON.stringify({ titulo: 'Catálogo' }),
    'utf8',
  );
  return carpeta;
}

function cargadorCon(carpeta: string): TranslocoDiscoLoader {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      TranslocoHttpLoader,
      TranslocoDiscoLoader,
      { provide: CARPETA_I18N, useValue: carpeta },
    ],
  });
  return TestBed.inject(TranslocoDiscoLoader);
}

describe('TranslocoDiscoLoader', () => {
  it('lee el archivo raíz del disco', async () => {
    const cargador = cargadorCon(await carpetaConTraducciones());

    await expect(firstValueFrom(cargador.getTranslation('es'))).resolves.toEqual({ saludo: 'Hola' });
  });

  // La convención de rutas de los scopes la comparte con el cargador de HTTP a propósito
  // (`rutaDeTraduccion`): son dos lectores del mismo archivo y no pueden divergir.
  it('resuelve un scope a scopes/<scope>/<idioma>.json', async () => {
    const cargador = cargadorCon(await carpetaConTraducciones());

    await expect(firstValueFrom(cargador.getTranslation('catalogo/es'))).resolves.toEqual({
      titulo: 'Catálogo',
    });
  });

  // En `ng serve` no existe la carpeta `dist`, y ahí el cargador de HTTP sí funciona porque el
  // servidor de desarrollo alcanza su propio origen. Sin este respaldo, arreglar el despliegue
  // habría roto el desarrollo.
  it('sin carpeta en disco cae al cargador de HTTP y avisa una sola vez', async () => {
    const cargador = cargadorCon(join(tmpdir(), 'no-existe-esta-carpeta-i18n'));
    const aviso = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const porHttp = TestBed.inject(TranslocoHttpLoader);
    const pedidos: string[] = [];
    vi.spyOn(porHttp, 'getTranslation').mockImplementation((clave: string) => {
      pedidos.push(clave);
      return { subscribe: (obs: { next: (v: unknown) => void; complete: () => void }) => {
        obs.next({ saludo: 'desde http' });
        obs.complete();
        return { unsubscribe: () => undefined };
      } } as never;
    });

    await expect(firstValueFrom(cargador.getTranslation('es'))).resolves.toEqual({
      saludo: 'desde http',
    });
    await firstValueFrom(cargador.getTranslation('en'));

    expect(pedidos).toEqual(['es', 'en']);
    expect(aviso).toHaveBeenCalledTimes(1);
    aviso.mockRestore();
  });
});
