import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import {
  REPOSITORIO_REVISION_ENVIOS,
  RepositorioRevisionEnvios,
} from '../../domain/repositorio-revision-envios.puerto';
import {
  AcuseDeRevision,
  BandejaDeRevision,
  EmisionEnRevision,
  EmisionResuelta,
  GuiaEnRevision,
  VeredictoDeEmision,
} from '../../domain/revision-envio.model';
import { BandejaRevisionPage } from './bandeja-revision.page';

function guia(overrides: Partial<GuiaEnRevision> = {}): GuiaEnRevision {
  return {
    guiaId: 'g1',
    numeroGuia: '034054505967',
    transportadora: 'Servientrega',
    pedidoId: 'p1',
    numeroPedido: 'TS-2026-000042',
    estado: 'RETENIDO',
    descripcion: 'retenido en bodega',
    ocurrioEn: '2026-09-15T14:00:00Z',
    recibidoEn: '2026-09-15T14:30:00Z',
    revisadaEn: null,
    ...overrides,
  };
}

function emision(overrides: Partial<EmisionEnRevision> = {}): EmisionEnRevision {
  return {
    emisionId: 'e1',
    pedidoId: 'p2',
    numeroPedido: 'TS-2026-000043',
    transportadora: 'Coordinadora',
    idTarifa: 'b9b9b9b9-0000-4000-8000-000000000001',
    estado: 'INDETERMINADA',
    detalle: 'la llamada no terminó',
    enviosEnPlataforma: [],
    solicitadaEn: '2026-09-16T10:00:00Z',
    actor: 'admin:7',
    ...overrides,
  };
}

class RepositorioRevisionFalso implements RepositorioRevisionEnvios {
  guiasAcusadas: { numeroGuia: string; nota: string | null }[] = [];
  emisionesAcusadas: { emisionId: string; nota: string | null }[] = [];
  resueltas: {
    emisionId: string;
    veredicto: VeredictoDeEmision;
    enviosEnPlataforma: readonly string[];
    nota: string | null;
  }[] = [];

  constructor(private readonly bandeja: BandejaDeRevision = { guias: [], emisiones: [] }) {}

  async listar(): Promise<BandejaDeRevision> {
    return this.bandeja;
  }

  async acusarGuia(numeroGuia: string, nota: string | null): Promise<AcuseDeRevision> {
    this.guiasAcusadas.push({ numeroGuia, nota });
    return acuse('GUIA', numeroGuia, nota);
  }

  async acusarEmision(emisionId: string, nota: string | null): Promise<AcuseDeRevision> {
    this.emisionesAcusadas.push({ emisionId, nota });
    return acuse('EMISION', emisionId, nota);
  }

  async resolverEmision(entrada: {
    emisionId: string;
    veredicto: VeredictoDeEmision;
    enviosEnPlataforma: readonly string[];
    nota: string | null;
  }): Promise<EmisionResuelta> {
    this.resueltas.push(entrada);
    return {
      emisionId: entrada.emisionId,
      estado: entrada.veredicto === 'SIN_COBRO' ? 'FALLIDA' : 'EN_CURSO',
      detalle: null,
      enviosEnPlataforma: entrada.enviosEnPlataforma,
      resueltaEn: '2026-09-17T16:00:00Z',
    };
  }
}

function acuse(tipo: 'GUIA' | 'EMISION', referencia: string, nota: string | null): AcuseDeRevision {
  return {
    id: 'a1',
    tipo,
    referencia,
    revisadoEn: '2026-09-17T15:00:00Z',
    actor: 'admin:7',
    nota,
  };
}

async function renderBandeja(bandeja: Partial<BandejaDeRevision> = {}) {
  const repositorio = new RepositorioRevisionFalso({
    guias: bandeja.guias ?? [],
    emisiones: bandeja.emisiones ?? [],
  });
  const resultado = await render(BandejaRevisionPage, {
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
      { provide: REPOSITORIO_REVISION_ENVIOS, useValue: repositorio },
    ],
  });
  return { ...resultado, repositorio };
}

