import { provideZonelessChangeDetection } from '@angular/core';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import { describe, expect, it, vi } from 'vitest';
import esAdmin from '../../../../../assets/i18n/scopes/admin/es.json';
import { OrdenDeDifusion, PublicacionEnRed, RedSocial } from '../domain/difusion.model';
import {
  REPOSITORIO_DIFUSION,
  RepositorioDifusion,
  ResultadoDifusion,
} from '../domain/repositorio-difusion.puerto';
import { PanelDeDifusion } from './panel-de-difusion';

const PRODUCTO = '01a0ca12-ce7f-7ae2-95e8-e6e0127dc7a6';

/** Doble escrito a mano, como el resto del panel. */
class RepositorioDoble implements RepositorioDifusion {
  ordenes: OrdenDeDifusion[] = [];
  resultado: ResultadoDifusion = { tipo: 'OK', publicaciones: [] };
  publicaciones: PublicacionEnRed[] = [];
  pies: Record<RedSocial, string> = {
    FACEBOOK: 'JBL Grip — $299.900\n\nhttps://www.tecnosport.co/es/productos/jbl-grip',
    INSTAGRAM: 'JBL Grip — $299.900\n\nEnlace en la bio 🔗',
  };

  async difundir(orden: OrdenDeDifusion): Promise<ResultadoDifusion> {
    this.ordenes.push(orden);
    return this.resultado;
  }

  async historial(): Promise<PublicacionEnRed[]> {
    return this.publicaciones;
  }

  async proponerPie(_productoId: string, red: RedSocial): Promise<string> {
    return this.pies[red];
  }
}

async function montar(repositorio: RepositorioDoble) {
  return render(PanelDeDifusion, {
    inputs: { productoId: PRODUCTO },
    providers: [
      provideZonelessChangeDetection(),
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: REPOSITORIO_DIFUSION, useValue: repositorio },
    ],
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { 'admin/es': esAdmin },
        translocoConfig: { availableLangs: ['es'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
  });
}

describe('PanelDeDifusion', () => {
  it('arranca en Instagram y vuelca el pie que propone el servidor', async () => {
    const repositorio = new RepositorioDoble();

    await montar(repositorio);

    const caja = await screen.findByLabelText(/texto de la publicación/i);
    await vi.waitFor(() =>
      expect((caja as HTMLTextAreaElement).value).toContain('Enlace en la bio'),
    );
  });

  /** El pie no es el mismo texto en las dos redes: en Facebook lleva el enlace. */
  it('al cambiar de red vuelve a pedir el pie de esa red', async () => {
    const repositorio = new RepositorioDoble();
    await montar(repositorio);
    await screen.findByLabelText(/texto de la publicación/i);

    fireEvent.click(screen.getByRole('button', { name: /^facebook/i }));

    await vi.waitFor(() => {
      const caja = screen.getByLabelText(/texto de la publicación/i) as HTMLTextAreaElement;
      expect(caja.value).toContain('https://www.tecnosport.co/es/productos/jbl-grip');
    });
  });

  /**
   * La que de verdad importa: si alguien escribió su texto, una respuesta de red que llega tarde
   * no se lo puede pisar. Es de las cosas que hacen desconfiar de una herramienta para siempre.
   */
  it('no sobrescribe el texto que la persona ya editó', async () => {
    const repositorio = new RepositorioDoble();
    await montar(repositorio);
    const caja = (await screen.findByLabelText(/texto de la publicación/i)) as HTMLTextAreaElement;
    await vi.waitFor(() => expect(caja.value).not.toBe(''));

    fireEvent.input(caja, { target: { value: 'Lo escribí yo' } });
    fireEvent.click(screen.getByRole('button', { name: /^facebook/i }));

    await vi.waitFor(() => expect(caja.value).toBe('Lo escribí yo'));
  });

  it('manda la red elegida y el texto de la caja', async () => {
    const repositorio = new RepositorioDoble();
    await montar(repositorio);
    const caja = (await screen.findByLabelText(/texto de la publicación/i)) as HTMLTextAreaElement;
    await vi.waitFor(() => expect(caja.value).not.toBe(''));

    fireEvent.click(screen.getByRole('button', { name: /publicar en instagram/i }));

    await vi.waitFor(() => {
      expect(repositorio.ordenes).toHaveLength(1);
      expect(repositorio.ordenes[0].redes).toEqual(['INSTAGRAM']);
      expect(repositorio.ordenes[0].pieDeFoto).toContain('Enlace en la bio');
    });
  });

  it('enseña el motivo cuando el producto no se puede difundir', async () => {
    const repositorio = new RepositorioDoble();
    repositorio.resultado = {
      tipo: 'NO_DIFUNDIBLE',
      motivo: 'El producto no tiene imagen principal.',
    };
    await montar(repositorio);
    const caja = (await screen.findByLabelText(/texto de la publicación/i)) as HTMLTextAreaElement;
    await vi.waitFor(() => expect(caja.value).not.toBe(''));

    fireEvent.click(screen.getByRole('button', { name: /publicar en instagram/i }));

    // Por texto y no por `role="alert"`: `ts-campo` monta el suyo siempre —vacío— para que el
    // lector de pantalla lo anuncie al llenarse, así que `findByRole('alert')` encuentra ese.
    expect(await screen.findByText(/no tiene imagen principal/i)).toBeTruthy();
  });

  it('avisa cuando la difusión ya está en marcha en vez de repetirla', async () => {
    const repositorio = new RepositorioDoble();
    repositorio.resultado = { tipo: 'YA_EN_MARCHA' };
    await montar(repositorio);
    const caja = (await screen.findByLabelText(/texto de la publicación/i)) as HTMLTextAreaElement;
    await vi.waitFor(() => expect(caja.value).not.toBe(''));

    fireEvent.click(screen.getByRole('button', { name: /publicar en instagram/i }));

    expect(await screen.findByText(/espera a que termine/i)).toBeTruthy();
  });

  it('dice cuándo se difundió por última vez en cada red', async () => {
    const repositorio = new RepositorioDoble();
    repositorio.publicaciones = [
      {
        id: '1',
        red: 'INSTAGRAM',
        estado: 'PUBLICADA',
        idPublicacionExterna: '181961',
        pieDeFoto: 'JBL Grip — $299.900',
        urlImagen: 'https://storage.googleapis.com/b/principal.jpg',
        solicitadaEn: '2026-09-29T15:00:00Z',
        publicadaEn: '2026-09-29T15:00:04Z',
        detalleDelFallo: null,
      },
    ];

    await montar(repositorio);

    expect(await screen.findByRole('button', { name: /instagram.*última vez/is })).toBeTruthy();
    expect(screen.getByRole('button', { name: /facebook.*nunca/is })).toBeTruthy();
  });
});
