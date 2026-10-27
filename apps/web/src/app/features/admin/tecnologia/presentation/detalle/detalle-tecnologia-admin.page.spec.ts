import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { Categoria, Marca } from '../../../../catalogo/domain/producto.model';
import {
  REPOSITORIO_CATEGORIAS,
  RepositorioCategorias,
} from '../../../../catalogo/domain/repositorio-categorias.puerto';
import {
  REPOSITORIO_MARCAS,
  RepositorioMarcas,
} from '../../../../catalogo/domain/repositorio-marcas.puerto';
import { REPOSITORIO_PROVEEDORES_ADMIN } from '../../../proveedores/domain/repositorio-proveedores-admin.puerto';
import {
  proveedorDePrueba,
  RepositorioProveedoresAdminFalso,
} from '../../../proveedores/presentation/apoyo-proveedores.spec-util';
import { BorradorTecnologia } from '../../domain/borrador-tecnologia.model';
import { REPOSITORIO_BORRADORES_TECNOLOGIA } from '../../domain/repositorio-borradores-tecnologia.puerto';
import {
  borradorTecnologiaDePrueba,
  RepositorioBorradoresTecnologiaFalso,
} from '../apoyo-tecnologia.spec-util';
import { DetalleTecnologiaAdminPage } from './detalle-tecnologia-admin.page';

const SAMSUNG: Marca = { id: 'm-sam', nombre: 'Samsung' };
const CELULARES: Categoria = {
  id: 'c-cel',
  nombre: 'Celulares',
  slug: 'celulares',
  linea: 'TECNOLOGIA',
  padreId: null,
  hashtags: [],
  escalaTallas: [],
};

class RepositorioMarcasFalso implements RepositorioMarcas {
  async listarTodas(): Promise<Marca[]> {
    return [SAMSUNG, { id: 'm-gen', nombre: 'Genérica' }];
  }
}

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [CELULARES];
  }
}

function rutaCon(id: string) {
  const paramMap = convertToParamMap({ id });
  return { paramMap: of(paramMap), snapshot: { paramMap } };
}

async function renderPagina(borrador: BorradorTecnologia = borradorTecnologiaDePrueba()) {
  const repositorio = new RepositorioBorradoresTecnologiaFalso([borrador]);
  const resultado = await render(DetalleTecnologiaAdminPage, {
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
      { provide: ActivatedRoute, useValue: rutaCon(borrador.id) },
      { provide: REPOSITORIO_BORRADORES_TECNOLOGIA, useValue: repositorio },
      {
        provide: REPOSITORIO_PROVEEDORES_ADMIN,
        useValue: new RepositorioProveedoresAdminFalso([proveedorDePrueba()]),
      },
      { provide: REPOSITORIO_MARCAS, useValue: new RepositorioMarcasFalso() },
      { provide: REPOSITORIO_CATEGORIAS, useValue: new RepositorioCategoriasFalso() },
    ],
  });
  await screen.findByRole('heading', { name: 'Samsung Galaxy A17 5G' });
  return { ...resultado, repositorio };
}

function grupoDe(titulo: string) {
  return within(screen.getByRole('group', { name: titulo }));
}

const UNA_SIM = 'Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM';
const DUAL = 'Samsung Galaxy A17 5G 8GB RAM 256GB Dual SIM';

