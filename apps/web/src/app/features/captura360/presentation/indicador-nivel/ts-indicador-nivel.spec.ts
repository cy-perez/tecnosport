import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import esCaptura from '../../../../../assets/i18n/scopes/captura360/es.json';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import { AnuncioDeNivel, Nivel } from '../../domain/nivel-360';
import { TsIndicadorNivel } from './ts-indicador-nivel';

function nivel(parcial: Partial<Nivel>): Nivel {
  return {
    estado: 'EN_RANGO',
    desviacion: 0,
    inclinar: 0,
    girar: 0,
    ejeDominante: 'INCLINAR',
    puedeDisparar: true,
    ...parcial,
  };
}

async function renderIndicador(
  valor: Nivel,
  fijandoReferencia = false,
  anuncio: AnuncioDeNivel | null = null,
) {
  return render(TsIndicadorNivel, {
    inputs: { nivel: valor, fijandoReferencia, anuncio },
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
        inclinar: -7,
        ejeDominante: 'INCLINAR',
        puedeDisparar: false,
      }),
    );

    expect(screen.getByText('Inclina el teléfono 7° hacia adelante.')).toBeTruthy();
  });

  it('distingue el eje: gamma se corrige girando, no inclinando', async () => {
    await renderIndicador(
      nivel({ estado: 'CERCA', girar: 5, ejeDominante: 'GIRAR', puedeDisparar: false }),
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

  // Por el texto y no por `.indicador-nivel__glifo`: esa clase existía solo
  // para el SCSS y desapareció al pasar el componente a Tailwind. El glifo *es*
  // el texto, así que consultarlo directamente prueba lo mismo sin depender de
  // cómo esté estilado.
  it('cada estado lleva su glifo, para no distinguirlos solo por el color', async () => {
    await renderIndicador(nivel({ estado: 'EN_RANGO' }));

    expect(screen.getByText('●')).toBeTruthy();
  });

  it('y el glifo de fuera de rango no es el mismo que el de en rango', async () => {
    await renderIndicador(nivel({ estado: 'FUERA_DE_RANGO', inclinar: -20, puedeDisparar: false }));

    expect(screen.getByText('○')).toBeTruthy();
    expect(screen.queryByText('●')).toBeNull();
  });
});

describe('lo que oye un lector de pantalla', () => {
  it('el texto visible no se anuncia: cambia con cada grado y volvería la pantalla inusable', async () => {
    const { container } = await renderIndicador(
      nivel({ estado: 'FUERA_DE_RANGO', inclinar: -7, puedeDisparar: false }),
    );

    const visible = container.querySelector('p:not([role])');
    expect(visible?.getAttribute('aria-hidden')).toBe('true');
    expect(visible?.hasAttribute('aria-live')).toBe(false);
  });

  it('la región viva vive siempre en el DOM, también sin nada que decir', async () => {
    // Un `role="status"` que nace ya lleno no se anuncia: medido con NVDA el 22 de septiembre.
    const { container } = await renderIndicador(nivel({ estado: 'EN_RANGO' }));

    const region = container.querySelector('[role="status"]');
    expect(region).toBeTruthy();
    expect(region?.textContent?.trim()).toBe('');
  });

  it('anuncia lo asentado, con los grados escritos y no con el símbolo', async () => {
    // El `°` lo leen distinto los lectores de pantalla, o no lo leen.
    await renderIndicador(
      nivel({ estado: 'FUERA_DE_RANGO', inclinar: -7, puedeDisparar: false }),
      false,
      {
        clave: 'inclina_adelante',
        grados: 7,
        candidata: 'inclina_adelante',
        desde: 0,
      },
    );

    expect(screen.getByRole('status').textContent).toContain('7 grados hacia adelante');
  });

  it('el texto anunciado no repite el visible, para que no se lea dos veces', async () => {
    const { container } = await renderIndicador(nivel({ estado: 'EN_RANGO' }), false, {
      clave: 'en_rango',
      grados: 0,
      candidata: 'en_rango',
      desde: 0,
    });

    const visible = container.querySelector('p:not([role])')?.textContent?.trim();
    const anunciado = screen.getByRole('status').textContent?.trim();
    expect(visible).not.toBe(anunciado);
  });
});