describe('BandejaRevisionPage', () => {
  it('muestra la guia detenida con el pedido al que pertenece', async () => {
    await renderBandeja({ guias: [guia()] });

    expect(await screen.findByText(/034054505967/)).toBeTruthy();
    expect(screen.getByText(/TS-2026-000042/)).toBeTruthy();
  });

  /**
   * Una bandeja vacia tiene que decirlo. Una pantalla en blanco no distingue "no hay nada que
   * revisar" de "no cargo", y son cosas opuestas.
   */
  it('sin nada quieto lo dice, en vez de dejar la pantalla en blanco', async () => {
    await renderBandeja();

    expect(await screen.findByText(esAdmin.revision_envios.vacia)).toBeTruthy();
  });

  /**
   * Las dos fechas viajan juntas a proposito: la de la transportadora y la de cuando nos enteramos.
   * La segunda es contra la que el servidor compara el acuse, y sin verla no se distingue un evento
   * de hace dos meses que nadie atendio de uno que acaba de llegar.
   */
  it('muestra cuando nos enteramos, no solo cuando la transportadora dice que paso', async () => {
    await renderBandeja({ guias: [guia()] });

    expect(await screen.findByText(/Nos enteramos el/)).toBeTruthy();
  });

  /** Una guia con acuse que sigue en la lista es una a la que le llego algo despues. */
  it('avisa cuando la guia ya se habia revisado y volvio a moverse', async () => {
    await renderBandeja({ guias: [guia({ revisadaEn: '2026-09-16T09:00:00Z' })] });

    expect(await screen.findByText(/Ya se había revisado el/)).toBeTruthy();
  });

  it('acusar una guia manda el numero y la nota escrita', async () => {
    const { repositorio } = await renderBandeja({ guias: [guia()] });

    fireEvent.click(await screen.findByRole('button', { name: 'Marcar como revisada' }));
    fireEvent.input(screen.getByLabelText('Qué encontraste'), {
      target: { value: 'Reclamé a la transportadora.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar la revisión' }));

    await vi.waitFor(() =>
      expect(repositorio.guiasAcusadas).toEqual([
        { numeroGuia: '034054505967', nota: 'Reclamé a la transportadora.' },
      ]),
    );
  });

  /** La nota es opcional: obligarla llenaria la base de "ok". */
  it('acusar sin nota manda nulo y no una cadena vacia', async () => {
    const { repositorio } = await renderBandeja({ guias: [guia()] });

    fireEvent.click(await screen.findByRole('button', { name: 'Marcar como revisada' }));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar la revisión' }));

    await vi.waitFor(() =>
      expect(repositorio.guiasAcusadas).toEqual([{ numeroGuia: '034054505967', nota: null }]),
    );
  });

  /**
   * El identificador de tarifa es lo unico con lo que se puede hacer algo con una emision
   * indeterminada: es lo que la encuentra en el panel de la plataforma.
   */
  it('la emision indeterminada muestra su identificador de tarifa', async () => {
    await renderBandeja({ emisiones: [emision()] });

    expect(await screen.findByText('b9b9b9b9-0000-4000-8000-000000000001')).toBeTruthy();
  });

  /**
   * Que acusar no resuelve nada tiene que estar escrito en la pantalla, no solo en el codigo: quien
   * la marca se puede ir creyendo que desbloqueo el pedido, y no lo hizo.
   */
  it('dice que marcar la emision no la resuelve', async () => {
    await renderBandeja({ emisiones: [emision()] });

    fireEvent.click(await screen.findByRole('button', { name: 'Marcar como revisada' }));

    expect(screen.getByText(esAdmin.revision_envios.emisiones.no_resuelve)).toBeTruthy();
  });

  it('acusar una emision manda su identificador', async () => {
    const { repositorio } = await renderBandeja({ emisiones: [emision()] });

    fireEvent.click(await screen.findByRole('button', { name: 'Marcar como revisada' }));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar la revisión' }));

    await vi.waitFor(() =>
      expect(repositorio.emisionesAcusadas).toEqual([{ emisionId: 'e1', nota: null }]),
    );
  });

  /**
   * El camino que desbloquea el pedido. Lo que se comprueba es que la pantalla mande el veredicto
   * que la persona eligio, porque de ese veredicto depende si el pedido queda libre o si seguimos
   * un envio ya pagado.
   */
  it('resolver sin cobro manda ese veredicto', async () => {
    const { repositorio } = await renderBandeja({ emisiones: [emision()] });

    fireEvent.click(await screen.findByRole('button', { name: 'Resolver' }));
    fireEvent.click(screen.getByRole('button', { name: 'No aparece: no hubo cobro' }));

    await vi.waitFor(() =>
      expect(repositorio.resueltas).toEqual([
        {
          emisionId: 'e1',
          veredicto: 'SIN_COBRO',
          enviosEnPlataforma: [],
          nota: null,
        },
      ]),
    );
  });

  /** Los identificadores se pegan como vengan del panel: separados por coma o por espacio. */
  it('resolver con envio manda los identificadores que se pegaron', async () => {
    const { repositorio } = await renderBandeja({ emisiones: [emision()] });

    fireEvent.click(await screen.findByRole('button', { name: 'Resolver' }));
    fireEvent.input(screen.getByLabelText('Identificadores del envío'), {
      target: { value: 'env-1, env-2' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Sí aparece: seguir este envío' }));

    await vi.waitFor(() =>
      expect(repositorio.resueltas[0].enviosEnPlataforma).toEqual(['env-1', 'env-2']),
    );
  });

  /**
   * Decir que el envio esta sin decir cual no resuelve nada, y la pantalla lo dice antes de ir al
   * servidor: es el unico camino en el que un error del operador cuesta una llamada inutil.
   */
  it('resolver con envio sin identificadores avisa y no llama al servidor', async () => {
    const { repositorio } = await renderBandeja({ emisiones: [emision()] });

    fireEvent.click(await screen.findByRole('button', { name: 'Resolver' }));
    fireEvent.click(screen.getByRole('button', { name: 'Sí aparece: seguir este envío' }));

    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(repositorio.resueltas).toEqual([]);
  });

  /** Una parcial no se resuelve por esta via: solo una indeterminada se cerro sin saber. */
  it('una emision parcial no ofrece resolver', async () => {
    await renderBandeja({ emisiones: [emision({ estado: 'PARCIAL' })] });

    await screen.findByRole('button', { name: 'Marcar como revisada' });
    expect(screen.queryByRole('button', { name: 'Resolver' })).toBeNull();
  });

  it('no tiene violaciones de accesibilidad con las dos listas llenas', async () => {
    const { container } = await renderBandeja({ guias: [guia()], emisiones: [emision()] });
    await screen.findByText(/034054505967/);

    await esperarSinViolaciones(container);
  });
});
