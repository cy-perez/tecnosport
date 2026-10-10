import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen, within } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import { REPOSITORIO_PROVEEDORES_ADMIN } from '../../../proveedores/domain/repositorio-proveedores-admin.puerto';
import {
  proveedorDePrueba,
  RepositorioProveedoresAdminFalso,
} from '../../../proveedores/presentation/apoyo-proveedores.spec-util';
import {
  ArchivoDeIngesta,
  ArchivosDeIngestaPaginados,
} from '../../domain/archivo-de-ingesta.model';
import {
  REPOSITORIO_ARCHIVOS_DE_INGESTA,
  RepositorioArchivosDeIngesta,
} from '../../domain/repositorio-archivos-de-ingesta.puerto';
import { ArchivosDeIngestaAdminPage } from './archivos-de-ingesta-admin.page';

function archivo(cambios: Partial<ArchivoDeIngesta> = {}): ArchivoDeIngesta {
  return {
    id: 'a-1',
    proveedorId: proveedorDePrueba().id,
    nombreOriginal: 'Chat con Bolsos.zip',
    tamanoBytes: 2 * 1024 * 1024,
    subidoEn: '2026-10-09T15:00:00Z',
    borradoEn: null,
    lotes: 1,
    enUso: false,
    ...cambios,
  };
}

/** Como el servidor: borrar marca la fecha, y uno en uso responde 409. */
class ArchivosFalso implements RepositorioArchivosDeIngesta {
  readonly borrados: string[] = [];
  falloAlBorrar: unknown = null;

  constructor(private archivos: ArchivoDeIngesta[]) {}

  async listar(): Promise<ArchivosDeIngestaPaginados> {
    return {
      items: this.archivos,
      pagina: 0,
      totalPaginas: this.archivos.length ? 1 : 0,
      totalArchivos: this.archivos.length,
    };
  }

  async borrar(id: string): Promise<void> {
    if (this.falloAlBorrar) {
      throw this.falloAlBorrar;
    }
    this.borrados.push(id);
    this.archivos = this.archivos.map((a) =>
      a.id === id ? { ...a, borradoEn: '2026-10-10T16:00:00Z' } : a,
    );
  }
}

async function renderPagina(archivos: ArchivoDeIngesta[]) {
  const repositorio = new ArchivosFalso(archivos);
  const resultado = await render(ArchivosDeIngestaAdminPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: REPOSITORIO_ARCHIVOS_DE_INGESTA, useValue: repositorio },
      {
        provide: REPOSITORIO_PROVEEDORES_ADMIN,
        useValue: new RepositorioProveedoresAdminFalso([proveedorDePrueba()]),
      },
    ],
  });
  return { ...resultado, repositorio };
}

describe('ArchivosDeIngestaAdminPage', () => {
  it('lista cada zip con su nombre, su tamaño, sus lotes y su estado', async () => {
    await renderPagina([
      archivo(),
      archivo({ id: 'a-2', nombreOriginal: null, tamanoBytes: null, lotes: 2, enUso: true }),
      archivo({ id: 'a-3', nombreOriginal: 'viejo.zip', borradoEn: '2026-10-01T15:00:00Z' }),
    ]);

    const tabla = within(await screen.findByRole('table'));
    expect(tabla.getByText('Chat con Bolsos.zip')).toBeTruthy();
    expect(tabla.getAllByText('2 MB')).toHaveLength(2);
    expect(tabla.getByText(esAdmin.ingestas.archivos.sinNombre)).toBeTruthy();
    expect(tabla.getByText(esAdmin.ingestas.archivos.sinTamano)).toBeTruthy();
    expect(tabla.getByText(esAdmin.ingestas.archivos.estados.enUso)).toBeTruthy();
    expect(tabla.getByText(/Borrado el/)).toBeTruthy();
  });

  it('solo ofrece borrar el que sigue ahí y nadie está leyendo', async () => {
    await renderPagina([
      archivo(),
      archivo({ id: 'a-2', nombreOriginal: 'en-uso.zip', enUso: true }),
      archivo({ id: 'a-3', nombreOriginal: 'viejo.zip', borradoEn: '2026-10-01T15:00:00Z' }),
    ]);

    await screen.findByRole('table');
    const botones = screen.getAllByRole('button', { name: /^Borrar / });
    expect(botones.map((b) => b.getAttribute('aria-label'))).toEqual([
      'Borrar Chat con Bolsos.zip',
    ]);
  });

  it('borrar pregunta, se lleva el archivo y lo dice', async () => {
    const { repositorio } = await renderPagina([archivo()]);

    fireEvent.click(await screen.findByRole('button', { name: 'Borrar Chat con Bolsos.zip' }));
    const caja = within(
      screen.getByRole('group', { name: '¿Borrar Chat con Bolsos.zip del almacenamiento?' }),
    );
    fireEvent.click(caja.getByRole('button', { name: esAdmin.ingestas.archivos.borrar.confirmar }));

    expect(
      await screen.findByText('Se borró Chat con Bolsos.zip del almacenamiento.'),
    ).toBeTruthy();
    expect(repositorio.borrados).toEqual(['a-1']);
    expect(await screen.findByText(/Borrado el/)).toBeTruthy();
    expect(screen.queryByRole('group')).toBeNull();
  });

  it('cancelar cierra la pregunta sin borrar', async () => {
    const { repositorio } = await renderPagina([archivo()]);

    fireEvent.click(await screen.findByRole('button', { name: 'Borrar Chat con Bolsos.zip' }));
    fireEvent.click(
      screen.getByRole('button', { name: esAdmin.ingestas.archivos.borrar.cancelar }),
    );

    expect(screen.queryByRole('group')).toBeNull();
    expect(repositorio.borrados).toEqual([]);
  });

  it('si una ingesta empezó a leerlo mientras tanto, dice por qué no se borró', async () => {
    const { repositorio } = await renderPagina([archivo()]);
    repositorio.falloAlBorrar = new ErrorHttp(409, 'en uso', 'ARCHIVO_DE_INGESTA_EN_USO');

    fireEvent.click(await screen.findByRole('button', { name: 'Borrar Chat con Bolsos.zip' }));
    fireEvent.click(
      screen.getByRole('button', { name: esAdmin.ingestas.archivos.borrar.confirmar }),
    );

    const alerta = await screen.findByText(esAdmin.errores.archivo_de_ingesta_en_uso);
    expect(alerta.getAttribute('role')).toBe('alert');
  });

  it('sin archivos lo dice', async () => {
    await renderPagina([]);

    expect(await screen.findByText(esAdmin.ingestas.archivos.ninguno)).toBeTruthy();
  });

  it('no tiene violaciones de accesibilidad con la pregunta abierta', async () => {
    const { container } = await renderPagina([archivo()]);

    fireEvent.click(await screen.findByRole('button', { name: 'Borrar Chat con Bolsos.zip' }));
    await esperarSinViolaciones(container);
  });
});