describe('DetalleTecnologiaAdminPage', () => {
  /**
   * El campo es de contado; la ayuda dice costo, mercado y la sugerencia en sus dos caras, la de
   * contado y la publicada con la comisión de Wompi (ADR-0095). Sin mercado no hay sugerencia.
   */
  it('la ayuda del precio dice costo, mercado y el sugerido de contado y publicado', async () => {
    await renderPagina();

    expect(esAdmin.tecnologia.detalle.precioVenta).toMatch(/contado/);
    // Intl separa el símbolo con un espacio duro: se normaliza para comparar.
    const ayudaDe = (titulo: string) => {
      const campo = grupoDe(titulo).getByLabelText(esAdmin.tecnologia.detalle.precioVenta);
      const ayuda = document.getElementById(campo.getAttribute('aria-describedby') ?? '');
      return ayuda?.textContent?.replace(/\s+/g, ' ') ?? '';
    };
    expect(ayudaDe(UNA_SIM)).toContain('$ 675.000');
    expect(ayudaDe(UNA_SIM)).toContain('$ 849.900');
    expect(ayudaDe(UNA_SIM)).toContain('$ 879.000');
    expect(ayudaDe(DUAL)).toContain('$ 690.000');
    expect(ayudaDe(DUAL)).not.toMatch(/sugerido/i);
  });

  it('arranca con los colores que sugiere la lista y el precio de contado sugerido', async () => {
    await renderPagina();

    const unaSim = grupoDe(UNA_SIM);
    expect((unaSim.getByLabelText('Negro') as HTMLInputElement).checked).toBe(true);
    expect((unaSim.getByLabelText('Gris') as HTMLInputElement).checked).toBe(false);
    expect(
      (unaSim.getByLabelText(esAdmin.tecnologia.detalle.precioVenta) as HTMLInputElement).value,
    ).toBe('849900');
    const dual = grupoDe(DUAL);
    expect((dual.getByLabelText('Negro') as HTMLInputElement).checked).toBe(false);
    expect(
      (dual.getByLabelText(esAdmin.tecnologia.detalle.precioVenta) as HTMLInputElement).value,
    ).toBe('');
  });

  it('aprobar guarda la elección y aprueba con la marca y la categoría que sugiere la lista', async () => {
    const { repositorio } = await renderPagina();
    await screen.findByRole('option', { name: 'Samsung' });

    fireEvent.click(grupoDe(UNA_SIM).getByLabelText('Gris'));
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion }));

    expect(await screen.findByText(esAdmin.tecnologia.aprobar.hecho)).toBeTruthy();
    expect(repositorio.elecciones[0].elecciones).toEqual([
      { sku: 'a17-1-sim', colores: ['Negro', 'Gris'], precioVenta: 849900 },
      { sku: 'a17-dual-sim', colores: [], precioVenta: null },
    ]);
    expect(repositorio.aprobaciones).toEqual([
      { id: 'bt-1', aprobacion: { marcaId: 'm-sam', categoriaId: 'c-cel' } },
    ]);
    expect(screen.getByRole('link', { name: esAdmin.tecnologia.detalle.verProducto })).toBeTruthy();
  });

  it('sin colores marcados no hay nada que vender, y no se envía', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.click(grupoDe(UNA_SIM).getByLabelText('Negro'));
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion }));

    expect(await screen.findByText(esAdmin.tecnologia.aprobar.nadaQueVender)).toBeTruthy();
    expect(repositorio.elecciones).toHaveLength(0);
    expect(repositorio.aprobaciones).toHaveLength(0);
  });

  it('una configuración con colores y sin precio no se aprueba', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.click(grupoDe(DUAL).getByLabelText('Azul'));
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion }));

    expect(await screen.findByText(esAdmin.tecnologia.aprobar.faltaPrecio)).toBeTruthy();
    expect(repositorio.aprobaciones).toHaveLength(0);
  });

  it('las configuraciones nuevas de un producto que ya se vende no piden marca ni categoría', async () => {
    const { repositorio } = await renderPagina(
      borradorTecnologiaDePrueba({ productoId: 'prod-9' }),
    );

    expect(screen.queryByLabelText(new RegExp(esAdmin.tecnologia.aprobar.marca))).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion }));

    expect(await screen.findByText(esAdmin.tecnologia.aprobar.hecho)).toBeTruthy();
    expect(repositorio.aprobaciones[0].aprobacion).toEqual({ marcaId: null, categoriaId: null });
  });

  it('sin paleta, los colores se escriben a mano', async () => {
    const { repositorio } = await renderPagina(
      borradorTecnologiaDePrueba({
        paleta: [],
        configuraciones: [borradorTecnologiaDePrueba().configuraciones[0]],
        productoId: 'prod-9',
      }),
    );

    const campo = screen.getByLabelText(
      esAdmin.tecnologia.detalle.coloresEscritos,
    ) as HTMLInputElement;
    expect(campo.value).toBe('Negro');
    fireEvent.input(campo, { target: { value: 'Negro, Azul claro' } });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion }));

    await screen.findByText(esAdmin.tecnologia.aprobar.hecho);
    expect(repositorio.elecciones[0].elecciones[0].colores).toEqual(['Negro', 'Azul claro']);
  });

  it('rechazar pide el motivo', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.rechazar.accion }));
    expect(await screen.findByText(esAdmin.tecnologia.rechazar.faltaMotivo)).toBeTruthy();
    expect(repositorio.rechazos).toHaveLength(0);

    fireEvent.input(screen.getByLabelText(new RegExp(esAdmin.tecnologia.rechazar.motivo)), {
      target: { value: 'No vendemos esta gama' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.rechazar.accion }));

    expect(await screen.findByText(esAdmin.tecnologia.rechazar.hecho)).toBeTruthy();
    expect(repositorio.rechazos).toEqual([{ id: 'bt-1', motivo: 'No vendemos esta gama' }]);
  });

  it('un precio mal escrito se dice en su campo y no se manda', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.input(grupoDe(UNA_SIM).getByLabelText(esAdmin.tecnologia.detalle.precioVenta), {
      target: { value: '849,900' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.detalle.guardar }));

    expect(
      (await screen.findAllByText(esAdmin.tecnologia.detalle.precioIlegible)).length,
    ).toBeGreaterThan(0);
    expect(repositorio.elecciones).toHaveLength(0);
  });

  it('no se aprueba al costo', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.input(grupoDe(UNA_SIM).getByLabelText(esAdmin.tecnologia.detalle.precioVenta), {
      target: { value: '675000' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion }));

    expect(await screen.findByText(esAdmin.tecnologia.aprobar.precioBajoCosto)).toBeTruthy();
    expect(repositorio.aprobaciones).toHaveLength(0);
  });

  it('una configuración que llega con la página abierta aparece sin perder lo marcado', async () => {
    const { repositorio, fixture } = await renderPagina();
    fireEvent.click(grupoDe(UNA_SIM).getByLabelText('Gris'));

    const actual = borradorTecnologiaDePrueba();
    repositorio.reemplazar({
      ...actual,
      configuraciones: [
        ...actual.configuraciones,
        {
          ...actual.configuraciones[0],
          sku: 'a17-esim',
          titulo: 'Samsung Galaxy A17 5G 8GB RAM 256GB eSIM',
        },
      ],
    });
    await fixture.debugElement.injector.get(QueryClient).invalidateQueries();

    expect(
      await screen.findByRole('group', {
        name: 'Colores de Samsung Galaxy A17 5G 8GB RAM 256GB eSIM',
      }),
    ).toBeTruthy();
    expect((grupoDe(UNA_SIM).getByLabelText('Gris') as HTMLInputElement).checked).toBe(true);
  });

  it('muestra las fotos del borrador y cuál es la principal', async () => {
    await renderPagina();

    const fotos = screen.getAllByRole('img');
    expect(fotos).toHaveLength(4);
    expect(fotos[0].getAttribute('src')).toBe('https://firmada.local/foto-1.avif');
    expect(fotos[0].getAttribute('alt')).toBe('Samsung Galaxy A17 5G sobre fondo blanco 1');
    expect(screen.getByText(esAdmin.tecnologia.detalle.principal)).toBeTruthy();
    expect(screen.getByText('Samsung Galaxy A17 5G sobre fondo blanco 2')).toBeTruthy();
  });

  it('un modelo nuevo sin sus cuatro fotos no se aprueba, y no se envía', async () => {
    const { repositorio } = await renderPagina(
      borradorTecnologiaDePrueba({ cantidadDeFotos: 1, fotosQueFaltan: 3, fotos: [] }),
    );
    await screen.findByRole('option', { name: 'Samsung' });

    expect(screen.getByText(/todavía no tiene fotos/)).toBeTruthy();
    const aprobar = screen.getByRole('button', { name: esAdmin.tecnologia.aprobar.accion });
    const aviso = screen.getByText('Le faltan 3 fotos para aprobarse.');
    expect(aprobar.getAttribute('aria-describedby')).toContain(aviso.id);
    fireEvent.click(aprobar);

    expect(await screen.findByText(esAdmin.tecnologia.aprobar.revisaFotos)).toBeTruthy();
    expect(repositorio.aprobaciones).toEqual([]);
  });

  it('las configuraciones nuevas de un producto no muestran fotos ni las piden', async () => {
    await renderPagina(
      borradorTecnologiaDePrueba({
        productoId: 'p-1',
        cantidadDeFotos: 0,
        fotosQueFaltan: 0,
        fotos: [],
      }),
    );

    expect(screen.queryByRole('heading', { name: esAdmin.tecnologia.detalle.fotos })).toBeNull();
    expect(screen.queryByText(/fotos para aprobarse/)).toBeNull();
  });

  describe('fotos editables (23/10/2026)', () => {
    const F = esAdmin.tecnologia.fotos;

    function ordenDeFotos(): string[] {
      return screen
        .getAllByRole('img')
        .map((img) => img.getAttribute('src') ?? '')
        .filter((src) => src.startsWith('https://firmada.local/'))
        .map((src) => src.replace('https://firmada.local/', '').replace(/\.\w+$/, ''));
    }

    it('usar una foto como principal la pone primera y lo dice', async () => {
      const { repositorio } = await renderPagina();

      fireEvent.click(
        screen.getByRole('button', { name: F.usarComoPrincipalFoto.replace('{{numero}}', '3') }),
      );

      expect(await screen.findByText(F.principalCambiada)).toBeTruthy();
      expect(repositorio.ordenes).toEqual([
        { id: 'bt-1', fotoIds: ['foto-3', 'foto-1', 'foto-2', 'foto-4'] },
      ]);
      await waitFor(() => expect(ordenDeFotos()).toEqual(['foto-3', 'foto-1', 'foto-2', 'foto-4']));
    });

    it('bajar la primera la cambia con la segunda, y en los extremos no hay botón de más', async () => {
      const { repositorio } = await renderPagina();

      expect(
        screen.queryByRole('button', { name: F.subirFoto.replace('{{numero}}', '1') }),
      ).toBeNull();
      expect(
        screen.queryByRole('button', { name: F.bajarFoto.replace('{{numero}}', '4') }),
      ).toBeNull();
      fireEvent.click(screen.getByRole('button', { name: F.bajarFoto.replace('{{numero}}', '1') }));

      expect(await screen.findByText(F.ordenGuardado)).toBeTruthy();
      expect(repositorio.ordenes[0].fotoIds).toEqual(['foto-2', 'foto-1', 'foto-3', 'foto-4']);
    });

    it('quitar pide confirmación y después quita', async () => {
      const { repositorio } = await renderPagina();

      fireEvent.click(
        screen.getByRole('button', { name: F.quitarFoto.replace('{{numero}}', '2') }),
      );
      expect(repositorio.quitadas).toEqual([]);
      fireEvent.click(screen.getByRole('button', { name: F.confirmarQuitarAccion }));

      expect(await screen.findByText(F.quitada)).toBeTruthy();
      expect(repositorio.quitadas).toEqual([{ id: 'bt-1', fotoId: 'foto-2' }]);
      await waitFor(() => expect(ordenDeFotos()).toEqual(['foto-1', 'foto-3', 'foto-4']));
    });

    it('subir sin archivo dice qué falta y no envía nada', async () => {
      const { repositorio } = await renderPagina();

      fireEvent.click(screen.getByRole('button', { name: F.agregar }));

      expect(await screen.findByText(F.faltanCampos)).toBeTruthy();
      expect(repositorio.subidas).toEqual([]);
    });

    it('subir una foto la agrega al final con su texto alternativo', async () => {
      // jsdom no decodifica imágenes ni crea URL de objetos: se simulan las dos cosas.
      vi.stubGlobal(
        'Image',
        class {
          onload: (() => void) | null = null;
          naturalWidth = 1600;
          naturalHeight = 1600;
          set src(_: string) {
            setTimeout(() => this.onload?.());
          }
        },
      );
      const crear = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:local/1');
      const revocar = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
      try {
        const { repositorio } = await renderPagina();
        const archivo = new File(['x'], 'frente.jpg', { type: 'image/jpeg' });

        fireEvent.change(screen.getByLabelText(F.archivo), { target: { files: [archivo] } });
        await screen.findByAltText(F.previsualizacion);
        fireEvent.click(screen.getByRole('button', { name: F.agregar }));

        expect(await screen.findByText(F.agregada)).toBeTruthy();
        expect(repositorio.subidas).toEqual([
          {
            id: 'bt-1',
            foto: {
              archivo,
              ancho: 1600,
              alto: 1600,
              altEs: 'Samsung Galaxy A17 5G sobre fondo blanco',
              altEn: 'Samsung Galaxy A17 5G on a white background',
            },
          },
        ]);
        await waitFor(() => expect(ordenDeFotos().at(-1)).toBe('subida-1'));
      } finally {
        vi.unstubAllGlobals();
        crear.mockRestore();
        revocar.mockRestore();
      }
    });

    it('un borrador rechazado muestra las fotos sin acciones', async () => {
      await renderPagina(
        borradorTecnologiaDePrueba({ estado: 'RECHAZADO', motivoRechazo: 'No vendemos esta gama' }),
      );

      expect(
        screen.queryByRole('button', { name: F.quitarFoto.replace('{{numero}}', '1') }),
      ).toBeNull();
      expect(screen.queryByRole('button', { name: F.agregar })).toBeNull();
    });
  });

  describe('ficha editable (23/10/2026)', () => {
    const FI = esAdmin.tecnologia.ficha;

    function abrirEditor(): HTMLTextAreaElement {
      fireEvent.click(screen.getByRole('button', { name: FI.editar }));
      return screen.getByLabelText(new RegExp(FI.campo)) as HTMLTextAreaElement;
    }

    it('editar arranca con la ficha vigente y guardar publica la editada', async () => {
      const { repositorio } = await renderPagina();

      const area = abrirEditor();
      expect(area.value).toBe('El Galaxy A17 5G.');
      fireEvent.input(area, { target: { value: 'El Galaxy A17 5G, sin la nota.' } });
      fireEvent.click(screen.getByRole('button', { name: FI.guardar }));

      expect(await screen.findByText(FI.guardada)).toBeTruthy();
      expect(repositorio.fichas).toEqual([
        { id: 'bt-1', descripcion: 'El Galaxy A17 5G, sin la nota.' },
      ]);
      expect(await screen.findByText('El Galaxy A17 5G, sin la nota.')).toBeTruthy();
      expect(screen.getByText(FI.editada)).toBeTruthy();
    });

    it('guardar el mismo texto de la skill vuelve a ella en vez de guardar una copia', async () => {
      const { repositorio } = await renderPagina(
        borradorTecnologiaDePrueba({ descripcion: 'Editada.', fichaEditada: true }),
      );

      abrirEditor();
      fireEvent.click(screen.getByRole('button', { name: FI.volver }));
      fireEvent.click(screen.getByRole('button', { name: FI.guardar }));

      expect(await screen.findByText(FI.vueltaALaSkill)).toBeTruthy();
      expect(repositorio.fichas).toEqual([{ id: 'bt-1', descripcion: null }]);
    });

    it('una ficha vacía no se envía', async () => {
      const { repositorio } = await renderPagina();

      fireEvent.input(abrirEditor(), { target: { value: '   ' } });
      fireEvent.click(screen.getByRole('button', { name: FI.guardar }));

      expect(await screen.findByText(FI.vacia)).toBeTruthy();
      expect(repositorio.fichas).toEqual([]);
    });

    it('cancelar deja la ficha como estaba', async () => {
      const { repositorio } = await renderPagina();

      fireEvent.input(abrirEditor(), { target: { value: 'Otra cosa.' } });
      fireEvent.click(screen.getByRole('button', { name: FI.cancelar }));

      expect(screen.queryByLabelText(new RegExp(FI.campo))).toBeNull();
      expect(screen.getByText('El Galaxy A17 5G.')).toBeTruthy();
      expect(repositorio.fichas).toEqual([]);
    });

    it('avisa que una lista trajo otra ficha de la skill', async () => {
      await renderPagina(
        borradorTecnologiaDePrueba({
          descripcion: 'Editada.',
          fichaEditada: true,
          fichaNuevaDeLaSkill: true,
        }),
      );

      expect(screen.getByText(FI.nuevaDeLaSkill)).toBeTruthy();
    });

    it('un borrador aprobado no ofrece editar la ficha', async () => {
      await renderPagina(borradorTecnologiaDePrueba({ estado: 'APROBADO', productoId: 'prod-1' }));

      expect(screen.queryByRole('button', { name: FI.editar })).toBeNull();
    });
  });

  describe('eliminar el borrador (24/10/2026)', () => {
    const EL = esAdmin.tecnologia.eliminar;

    it('pide confirmación, lo borra y vuelve a la bandeja diciendo cuál', async () => {
      const { repositorio, fixture } = await renderPagina();
      const router = fixture.debugElement.injector.get(Router);
      const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      fireEvent.click(screen.getByRole('button', { name: EL.accion }));
      expect(repositorio.eliminados).toEqual([]);
      fireEvent.click(screen.getByRole('button', { name: EL.confirmar }));

      await waitFor(() => expect(repositorio.eliminados).toEqual(['bt-1']));
      await waitFor(() =>
        expect(navegar).toHaveBeenCalledWith(['/', 'es', 'admin', 'tecnologia'], {
          state: { borradorTecnologiaEliminado: 'Samsung Galaxy A17 5G' },
        }),
      );
    });

    it('cancelar no borra nada', async () => {
      const { repositorio } = await renderPagina();

      fireEvent.click(screen.getByRole('button', { name: EL.accion }));
      fireEvent.click(screen.getByRole('button', { name: EL.cancelar }));

      expect(screen.queryByRole('button', { name: EL.confirmar })).toBeNull();
      expect(repositorio.eliminados).toEqual([]);
    });

    it('un aprobado no ofrece eliminarse', async () => {
      await renderPagina(borradorTecnologiaDePrueba({ estado: 'APROBADO', productoId: 'prod-1' }));

      expect(screen.queryByRole('button', { name: EL.accion })).toBeNull();
    });
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina();

    await esperarSinViolaciones(container);
  });
});
