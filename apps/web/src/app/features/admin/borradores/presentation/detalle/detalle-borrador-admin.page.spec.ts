import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import { Categoria, ColorDePaleta, Marca } from '../../../../catalogo/domain/producto.model';
import {
  REPOSITORIO_PALETA_COLORES,
  RepositorioPaletaColores,
} from '../../../../catalogo/domain/repositorio-paleta-colores.puerto';
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
import { Borrador, FotoBorrador } from '../../domain/borrador.model';
import { REPOSITORIO_BORRADORES_ADMIN } from '../../domain/repositorio-borradores-admin.puerto';
import {
  borradorDePrueba,
  fotoDePrueba,
  RepositorioBorradoresAdminFalso,
} from '../apoyo-borradores.spec-util';
import { DetalleBorradorAdminPage } from './detalle-borrador-admin.page';

const MARCA: Marca = { id: 'm1', nombre: 'Genérica' };
const CATEGORIA: Categoria = {
  id: 'c1',
  nombre: 'Bolsos de mano',
  slug: 'bolsos-de-mano',
  linea: 'BOLSOS',
  padreId: null,
  hashtags: [],
  escalaTallas: [],
};

class RepositorioMarcasFalso implements RepositorioMarcas {
  async listarTodas(): Promise<Marca[]> {
    return [MARCA];
  }
}

const BLUSAS: Categoria = {
  id: 'c2',
  nombre: 'Blusas',
  slug: 'ropa-dama-blusas',
  linea: 'ROPA',
  padreId: null,
  hashtags: [],
  escalaTallas: [],
};

const DAMA: Categoria = {
  id: 'c3',
  nombre: 'Dama',
  slug: 'ropa-dama',
  linea: 'ROPA',
  padreId: null,
  hashtags: [],
  escalaTallas: ['XS', 'S', 'M', 'L'],
};

const CAMISAS: Categoria = {
  id: 'c4',
  nombre: 'Camisas',
  slug: 'ropa-dama-camisas',
  linea: 'ROPA',
  padreId: 'c3',
  hashtags: [],
  escalaTallas: [],
};

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [CATEGORIA, BLUSAS, DAMA, CAMISAS];
  }
}

class RepositorioPaletaFalso implements RepositorioPaletaColores {
  async listarTodos(): Promise<ColorDePaleta[]> {
    return [
      // Fuera de orden a propósito: la pantalla los ordena alfabéticamente.
      { nombre: 'Vino', nombreEn: 'Burgundy', hex: '#722F37' },
      { nombre: 'Negro', nombreEn: 'Black', hex: '#111111' },
      { nombre: 'Café', nombreEn: 'Brown', hex: '#6F4E37' },
    ];
  }
}

function rutaCon(id: string) {
  const paramMap = convertToParamMap({ id });
  return { paramMap: of(paramMap), snapshot: { paramMap } };
}

async function renderPagina(
  borrador: Borrador = borradorDePrueba(),
  fotos: FotoBorrador[] = [fotoDePrueba('f-1'), fotoDePrueba('f-2')],
  textos: string[] = ['Bolso tote 53.000 sirve hasta L', 'Disponible en negro y café'],
) {
  const repositorio = new RepositorioBorradoresAdminFalso([borrador], fotos, textos);
  const resultado = await render(DetalleBorradorAdminPage, {
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
      { provide: REPOSITORIO_BORRADORES_ADMIN, useValue: repositorio },
      {
        provide: REPOSITORIO_PROVEEDORES_ADMIN,
        useValue: new RepositorioProveedoresAdminFalso([proveedorDePrueba()]),
      },
      { provide: REPOSITORIO_MARCAS, useValue: new RepositorioMarcasFalso() },
      { provide: REPOSITORIO_CATEGORIAS, useValue: new RepositorioCategoriasFalso() },
      { provide: REPOSITORIO_PALETA_COLORES, useValue: new RepositorioPaletaFalso() },
      { provide: ActivatedRoute, useValue: rutaCon(borrador.id) },
    ],
  });
  return { ...resultado, repositorio };
}

