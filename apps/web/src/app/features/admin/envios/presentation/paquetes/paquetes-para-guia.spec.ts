import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import { PaquetesDePedido } from '../../domain/paquetes-de-pedido.model';
import {
  REPOSITORIO_PAQUETES_PEDIDO,
  RepositorioPaquetesPedido,
} from '../../domain/repositorio-paquetes-pedido.puerto';
import { PaquetesParaGuia } from './paquetes-para-guia';

const MIXTO: PaquetesDePedido = {
  conRecaudo: false,
  paquetes: [
    {
      pesoKg: 2,
      largoCm: 25,
      anchoCm: 20,
      altoCm: 15,
      valorDeclarado: { valor: 400_000, moneda: 'COP' },
      contenido: 'Electrónica y accesorios',
    },
    {
      pesoKg: 1,
      largoCm: 40,
      anchoCm: 30,
      altoCm: 10,
      valorDeclarado: { valor: 250_000, moneda: 'COP' },
      contenido: 'Calzado deportivo',
    },
  ],
};

class RepositorioPaquetesFalso implements RepositorioPaquetesPedido {
  pedidos: string[] = [];

  constructor(private readonly respuesta: PaquetesDePedido | Error) {}

  async consultar(pedidoId: string): Promise<PaquetesDePedido> {
    this.pedidos.push(pedidoId);
    if (this.respuesta instanceof Error) {
      throw this.respuesta;
    }
    return this.respuesta;
  }
}

async function renderPaquetes(respuesta: PaquetesDePedido | Error) {
  const repositorio = new RepositorioPaquetesFalso(respuesta);
  await render(PaquetesParaGuia, {
    inputs: { pedidoId: 'p-1' },
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_PAQUETES_PEDIDO, useValue: repositorio },
    ],
  });
  return repositorio;
}

describe('PaquetesParaGuia', () => {
  /** Un pedido mixto son dos paquetes: la caja medida y la bolsa de referencia. */
  it('muestra cada paquete con sus medidas, su peso en kilos y su contenido', async () => {
    const repositorio = await renderPaquetes(MIXTO);

    expect(await screen.findByText('25 × 20 × 15 cm')).toBeTruthy();
    expect(screen.getByText('40 × 30 × 10 cm')).toBeTruthy();
    expect(screen.getByText('2 kg')).toBeTruthy();
    expect(screen.getByText('Calzado deportivo')).toBeTruthy();
    expect(screen.getAllByText(esAdmin.paquetes_guia.bolsa_plastica)).toHaveLength(2);
    expect(repositorio.pedidos).toEqual(['p-1']);
  });

  it('pagado en línea no habla de recaudo', async () => {
    await renderPaquetes(MIXTO);

    await screen.findByText('25 × 20 × 15 cm');
    expect(screen.queryByText(esAdmin.paquetes_guia.con_recaudo)).toBeNull();
  });

  /** Lo que evita cobrar el doble: no escribir el total en cada paquete. */
  it('en contraentrega avisa que el flete ya va repartido', async () => {
    await renderPaquetes({ ...MIXTO, conRecaudo: true });

    expect(await screen.findByText(esAdmin.paquetes_guia.con_recaudo)).toBeTruthy();
  });

  it('si un artículo no se puede empacar dice qué hacer, por su código', async () => {
    await renderPaquetes(new ErrorHttp(409, 'no se pudieron calcular', 'ARTICULO_SIN_MEDIDAS'));

    expect(await screen.findByText(esAdmin.errores.articulo_sin_medidas)).toBeTruthy();
  });
});
