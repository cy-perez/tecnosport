import { render, screen } from '@testing-library/angular';
import { TsSuperposicionGuia } from './ts-superposicion-guia';

const FANTASMA = 'blob:toma-anterior';

describe('TsSuperposicionGuia', () => {
  it('pinta el fantasma del fotograma anterior para poder alinear contra el', async () => {
    const { container } = await render(TsSuperposicionGuia, {
      inputs: { fantasma: FANTASMA },
    });

    const imagen = container.querySelector('img');
    expect(imagen?.getAttribute('src')).toBe(FANTASMA);
  });

  it('en la primera toma no hay fantasma que pintar', async () => {
    const { container } = await render(TsSuperposicionGuia, { inputs: { fantasma: null } });

    expect(container.querySelector('img')).toBeNull();
  });

  it('el fantasma se puede ocultar sin perderlo, para ver la toma limpia', async () => {
    const { container } = await render(TsSuperposicionGuia, {
      inputs: { fantasma: FANTASMA, mostrarFantasma: false },
    });

    expect(container.querySelector('img')).toBeNull();
  });

  it('la guia es decorativa: no aporta nada a quien no la ve', async () => {
    const { fixture } = await render(TsSuperposicionGuia, { inputs: { fantasma: FANTASMA } });

    expect(fixture.nativeElement.getAttribute('aria-hidden')).toBe('true');
    // Y el fantasma tampoco se anuncia: lo que hay que saber va en texto, fuera de este componente.
    expect(screen.queryByRole('img')).toBeNull();
  });
});
