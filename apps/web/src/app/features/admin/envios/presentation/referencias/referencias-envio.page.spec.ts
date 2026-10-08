import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import {
  CategoriaConPeso,
  MedidasDeReferencia,
  ReferenciasDeEnvio,
} from '../../domain/referencias-envio.model';
import {
  REPOSITORIO_REFERENCIAS_ENVIO,
  RepositorioReferenciasEnvio,
} from '../../domain/repositorio-referencias-envio.puerto';
import { ReferenciasEnvioPage } from './referencias-envio.page';

const JEANS: CategoriaConPeso = {
  categoriaId: 'c-jeans',
  nombre: 'Jeans',
  rama: 'Dama',
  linea: 'ROPA',
  pesoGramos: 700,
};
const POLOS: CategoriaConPeso = {
  categoriaId: 'c-polos',
  nombre: 'Polos',
  rama: 'Dama',
  linea: 'ROPA',
  pesoGramos: null,
};
const UNISEX: CategoriaConPeso = {
  categoriaId: 'c-unisex',
  nombre: 'Unisex',
  rama: null,
  linea: 'CALZADO',
  pesoGramos: 700,
};

class RepositorioReferenciasFalso implements RepositorioReferenciasEnvio {
  medidasGuardadas: MedidasDeReferencia[] = [];
  pesosGuardados: { categoriaId: string; pesoGramos: number }[] = [];
  pesosQuitados: string[] = [];
  /** Si viene, `fijarPeso` falla con este error en vez de guardar. */
  errorAlFijarPeso: unknown = null;

  constructor(private referencias: ReferenciasDeEnvio) {}

  async consultar(): Promise<ReferenciasDeEnvio> {
    return this.referencias;
  }

  async fijarMedidas(medidas: MedidasDeReferencia): Promise<MedidasDeReferencia> {
    this.medidasGuardadas.push(medidas);
    this.referencias = { ...this.referencias, medidas };
    return medidas;
  }

  async fijarPeso(categoriaId: string, pesoGramos: number): Promise<void> {
    if (this.errorAlFijarPeso) {
      throw this.errorAlFijarPeso;
    }
    this.pesosGuardados.push({ categoriaId, pesoGramos });
  }

  async quitarPeso(categoriaId: string): Promise<void> {
    this.pesosQuitados.push(categoriaId);
  }
}

async function renderReferencias(referencias: Partial<ReferenciasDeEnvio> = {}) {
  const repositorio = new RepositorioReferenciasFalso({
    medidas:
      referencias.medidas === undefined
        ? { largoCm: 40, anchoCm: 30, altoCm: 10 }
        : referencias.medidas,
    categorias: referencias.categorias ?? [JEANS, POLOS, UNISEX],
  });
  const resultado = await render(ReferenciasEnvioPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_REFERENCIAS_ENVIO, useValue: repositorio },
    ],
  });
  return { ...resultado, repositorio };
}

