import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen, within } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { REPOSITORIO_PROVEEDORES_ADMIN } from '../../../proveedores/domain/repositorio-proveedores-admin.puerto';
import {
  proveedorDePrueba,
  RepositorioProveedoresAdminFalso,
} from '../../../proveedores/presentation/apoyo-proveedores.spec-util';
import { LoteIngesta, LotesPaginados, SubirExportacion } from '../../domain/ingesta.model';
import {
  REPOSITORIO_INGESTAS_ADMIN,
  RepositorioIngestasAdmin,
} from '../../domain/repositorio-ingestas-admin.puerto';
import { ListaIngestasAdminPage } from './lista-ingestas-admin.page';

function loteDePrueba(overrides: Partial<LoteIngesta> = {}): LoteIngesta {
  return {
    id: 'lote-1',
    proveedorId: 'prov-1',
    estado: 'TERMINADO',
    creadoEn: '2026-09-30T15:00:00Z',
    iniciadoEn: '2026-09-30T15:00:01Z',
    terminadoEn: '2026-09-30T15:01:00Z',
    resumen: {
      mensajesLeidos: 25,
      mensajesNuevos: 25,
      mensajesIgnorados: 0,
      publicaciones: 9,
      borradoresNuevos: 9,
      renovaciones: 0,
      agotados: 0,
      descartes: 0,
      alertas: 2,
    },
    detalleError: null,
    ...overrides,
  };
}

class RepositorioIngestasAdminFalso implements RepositorioIngestasAdmin {
  readonly subidas: SubirExportacion[] = [];

  constructor(private lotes: LoteIngesta[] = []) {}

  async listar(): Promise<LotesPaginados> {
    return {
      items: this.lotes,
      pagina: 0,
      totalPaginas: this.lotes.length ? 1 : 0,
      totalLotes: this.lotes.length,
    };
  }

  async obtener(id: string): Promise<LoteIngesta> {
    return this.lotes.find((l) => l.id === id) ?? loteDePrueba({ id });
  }

  async subir(comando: SubirExportacion): Promise<LoteIngesta> {
    this.subidas.push(comando);
    const lote = loteDePrueba({ id: 'lote-nuevo', estado: 'RECIBIDO', terminadoEn: null });
    this.lotes = [lote, ...this.lotes];
    return lote;
  }
}

async function renderPagina(lotes: LoteIngesta[] = []) {
  const repositorio = new RepositorioIngestasAdminFalso(lotes);
  const resultado = await render(ListaIngestasAdminPage, {
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
      { provide: REPOSITORIO_INGESTAS_ADMIN, useValue: repositorio },
      {
        provide: REPOSITORIO_PROVEEDORES_ADMIN,
        useValue: new RepositorioProveedoresAdminFalso([
          proveedorDePrueba(),
          proveedorDePrueba({ id: 'prov-2', nombre: 'Inactivo S.A.', activo: false }),
        ]),
      },
    ],
  });
  return { ...resultado, repositorio };
}

const s = esAdmin.ingestas.subir;

describe('ListaIngestasAdminPage', () => {
  it('muestra cada lote con el nombre del proveedor, su estado y su resultado', async () => {
    await renderPagina([loteDePrueba()]);

    const tabla = within(await screen.findByRole('table'));
    expect(tabla.getByText('Bolsos Medellín')).toBeTruthy();
    expect(tabla.getByText(esAdmin.ingestas.estados.TERMINADO)).toBeTruthy();
    expect(screen.getByText('9 publicaciones')).toBeTruthy();
    expect(screen.getByText('9 borradores nuevos, 0 renovaciones, 0 agotados')).toBeTruthy();
  });

  it('un lote con error muestra el detalle', async () => {
    await renderPagina([
      loteDePrueba({ estado: 'ERROR', detalleError: 'La exportación no trae el .txt del chat.' }),
    ]);

    expect(await screen.findByText('La exportación no trae el .txt del chat.')).toBeTruthy();
  });

  it('sin proveedor ni archivo dice qué falta y no sube nada', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.click(await screen.findByRole('button', { name: s.accion }));

    expect(await screen.findByText(s.faltanCampos)).toBeTruthy();
    expect(repositorio.subidas).toEqual([]);
  });

  /** Solo los activos se ofrecen para subir: al inactivo el servidor lo rechaza igual. */
  it('el desplegable de subida no ofrece a los proveedores inactivos', async () => {
    await renderPagina();

    const subida = within(screen.getByLabelText(s.proveedor));
    expect(await subida.findByRole('option', { name: 'Bolsos Medellín' })).toBeTruthy();
    expect(subida.queryByRole('option', { name: 'Inactivo S.A.' })).toBeNull();
    // El filtro de la tabla sí lo ofrece: sus lotes viejos siguen existiendo.
    expect(
      within(screen.getByLabelText(esAdmin.ingestas.filtroProveedor)).getByRole('option', {
        name: 'Inactivo S.A.',
      }),
    ).toBeTruthy();
  });

  it('sube el zip al proveedor elegido y lo confirma por su nombre', async () => {
    const { repositorio } = await renderPagina();
    await within(screen.getByLabelText(s.proveedor)).findByRole('option', {
      name: 'Bolsos Medellín',
    });

    fireEvent.change(screen.getByLabelText(s.proveedor), { target: { value: 'prov-1' } });
    const archivo = new File(['zip'], 'chat.zip', { type: 'application/zip' });
    fireEvent.change(screen.getByLabelText(s.archivo), { target: { files: [archivo] } });
    fireEvent.click(screen.getByRole('button', { name: s.accion }));

    expect(await screen.findByText(/chat\.zip quedó en cola/)).toBeTruthy();
    expect(repositorio.subidas).toEqual([{ proveedorId: 'prov-1', archivo }]);
  });

  it('rechaza un archivo que no es zip antes de subirlo', async () => {
    const { repositorio } = await renderPagina();
    await within(screen.getByLabelText(s.proveedor)).findByRole('option', {
      name: 'Bolsos Medellín',
    });

    fireEvent.change(screen.getByLabelText(s.proveedor), { target: { value: 'prov-1' } });
    const archivo = new File(['x'], 'foto.jpg', { type: 'image/jpeg' });
    fireEvent.change(screen.getByLabelText(s.archivo), { target: { files: [archivo] } });

    expect(await screen.findByText(s.tipoNoSoportado)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: s.accion }));
    expect(repositorio.subidas).toEqual([]);
  });

  it('filtrar por proveedor navega con el query param', async () => {
    const { fixture } = await renderPagina([loteDePrueba()]);
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    await within(screen.getByLabelText(esAdmin.ingestas.filtroProveedor)).findByRole('option', {
      name: 'Bolsos Medellín',
    });

    fireEvent.change(screen.getByLabelText(esAdmin.ingestas.filtroProveedor), {
      target: { value: 'prov-1' },
    });

    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { proveedor: 'prov-1' } }),
    );
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina([loteDePrueba()]);

    await within(await screen.findByRole('table')).findByText('Bolsos Medellín');
    await esperarSinViolaciones(container);
  });
});