const a = esAdmin.borradores.aprobar;
const d = esAdmin.borradores.datos;

async function llenarAprobacion() {
  await screen.findByRole('option', { name: 'Genérica' });
  fireEvent.change(screen.getByLabelText(a.marca), { target: { value: 'm1' } });
  fireEvent.change(screen.getByLabelText(a.categoria), { target: { value: 'c1' } });
  fireEvent.input(screen.getByLabelText(a.existenciaInicial), { target: { value: '2' } });
  fireEvent.input(screen.getByLabelText(a.altEn), { target: { value: 'Medium tote bag' } });
}

describe('DetalleBorradorAdminPage', () => {
  it('muestra lo que escribió el proveedor, las fotos y las alertas', async () => {
    await renderPagina(borradorDePrueba({ alertas: ['CONFIANZA_BAJA'] }));

    expect(await screen.findByText('Bolso tote 53.000 sirve hasta L')).toBeTruthy();
    expect(screen.getAllByRole('img')).toHaveLength(2);
    expect(screen.getByText(esAdmin.borradores.alertas.CONFIANZA_BAJA)).toBeTruthy();
    expect(screen.getByText(/precio del proveedor: \$ 53\.000/)).toBeTruthy();
  });

  it('prellena los datos extraídos y el precio de venta con el sugerido', async () => {
    await renderPagina();

    const titulo = (await screen.findByLabelText(d.tituloProducto)) as HTMLInputElement;
    await vi.waitFor(() => expect(titulo.value).toBe('Bolso tote en cuero sintético'));
    expect((screen.getByLabelText(d.sirveHasta) as HTMLInputElement).value).toBe('L');
    expect((screen.getByLabelText(d.tonosNombrados) as HTMLInputElement).value).toBe('Negro, Café');
    expect((screen.getByLabelText(a.precioVenta) as HTMLInputElement).value).toBe('72000');
    expect((screen.getByLabelText(a.altEs) as HTMLInputElement).value).toBe(
      'Bolso tote en cuero sintético',
    );
    expect((screen.getByLabelText(a.altEn) as HTMLInputElement).value).toBe('');
  });

  /**
   * La tarjeta de datos dice "la aprobación de abajo parte de estos datos": el título corregido
   * viaja con la aprobación aunque nadie haya pulsado "Guardar datos".
   */
  it('aprobar lleva el título y las tallas tal como están escritos, sin guardar antes', async () => {
    const { repositorio } = await renderPagina();
    await llenarAprobacion();
    const titulo = screen.getByLabelText(d.tituloProducto) as HTMLInputElement;
    await vi.waitFor(() => expect(titulo.value).toBe('Bolso tote en cuero sintético'));

    fireEvent.input(titulo, { target: { value: 'Bolso tote negro' } });
    fireEvent.input(screen.getByLabelText(d.sirveHasta), { target: { value: 'XL' } });
    fireEvent.click(screen.getByRole('button', { name: a.accion }));

    await screen.findByRole('link', { name: a.verProducto });
    expect(repositorio.aprobaciones[0].aprobacion).toMatchObject({
      titulo: 'Bolso tote negro',
      tallas: { tipo: 'UNICA', sirveHasta: 'XL', valores: [] },
    });
  });

  /**
   * El criterio de aceptación de la aprobación: crea el producto con el tono elegido por foto,
   * las unidades iniciales y el precio de venta definitivo. Sin marca ni categoría no se manda.
   */
  it('sin marca ni categoría dice qué falta y no aprueba', async () => {
    const { repositorio } = await renderPagina();

    fireEvent.click(await screen.findByRole('button', { name: a.accion }));

    expect(await screen.findByText(a.faltanCampos)).toBeTruthy();
    expect(repositorio.aprobaciones).toEqual([]);
  });

  it('ofrece los colores de la paleta en orden alfabético', async () => {
    await renderPagina();

    fireEvent.click(await screen.findByRole('button', { name: /Color de la foto 1/ }));
    const casillas = await screen.findAllByRole('checkbox', { name: /^(Café|Negro|Vino)$/ });
    expect(casillas.map((c) => (c as HTMLInputElement).labels?.[0]?.textContent?.trim())).toEqual([
      'Café',
      'Negro',
      'Vino',
    ]);
  });

  it('aprueba con el tono de cada foto, las unidades y el precio, y enlaza el producto', async () => {
    const { repositorio } = await renderPagina();
    await llenarAprobacion();

    // La foto 1 es de dos colores, marcados en orden: negro y luego vino.
    fireEvent.click(await screen.findByRole('button', { name: /Color de la foto 1/ }));
    fireEvent.click(await screen.findByLabelText('Negro'));
    fireEvent.click(screen.getByLabelText('Vino'));
    fireEvent.input(screen.getByLabelText(a.precioVenta), { target: { value: '75.000' } });
    fireEvent.click(screen.getByRole('button', { name: a.accion }));

    expect(await screen.findByRole('link', { name: a.verProducto })).toBeTruthy();
    expect(repositorio.aprobaciones).toHaveLength(1);
    expect(repositorio.aprobaciones[0].aprobacion).toMatchObject({
      marcaId: 'm1',
      categoriaId: 'c1',
      precioVenta: 75000,
      existenciaInicial: 2,
      altEs: 'Bolso tote en cuero sintético',
      fotos: [
        { mensajeId: 'f-1', tono: 'Negro / Vino', colorHex: '#111111' },
        { mensajeId: 'f-2', tono: null, colorHex: null },
      ],
    });
  });

  /**
   * Una publicación de ropa trae doce o catorce fotos y el catálogo admite nueve: por omisión
   * entran las nueve primeras, y quien aprueba cambia cuáles. La primera elegida es la principal.
   */
  it('con más fotos de las que caben entran las nueve primeras y se puede cambiar cuáles', async () => {
    const fotos = Array.from({ length: 11 }, (_, i) => fotoDePrueba('f-' + (i + 1)));
    const { repositorio } = await renderPagina(borradorDePrueba(), fotos);
    await llenarAprobacion();

    expect(await screen.findByText(/9 de 9 fotos elegidas/)).toBeTruthy();
    expect((screen.getByLabelText('Incluir la foto 11') as HTMLInputElement).checked).toBe(false);

    fireEvent.click(screen.getByLabelText('Foto principal (la 1)'));
    fireEvent.click(screen.getByLabelText('Incluir la foto 11'));
    expect(await screen.findByText('Foto principal (la 2)')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: a.accion }));

    await screen.findByRole('link', { name: a.verProducto });
    expect(repositorio.aprobaciones[0].aprobacion.fotos.map((f) => f.mensajeId)).toEqual([
      'f-2',
      'f-3',
      'f-4',
      'f-5',
      'f-6',
      'f-7',
      'f-8',
      'f-9',
      'f-11',
    ]);
  });

  /** Una blusa siempre cae en Dama › Blusas: la categoría llega puesta y se puede cambiar. */
  it('una blusa llega con su categoría preseleccionada', async () => {
    await renderPagina(borradorDePrueba({ tipo: 'BLUSA' }));
    await screen.findByRole('option', { name: /Blusas/ });

    await vi.waitFor(() =>
      expect((screen.getByLabelText(a.categoria) as HTMLSelectElement).value).toBe('c2'),
    );
  });

  /** Un bolso depende de para quién es: la categoría la elige quien aprueba. */
  it('un tipo sin categoría fija no preselecciona ninguna', async () => {
    await renderPagina(borradorDePrueba({ tipo: 'BOLSO' }));
    await screen.findByRole('option', { name: /Blusas/ });

    expect((screen.getByLabelText(a.categoria) as HTMLSelectElement).value).toBe('');
  });

  it('marcar otra foto como principal la manda primero', async () => {
    const { repositorio } = await renderPagina();
    await llenarAprobacion();

    fireEvent.click(screen.getByRole('button', { name: 'Marcar la foto 2 como principal' }));
    expect(await screen.findByLabelText('Foto principal (la 2)')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: a.accion }));

    await screen.findByRole('link', { name: a.verProducto });
    expect(repositorio.aprobaciones[0].aprobacion.fotos.map((f) => f.mensajeId)).toEqual([
      'f-2',
      'f-1',
    ]);
  });

  /**
   * Un mensaje con varios productos y las mismas fotos: no hay principal hasta que alguien la
   * marque, porque de ella sale la huella con que se reconoce este producto y no el otro.
   */
  it('con fotos compartidas no aprueba sin marcar la principal', async () => {
    const { repositorio } = await renderPagina(
      borradorDePrueba({ alertas: ['FOTOS_COMPARTIDAS'] }),
    );
    await llenarAprobacion();

    expect(await screen.findByText(a.fotosCompartidas)).toBeTruthy();
    expect(screen.queryByLabelText('Foto principal (la 1)')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: a.accion }));

    expect(await screen.findByText(a.marcaLaPrincipal)).toBeTruthy();
    expect(repositorio.aprobaciones).toEqual([]);

    fireEvent.click(screen.getByRole('button', { name: 'Marcar la foto 2 como principal' }));
    fireEvent.click(screen.getByRole('button', { name: a.accion }));

    await screen.findByRole('link', { name: a.verProducto });
    expect(repositorio.aprobaciones[0].aprobacion.fotos.map((f) => f.mensajeId)).toEqual([
      'f-2',
      'f-1',
    ]);
  });

  it('sin ninguna foto elegida no aprueba y lo dice', async () => {
    const { repositorio } = await renderPagina();
    await llenarAprobacion();

    fireEvent.click(screen.getByLabelText('Foto principal (la 1)'));
    fireEvent.click(screen.getByLabelText('Foto principal (la 2)'));
    fireEvent.click(screen.getByRole('button', { name: a.accion }));

    expect(await screen.findByText(a.sinFotosElegidas)).toBeTruthy();
    expect(repositorio.aprobaciones).toEqual([]);
  });

  it('guarda las correcciones de los datos extraídos', async () => {
    const { repositorio } = await renderPagina();
    const titulo = (await screen.findByLabelText(d.tituloProducto)) as HTMLInputElement;
    await vi.waitFor(() => expect(titulo.value).toBe('Bolso tote en cuero sintético'));

    fireEvent.input(titulo, { target: { value: 'Bolso tote negro' } });
    fireEvent.input(screen.getByLabelText(d.descripcion), {
      target: { value: 'Bolso tote negro con cierre magnético y bolsillo interno.' },
    });
    fireEvent.click(screen.getByRole('button', { name: d.guardar }));

    expect(await screen.findByText(d.guardado)).toBeTruthy();
    expect(repositorio.ediciones[0].cambios).toMatchObject({
      titulo: 'Bolso tote negro',
      tallas: { tipo: 'UNICA', sirveHasta: 'L', valores: [] },
      tonosNombrados: ['Negro', 'Café'],
      descripcion: 'Bolso tote negro con cierre magnético y bolsillo interno.',
    });
  });

  /** La descripción es la de la ficha: obligatoria desde el 3 de octubre de 2026. */
  it('sin descripción no aprueba y dice dónde escribirla', async () => {
    const { repositorio } = await renderPagina(borradorDePrueba({ descripcion: null }));
    await llenarAprobacion();

    fireEvent.click(screen.getByRole('button', { name: a.accion }));

    // En el aviso de aprobar y en el propio campo, que es donde hay que escribirla.
    expect(await screen.findAllByText(a.faltaDescripcion)).toHaveLength(2);
    expect(repositorio.aprobaciones).toEqual([]);
  });

  it('la descripción de los datos extraídos viaja con la aprobación, como está escrita', async () => {
    const { repositorio } = await renderPagina();
    await llenarAprobacion();
    const descripcion = screen.getByLabelText(d.descripcion) as HTMLTextAreaElement;
    await vi.waitFor(() =>
      expect(descripcion.value).toBe('Bolso tote en cuero sintético con cierre magnético.'),
    );

    fireEvent.input(descripcion, { target: { value: '  Bolso tote con tira larga.  ' } });
    fireEvent.click(screen.getByRole('button', { name: a.accion }));

    await screen.findByRole('link', { name: a.verProducto });
    expect(repositorio.aprobaciones[0].aprobacion.descripcion).toBe('Bolso tote con tira larga.');
  });

  /** El título en inglés de la extracción, con el nombre comercial, es el alt en inglés. */
  it('el alt en inglés parte del título en inglés que propuso la extracción', async () => {
    await renderPagina(borradorDePrueba({ altEn: 'Faux leather tote bag' }));

    const altEn = (await screen.findByLabelText(a.altEn)) as HTMLInputElement;
    await vi.waitFor(() => expect(altEn.value).toBe('Faux leather tote bag'));
  });

  /** Una réplica se publica con la marca Genérica; la original solo va en el título. */
  it('una réplica llega con la marca Genérica y lo explica', async () => {
    await renderPagina(borradorDePrueba({ alertas: ['REPLICA'] }));
    await screen.findByRole('option', { name: 'Genérica' });

    await vi.waitFor(() =>
      expect((screen.getByLabelText(a.marca) as HTMLSelectElement).value).toBe('m1'),
    );
    expect(screen.getByText(esAdmin.borradores.alertas.REPLICA)).toBeTruthy();
  });

  it('sin la alerta de réplica no elige marca', async () => {
    await renderPagina();
    await screen.findByRole('option', { name: 'Genérica' });

    expect((screen.getByLabelText(a.marca) as HTMLSelectElement).value).toBe('');
  });

  it('eliminar una foto pregunta antes, la saca de la revisión y lo anuncia', async () => {
    const { repositorio } = await renderPagina();
    const e = esAdmin.borradores.eliminarFoto;

    fireEvent.click(await screen.findByRole('button', { name: 'Eliminar la foto 2' }));
    expect(screen.getByText('¿Eliminar la foto 2 de este borrador?')).toBeTruthy();
    expect(repositorio.fotosDescartadas).toEqual([]);

    fireEvent.click(screen.getByRole('button', { name: e.confirmar }));

    expect(await screen.findByText(e.hecho)).toBeTruthy();
    expect(repositorio.fotosDescartadas).toEqual([{ id: 'b-1', mensajeId: 'f-2' }]);
    await vi.waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Eliminar la foto 2' })).toBeNull(),
    );
  });

  /**
   * El caso que motivó la subida: la ingesta dejó el borrador sin fotos. La que se sube entra con
   * las mismas opciones que una del proveedor —incluida, principal, tono— y viaja al aprobar.
   */
  it('un borrador sin fotos deja subirlas y la subida se aprueba como las demás', async () => {
    const { repositorio } = await renderPagina(borradorDePrueba({ alertas: ['SIN_FOTOS'] }), []);
    const s = esAdmin.borradores.subirFotos;

    expect(await screen.findByText(esAdmin.borradores.detalle.sinFotosEditable)).toBeTruthy();
    const archivo = new File(['x'], 'bolso.jpg', { type: 'image/jpeg' });
    fireEvent.change(screen.getByLabelText(s.etiqueta), { target: { files: [archivo] } });

    expect(await screen.findByText('Fotos agregadas al borrador: 1.')).toBeTruthy();
    expect(repositorio.fotosSubidas).toEqual([{ id: 'b-1', archivo }]);
    expect(await screen.findByText(esAdmin.borradores.detalle.fotoSubida)).toBeTruthy();
    await vi.waitFor(() =>
      expect(screen.queryByText(esAdmin.borradores.alertas.SIN_FOTOS)).toBeNull(),
    );
    expect(screen.getByRole('button', { name: 'Eliminar la foto 1' })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Color de la foto 1/ })).toBeTruthy();

    await llenarAprobacion();
    fireEvent.click(screen.getByRole('button', { name: a.accion }));

    expect(await screen.findByRole('link', { name: a.verProducto })).toBeTruthy();
    expect(repositorio.aprobaciones[0].aprobacion.fotos).toEqual([
      { mensajeId: 'subida-1', tono: null, colorHex: null },
    ]);
  });

  /** La confirmación que llegue después encontraría el borrador aprobado, y la foto no entraría. */
  it('no aprueba mientras se suben fotos y dice que hay que esperar', async () => {
    const { repositorio } = await renderPagina();
    let terminar: () => void = () => undefined;
    const pendiente = new Promise<void>((resolver) => (terminar = resolver));
    const subirFoto = repositorio.subirFoto.bind(repositorio);
    repositorio.subirFoto = async (id, archivo) => {
      await pendiente;
      return subirFoto(id, archivo);
    };
    await llenarAprobacion();

    fireEvent.change(screen.getByLabelText(esAdmin.borradores.subirFotos.etiqueta), {
      target: { files: [new File(['x'], 'bolso.jpg', { type: 'image/jpeg' })] },
    });
    fireEvent.click(screen.getByRole('button', { name: a.accion }));

    expect(await screen.findByText(a.esperaSubida)).toBeTruthy();
    expect(repositorio.aprobaciones).toEqual([]);
    terminar();
    expect(await screen.findByText('Fotos agregadas al borrador: 1.')).toBeTruthy();
  });

  /**
   * El componente se reutiliza al navegar entre borradores: lo que faltaba por subir no puede caer
   * en el borrador nuevo.
   */
  it('si se navega a otro borrador a mitad de la subida, el resto no se sube', async () => {
    const { repositorio, fixture } = await renderPagina();
    let terminar: () => void = () => undefined;
    const pendiente = new Promise<void>((resolver) => (terminar = resolver));
    const subirFoto = repositorio.subirFoto.bind(repositorio);
    repositorio.subirFoto = async (id, archivo) => {
      await pendiente;
      return subirFoto(id, archivo);
    };

    fireEvent.change(await screen.findByLabelText(esAdmin.borradores.subirFotos.etiqueta), {
      target: {
        files: [
          new File(['x'], 'uno.jpg', { type: 'image/jpeg' }),
          new File(['x'], 'dos.jpg', { type: 'image/jpeg' }),
        ],
      },
    });
    const pagina = fixture.componentInstance as unknown as { id: () => string };
    const original = pagina.id;
    pagina.id = () => 'b-2';
    terminar();

    await vi.waitFor(() => expect(repositorio.fotosSubidas).toHaveLength(1));
    await new Promise((resolver) => setTimeout(resolver, 50));
    expect(repositorio.fotosSubidas.map((s) => [s.id, s.archivo.name])).toEqual([
      ['b-1', 'uno.jpg'],
    ]);
    pagina.id = original;
  });

  it('de varios archivos sube los que puede y dice cuál no entró y por qué', async () => {
    const { repositorio } = await renderPagina();
    const s = esAdmin.borradores.subirFotos;
    repositorio.fallosAlSubir.set(
      'roto.png',
      new ErrorHttp(422, 'no abre', 'IMAGEN_DE_PROVEEDOR_ILEGIBLE'),
    );
    repositorio.fallosAlSubir.set(
      'enorme.jpg',
      new ErrorHttp(413, 'pesa', 'FOTO_DEMASIADO_GRANDE'),
    );

    fireEvent.change(await screen.findByLabelText(s.etiqueta), {
      target: {
        files: [
          new File(['x'], 'foto.webp', { type: 'image/webp' }),
          new File(['x'], 'roto.png', { type: 'image/png' }),
          new File(['x'], 'enorme.jpg', { type: 'image/jpeg' }),
          new File(['x'], 'bien.jpg', { type: 'image/jpeg' }),
        ],
      },
    });

    expect(await screen.findByText('Fotos agregadas al borrador: 1.')).toBeTruthy();
    expect(screen.getByText('«foto.webp» no es JPEG ni PNG y no se subió.')).toBeTruthy();
    expect(screen.getByText('«roto.png» no se pudo abrir como imagen y no se subió.')).toBeTruthy();
    expect(
      screen.getByText(
        '«enorme.jpg» pesa más de lo que se admite y no se subió. Redúcela y vuelve a intentarlo.',
      ),
    ).toBeTruthy();
    expect(repositorio.fotosSubidas.map((subida) => subida.archivo.name)).toEqual(['bien.jpg']);
  });

  /** La del proveedor deja su archivo; la subida es solo de este borrador y se borra. */
  it('eliminar una foto subida avisa que su archivo se borra', async () => {
    await renderPagina(borradorDePrueba(), [
      fotoDePrueba('f-1'),
      { ...fotoDePrueba('s-1'), origen: 'PANEL' },
    ]);
    const e = esAdmin.borradores.eliminarFoto;

    fireEvent.click(await screen.findByRole('button', { name: 'Eliminar la foto 2' }));
    expect(screen.getByText(e.loQueImplicaSubida)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: e.cancelar }));

    fireEvent.click(screen.getByRole('button', { name: 'Eliminar la foto 1' }));
    expect(screen.getByText(e.loQueImplica)).toBeTruthy();
  });

  it('cancelar la eliminación de una foto no toca nada', async () => {
    const { repositorio } = await renderPagina();
    const e = esAdmin.borradores.eliminarFoto;

    fireEvent.click(await screen.findByRole('button', { name: 'Eliminar la foto 1' }));
    fireEvent.click(screen.getByRole('button', { name: e.cancelar }));

    expect(screen.queryByText('¿Eliminar la foto 1 de este borrador?')).toBeNull();
    expect(repositorio.fotosDescartadas).toEqual([]);
  });

  /** Camisas no tiene escala propia: la revisión ofrece la de Ropa › Dama para marcar. */
  it('con categoría, las tallas se marcan sobre su escala y avisa las que no están', async () => {
    const { repositorio } = await renderPagina(
      borradorDePrueba({ tallas: { tipo: 'LISTA', sirveHasta: null, valores: ['M', 'XXL'] } }),
    );
    await screen.findByRole('option', { name: /Camisas/ });
    fireEvent.change(screen.getByLabelText(a.categoria), { target: { value: 'c4' } });

    const s = (await screen.findByLabelText('S')) as HTMLInputElement;
    expect(s.checked).toBe(false);
    expect((screen.getByLabelText('M') as HTMLInputElement).checked).toBe(true);
    expect(screen.getByText(/Fuera de la escala de la categoría: XXL/)).toBeTruthy();

    fireEvent.click(s);
    fireEvent.click(screen.getByRole('button', { name: d.guardar }));

    expect(await screen.findByText(d.guardado)).toBeTruthy();
    expect(repositorio.ediciones[0].cambios.tallas).toEqual({
      tipo: 'LISTA',
      sirveHasta: null,
      valores: ['S', 'M', 'XXL'],
    });
  });

  it('rechazar exige un motivo y lo manda', async () => {
    const { repositorio } = await renderPagina();
    const r = esAdmin.borradores.rechazar;

    fireEvent.click(await screen.findByRole('button', { name: r.accion }));
    const aviso = await screen.findByText(r.faltaMotivo);
    // Dentro de la tarjeta de rechazar, no en la de aprobar: la vista tiene que relacionarlos.
    expect(aviso.closest('form')).toBe(screen.getByLabelText(r.motivo).closest('form'));

    fireEvent.input(screen.getByLabelText(r.motivo), { target: { value: 'Foto borrosa' } });
    fireEvent.click(screen.getByRole('button', { name: r.accion }));

    expect(await screen.findByText(r.hecho)).toBeTruthy();
    expect(repositorio.rechazos).toEqual([{ id: 'b-1', motivo: 'Foto borrosa' }]);
  });

  /** Un borrador decidido no ofrece ninguna de las tres acciones: el servidor las rechazaría. */
  it('un borrador ya aprobado se muestra sin formularios y con el enlace al producto', async () => {
    await renderPagina(borradorDePrueba({ estado: 'APROBADO', productoId: 'p-9' }));

    expect(
      await screen.findByRole('link', { name: esAdmin.borradores.detalle.verProducto }),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: a.accion })).toBeNull();
    expect(screen.queryByRole('button', { name: d.guardar })).toBeNull();
    expect(screen.queryByRole('button', { name: esAdmin.borradores.borrar.accion })).toBeNull();
    expect(screen.queryByLabelText(esAdmin.borradores.subirFotos.etiqueta)).toBeNull();
    await vi.waitFor(() =>
      expect((screen.getByLabelText(d.tituloProducto) as HTMLInputElement).disabled).toBe(true),
    );
  });

  it('borrar pregunta antes, borra y vuelve a la bandeja', async () => {
    const { repositorio, fixture } = await renderPagina();
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const b = esAdmin.borradores.borrar;

    const boton = await screen.findByRole('button', { name: b.accion });
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    fireEvent.click(boton);

    // La advertencia va en la descripción del grupo, para que se oiga antes de confirmar.
    const grupo = await screen.findByRole('group', { name: b.pregunta });
    expect(grupo.getAttribute('aria-describedby')).toBe('borrar-implica');
    expect(screen.getByText(b.loQueImplica)).toBeTruthy();
    expect(repositorio.eliminados).toEqual([]);

    fireEvent.click(screen.getByRole('button', { name: b.confirmar }));

    await vi.waitFor(() => expect(repositorio.eliminados).toEqual(['b-1']));
    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith(['/es', 'admin', 'borradores']));
  });

  it('cancelar cierra la pregunta sin borrar nada', async () => {
    const { repositorio } = await renderPagina();
    const b = esAdmin.borradores.borrar;

    fireEvent.click(await screen.findByRole('button', { name: b.accion }));
    fireEvent.click(await screen.findByRole('button', { name: b.cancelar }));

    expect(screen.queryByRole('group', { name: b.pregunta })).toBeNull();
    expect(repositorio.eliminados).toEqual([]);
  });

  it('si el servidor no deja borrar, lo dice con su código y no sale de la pantalla', async () => {
    const { repositorio, fixture } = await renderPagina();
    const navegar = vi.spyOn(fixture.debugElement.injector.get(Router), 'navigate');
    repositorio.falloAlEliminar = new ErrorHttp(409, 'no', 'BORRADOR_NO_ELIMINABLE');
    const b = esAdmin.borradores.borrar;

    fireEvent.click(await screen.findByRole('button', { name: b.accion }));
    fireEvent.click(await screen.findByRole('button', { name: b.confirmar }));

    expect(await screen.findByText(esAdmin.errores.borrador_no_eliminable)).toBeTruthy();
    expect(navegar).not.toHaveBeenCalled();
  });

  /** Su producto se borró: ya no es memoria de nada, y sin esto sus fotos no se podían liberar. */
  it('un aprobado cuyo producto se borró se puede borrar y dice por qué', async () => {
    await renderPagina(borradorDePrueba({ estado: 'APROBADO', productoId: null }));

    expect(
      await screen.findByRole('button', { name: esAdmin.borradores.borrar.accion }),
    ).toBeTruthy();
    expect(screen.getByText(esAdmin.borradores.borrar.explicacionSinProducto)).toBeTruthy();
    expect(screen.queryByText(esAdmin.borradores.borrar.explicacion)).toBeNull();
  });

  it('un rechazado también se puede borrar', async () => {
    await renderPagina(borradorDePrueba({ estado: 'RECHAZADO', motivoRechazo: 'Promoción' }));

    expect(
      await screen.findByRole('button', { name: esAdmin.borradores.borrar.accion }),
    ).toBeTruthy();
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina();

    await screen.findByRole('option', { name: 'Genérica' });
    await esperarSinViolaciones(container);
  });
});