describe('ReferenciasEnvioPage', () => {
  it('lista cada categoría con su línea, su rama y su peso', async () => {
    await renderReferencias();

    expect(await screen.findByText('Dama › Jeans')).toBeTruthy();
    expect(screen.getByText('Unisex')).toBeTruthy();
    expect(screen.getByText('Calzado deportivo')).toBeTruthy();
    expect(screen.getAllByText('700 g')).toHaveLength(2);
  });

  /** Las que no tienen peso son las que importan: sus productos sin medir no van a domicilio. */
  it('una categoría sin peso lo dice con palabras, y el resumen las cuenta', async () => {
    await renderReferencias();

    expect(await screen.findByText(esAdmin.referencias_envio.sin_peso)).toBeTruthy();
    expect(screen.getByText('3 categoría(s), 1 sin peso.')).toBeTruthy();
  });

  /** El formulario arranca con las medidas que hay: cambiar el alto es cambiar un número. */
  it('el formulario de medidas arranca con las guardadas', async () => {
    await renderReferencias();

    expect(((await screen.findByLabelText('Largo (cm)')) as HTMLInputElement).value).toBe('40');
    expect((screen.getByLabelText('Ancho (cm)') as HTMLInputElement).value).toBe('30');
    expect((screen.getByLabelText('Alto (cm)') as HTMLInputElement).value).toBe('10');
  });

  it('guardar las medidas manda las tres cifras y lo confirma', async () => {
    const { repositorio } = await renderReferencias();

    fireEvent.input(await screen.findByLabelText('Alto (cm)'), { target: { value: '5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar las medidas' }));

    await vi.waitFor(() =>
      expect(repositorio.medidasGuardadas).toEqual([{ largoCm: 40, anchoCm: 30, altoCm: 5 }]),
    );
    expect(await screen.findByText(esAdmin.referencias_envio.medidas_guardadas)).toBeTruthy();
  });

  /** No se deshabilita el botón: se valida al pulsar y se dice qué falta. */
  it('unas medidas en cero no se mandan y se dice por qué', async () => {
    const { repositorio } = await renderReferencias();

    fireEvent.input(await screen.findByLabelText('Alto (cm)'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar las medidas' }));

    expect(await screen.findByText(esAdmin.referencias_envio.faltan_medidas)).toBeTruthy();
    expect(repositorio.medidasGuardadas).toEqual([]);
  });

  it('sin medidas guardadas avisa de que nada sin medir se cotiza', async () => {
    await renderReferencias({ medidas: null });

    expect(await screen.findByText(esAdmin.referencias_envio.sin_medidas)).toBeTruthy();
  });

  it('poner el peso a una categoría sin peso lo guarda en gramos', async () => {
    const { repositorio } = await renderReferencias();

    fireEvent.click(await screen.findByRole('button', { name: 'Poner el peso de Dama › Polos' }));
    fireEvent.input(screen.getByLabelText('Peso promedio de Dama › Polos (gramos)'), {
      target: { value: '300' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar el peso' }));

    await vi.waitFor(() =>
      expect(repositorio.pesosGuardados).toEqual([{ categoriaId: 'c-polos', pesoGramos: 300 }]),
    );
    expect(await screen.findByText('Peso de Dama › Polos guardado: 300 g.')).toBeTruthy();
  });

  /** Cambiar un peso es cambiar un número: el campo arranca con el que hay. */
  it('cambiar un peso arranca con el que tiene', async () => {
    await renderReferencias();

    fireEvent.click(await screen.findByRole('button', { name: 'Cambiar el peso de Dama › Jeans' }));

    expect(
      (screen.getByLabelText('Peso promedio de Dama › Jeans (gramos)') as HTMLInputElement).value,
    ).toBe('700');
  });

  it('un peso vacío no se manda y se dice qué falta', async () => {
    const { repositorio } = await renderReferencias();

    fireEvent.click(await screen.findByRole('button', { name: 'Poner el peso de Dama › Polos' }));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar el peso' }));

    expect(await screen.findByText(esAdmin.referencias_envio.falta_peso)).toBeTruthy();
    expect(repositorio.pesosGuardados).toEqual([]);
    // El campo queda marcado, no solo el mensaje general: con lector de pantalla es lo que se oye.
    expect(
      screen.getByLabelText('Peso promedio de Dama › Polos (gramos)').getAttribute('aria-invalid'),
    ).toBe('true');
    expect(screen.getByText(esAdmin.referencias_envio.peso_invalido)).toBeTruthy();
  });

  it('quitar el peso lo manda y avisa de que sus productos sin medir ya no van a domicilio', async () => {
    const { repositorio } = await renderReferencias();

    fireEvent.click(await screen.findByRole('button', { name: 'Cambiar el peso de Dama › Jeans' }));
    fireEvent.click(screen.getByRole('button', { name: 'Quitar el peso' }));

    await vi.waitFor(() => expect(repositorio.pesosQuitados).toEqual(['c-jeans']));
    expect(await screen.findByText(/Dama › Jeans quedó sin peso/)).toBeTruthy();
  });

  /** Una categoría sin peso no tiene nada que quitar: el botón no se ofrece. */
  it('a una categoría sin peso no se le ofrece quitarlo', async () => {
    await renderReferencias();

    fireEvent.click(await screen.findByRole('button', { name: 'Poner el peso de Dama › Polos' }));

    expect(screen.queryByRole('button', { name: 'Quitar el peso' })).toBeNull();
  });

  /** El rechazo del servidor se traduce por su código, no con el mensaje genérico. */
  it('si el servidor no admite el peso, dice por qué', async () => {
    const { repositorio } = await renderReferencias();
    repositorio.errorAlFijarPeso = new ErrorHttp(
      409,
      'no se pudo guardar el peso de referencia',
      'PESO_DE_REFERENCIA_NO_ADMITIDO',
    );

    fireEvent.click(await screen.findByRole('button', { name: 'Cambiar el peso de Dama › Jeans' }));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar el peso' }));

    expect(await screen.findByText(esAdmin.errores.peso_de_referencia_no_admitido)).toBeTruthy();
  });

  it('no tiene violaciones de accesibilidad con un formulario abierto', async () => {
    const { container } = await renderReferencias();

    fireEvent.click(await screen.findByRole('button', { name: 'Poner el peso de Dama › Polos' }));

    await esperarSinViolaciones(container);
  });
});
