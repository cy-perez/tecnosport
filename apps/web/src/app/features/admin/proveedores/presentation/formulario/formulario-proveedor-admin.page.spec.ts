import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { Proveedor } from '../../domain/proveedor.model';
import { REPOSITORIO_PROVEEDORES_ADMIN } from '../../domain/repositorio-proveedores-admin.puerto';
import {
  proveedorDePrueba,
  RepositorioProveedoresAdminFalso,
} from '../apoyo-proveedores.spec-util';
import { FormularioProveedorAdminPage } from './formulario-proveedor-admin.page';

function rutaCon(id: string | null) {
  const paramMap = convertToParamMap(id ? { id } : {});
  return { paramMap: of(paramMap), snapshot: { paramMap } };
}

async function renderPagina(proveedores: Proveedor[] = [], id: string | null = null) {
  const repositorio = new RepositorioProveedoresAdminFalso(proveedores);
  const resultado = await render(FormularioProveedorAdminPage, {
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
      { provide: REPOSITORIO_PROVEEDORES_ADMIN, useValue: repositorio },
      { provide: ActivatedRoute, useValue: rutaCon(id) },
    ],
  });
  return { ...resultado, repositorio };
}

const f = esAdmin.proveedores.formulario;
const e = esAdmin.proveedores.eliminar;

function escribir(etiqueta: string, valor: string) {
  fireEvent.input(screen.getByLabelText(etiqueta), { target: { value: valor } });
}

describe('FormularioProveedorAdminPage', () => {
  it('con el formulario vacío dice qué falta y no crea nada', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.click(await screen.findByRole('button', { name: f.crear }));

    expect(await screen.findByText(f.faltanCampos)).toBeTruthy();
    expect(repositorio.creados).toEqual([]);
  });

  /** El margen multiplica el costo: por debajo de 1 se vendería a pérdida, y el backend lo rechaza. */
  it('un margen menor que 1 no se manda', async () => {
    const { repositorio } = await renderPagina();

    escribir(f.nombre, 'Bolsos Medellín');
    escribir(f.remitente, 'Bolsos Medellín');
    escribir(f.telefono, '573001234567');
    fireEvent.change(screen.getByLabelText(f.linea), { target: { value: 'BOLSOS' } });
    escribir(f.margen, '0.9');
    fireEvent.click(screen.getByRole('button', { name: f.crear }));

    expect(await screen.findByText(f.faltanCampos)).toBeTruthy();
    expect(repositorio.creados).toEqual([]);
  });

  it('crea el proveedor con la línea elegida y vuelve a la lista', async () => {
    const { repositorio, fixture } = await renderPagina();
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    escribir(f.nombre, 'Bolsos Medellín');
    escribir(f.remitente, 'Bolsos Medellín');
    escribir(f.telefono, '573001234567');
    fireEvent.change(screen.getByLabelText(f.linea), { target: { value: 'BOLSOS' } });
    escribir(f.margen, '1,35');
    fireEvent.click(screen.getByRole('button', { name: f.crear }));

    await vi.waitFor(() => {
      expect(repositorio.creados).toHaveLength(1);
      expect(navegar).toHaveBeenCalled();
    });
    expect(repositorio.creados[0]).toMatchObject({
      nombre: 'Bolsos Medellín',
      linea: 'BOLSOS',
      factorDeMargen: 1.35,
      activo: true,
    });
  });

  it('al editar prellena el formulario y guarda con el id de la ruta', async () => {
    const { repositorio } = await renderPagina([proveedorDePrueba()], 'prov-1');

    const nombre = (await screen.findByLabelText(f.nombre)) as HTMLInputElement;
    await vi.waitFor(() => expect(nombre.value).toBe('Bolsos Medellín'));

    escribir(f.nombre, 'Bolsos del Valle');
    fireEvent.click(screen.getByRole('button', { name: f.guardar }));

    expect(await screen.findByText(/Bolsos del Valle quedó guardado/)).toBeTruthy();
    expect(repositorio.editados[0]).toMatchObject({
      id: 'prov-1',
      datos: { nombre: 'Bolsos del Valle', linea: 'BOLSOS' },
    });
  });

  it('al crear no ofrece eliminar', async () => {
    await renderPagina();

    await screen.findByRole('button', { name: f.crear });
    expect(screen.queryByRole('button', { name: e.accion })).toBeNull();
  });

  /** Dos pasos: el primer botón solo pregunta, y solo el segundo elimina. */
  it('elimina tras confirmar y vuelve a la lista', async () => {
    const { repositorio, fixture } = await renderPagina([proveedorDePrueba()], 'prov-1');
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fireEvent.click(await screen.findByRole('button', { name: e.accion }));
    expect(repositorio.eliminados).toEqual([]);
    expect(await screen.findByText('¿Eliminar a Bolsos Medellín?')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: e.confirmar }));

    await vi.waitFor(() => {
      expect(repositorio.eliminados).toEqual(['prov-1']);
      expect(navegar).toHaveBeenCalled();
    });
  });

  it('cancelar cierra la confirmación sin eliminar', async () => {
    const { repositorio } = await renderPagina([proveedorDePrueba()], 'prov-1');

    fireEvent.click(await screen.findByRole('button', { name: e.accion }));
    fireEvent.click(screen.getByRole('button', { name: e.cancelar }));

    expect(screen.queryByRole('button', { name: e.confirmar })).toBeNull();
    expect(repositorio.eliminados).toEqual([]);
  });

  /** El 409 trae cuántos productos en `productos`: la pantalla lo dice en vez de "intenta de nuevo". */
  it('con productos en el catálogo dice cuántos y no navega', async () => {
    const { repositorio, fixture } = await renderPagina([proveedorDePrueba()], 'prov-1');
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    repositorio.errorAlEliminar = new ErrorHttp(409, 'conflicto', 'PROVEEDOR_CON_PRODUCTOS', {
      productos: '3',
    });

    fireEvent.click(await screen.findByRole('button', { name: e.accion }));
    fireEvent.click(screen.getByRole('button', { name: e.confirmar }));

    expect(
      await screen.findByText(/No se puede eliminar: 3 producto\(s\) del catálogo/),
    ).toBeTruthy();
    expect(navegar).not.toHaveBeenCalled();
  });

  it('con una ingesta en curso dice que espere', async () => {
    const { repositorio } = await renderPagina([proveedorDePrueba()], 'prov-1');
    repositorio.errorAlEliminar = new ErrorHttp(409, 'conflicto', 'PROVEEDOR_CON_INGESTA_EN_CURSO');

    fireEvent.click(await screen.findByRole('button', { name: e.accion }));
    fireEvent.click(screen.getByRole('button', { name: e.confirmar }));

    expect(await screen.findByText(esAdmin.errores.proveedor_con_ingesta_en_curso)).toBeTruthy();
  });

  it('no tiene violaciones de accesibilidad con la confirmación abierta', async () => {
    const { container } = await renderPagina([proveedorDePrueba()], 'prov-1');

    fireEvent.click(await screen.findByRole('button', { name: e.accion }));
    await screen.findByRole('button', { name: e.confirmar });
    await esperarSinViolaciones(container);
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina();

    await screen.findByLabelText(f.nombre);
    await esperarSinViolaciones(container);
  });
});
