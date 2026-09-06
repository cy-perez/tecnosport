import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import esCaptura from '../../../../../assets/i18n/scopes/captura360/es.json';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import { Nivel } from '../../domain/nivel-360';
import { TsIndicadorNivel } from './ts-indicador-nivel';

function nivel(parcial: Partial<Nivel>): Nivel {
  return {
    estado: 'EN_RANGO',
    desviacionBeta: 0,
    desviacionGamma: 0,
    ejeDominante: 'BETA',
    puedeDisparar: true,
    ...parcial,
  };
}

async function renderIndicador(valor: Nivel, fijandoReferencia = false) {
  return render(TsIndicadorNivel, {
    inputs: { nivel: valor, fijandoReferencia },
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'captura360/es': esCaptura } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
  });
}

describe('TsIndicadorNivel', () => {
  it('en rango lo dice con texto, no solo con un color', async () => {
    await renderIndicador(nivel({ estado: 'EN_RANGO' }));

    expect(screen.getByText('Nivelado.')).toBeTruthy();
  });

  it('fuera de rango dice hacia donde corregir y cuantos grados', async () => {
    await renderIndicador(
      nivel({
        estado: 'FUERA_DE_RANGO',
        desviacionBeta: -7,
        ejeDominante: 'BETA',
        puedeDisparar: false,
      }),
    );

    expect(screen.getByText('Inclina el teléfono 7° hacia adelante.')).toBeTruthy();
  });

  it('distingue el eje: gamma se corrige girando, no inclinando', async () => {
    await renderIndicador(
      nivel({ estado: 'CERCA', desviacionGamma: 5, ejeDominante: 'GAMMA', puedeDisparar: false }),
    );

    expect(screen.getByText('Gira el teléfono 5° hacia la derecha.')).toBeTruthy();
  });

  it('sin sensor lo dice, en vez de fingir que esta nivelado', async () => {
    await renderIndicador(nivel({ estado: 'SIN_SENSOR', ejeDominante: null }));

    expect(screen.getByText('Sin nivel disponible.')).toBeTruthy();
  });

  it('la primera toma avisa de que es ella la que fija la referencia', async () => {
    await renderIndicador(nivel({ estado: 'EN_RANGO' }), true);

    expect(screen.getByText('Esta toma fija la inclinación de referencia del set.')).toBeTruthy();
  });

  it('cada estado lleva su glifo, para no distinguirlos solo por el color', async () => {
    const { container } = await renderIndicador(nivel({ estado: 'EN_RANGO' }));

    expect(container.querySelector('.indicador-nivel__glifo')?.textContent?.trim()).toBe('●');
  });

  it('y el glifo de fuera de rango no es el mismo que el de en rango', async () => {
    const { container } = await renderIndicador(
      nivel({ estado: 'FUERA_DE_RANGO', desviacionBeta: -20, puedeDisparar: false }),
    );

    expect(container.querySelector('.indicador-nivel__glifo')?.textContent?.trim()).toBe('○');
  });
});
