import { render } from '@testing-library/angular';
import { TsCargando } from './ts-cargando';

async function renderCargando(clase?: string) {
  const resultado = await render(TsCargando, clase ? { inputs: { clase } } : {});
  await resultado.fixture.whenStable();
  const svg = resultado.container.querySelector('svg');
  if (!svg) {
    throw new Error('El anillo no pintó ningún <svg>.');
  }
  return { ...resultado, svg };
}

describe('TsCargando', () => {
  /**
   * El anillo no es el anuncio de que algo está cargando: eso le toca al `aria-busy` del botón o al
   * `role="status"` del párrafo que lo acompaña. Si se anunciara aquí, el estado se diría dos
   * veces — y con lector de pantalla eso es lo único perceptible.
   */
  it('no dice nada al lector de pantalla', async () => {
    const { svg } = await renderCargando();

    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(svg.getAttribute('focusable')).toBe('false');
  });

  /**
   * Los dos círculos son el anillo: la pista entera al 25 % y el arco encima. Sin la pista, un arco
   * suelto girando se lee como un fragmento y no como un ciclo.
   */
  it('pinta la pista completa y el arco encima', async () => {
    const { svg } = await renderCargando();

    const circulos = [...svg.querySelectorAll('circle')];
    expect(circulos).toHaveLength(2);
    expect(circulos[0].getAttribute('opacity')).toBe('0.25');
    expect(circulos[0].getAttribute('stroke-dasharray')).toBeNull();
    expect(circulos[1].getAttribute('stroke-dasharray')).toBe('13.74 41.24');
  });

  /**
   * Los dos trazos salen de `currentColor` y no de un color fijo, que es lo que separa este anillo
   * del de la referencia: se pinta sobre el grafito del botón primario, el ámbar del de acento, el
   * rojo del de peligro y el fondo de la página, y un arco de color fijo desaparece sobre el suyo.
   */
  it('hereda el color del texto en los dos trazos', async () => {
    const { svg } = await renderCargando();

    expect(
      [...svg.querySelectorAll('circle')].every(
        (circulo) => circulo.getAttribute('stroke') === 'currentColor',
      ),
    ).toBe(true);
  });

  /**
   * `girando` es lo que lo hace girar, y es la clase que `src/tailwind.css` apaga cuando se pidió
   * menos movimiento. Que exista en el elemento es la mitad que una prueba sí puede ver; que la
   * animación se detenga de verdad hay que mirarlo en el navegador, porque jsdom no aplica CSS.
   */
  it('lleva la clase que lo gira, y la de quien llama gana sobre el tamaño por omisión', async () => {
    const { svg } = await renderCargando('size-24 text-ts-acento');

    const clases = svg.getAttribute('class') ?? '';
    expect(clases).toContain('girando');
    expect(clases).toContain('size-24');
    expect(clases).toContain('text-ts-acento');
    expect(clases).not.toContain('size-16');
  });
});
