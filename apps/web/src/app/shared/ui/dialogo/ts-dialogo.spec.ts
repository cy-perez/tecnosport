import { Component, signal } from '@angular/core';
import { fireEvent, render, screen } from '@testing-library/angular';
import { TsDialogo } from './ts-dialogo';

@Component({
  imports: [TsDialogo],
  template: `
    <button type="button" #disparador>Abrir</button>
    <ts-dialogo
      idDialogo="borrar-set"
      [abierto]="abierto()"
      titulo="Borrar el set de rotación"
      etiquetaCerrar="Cerrar"
      (cerrar)="cerrado.set(true)"
    >
      <p>Esta acción no se puede deshacer.</p>
      <button acciones type="button">Borrar</button>
    </ts-dialogo>
  `,
})
class Anfitrion {
  readonly abierto = signal(false);
  readonly cerrado = signal(false);
}

async function abierto() {
  const vista = await render(Anfitrion);
  vista.fixture.componentInstance.abierto.set(true);
  await vista.fixture.whenStable();
  return vista;
}

describe('TsDialogo', () => {
  it('cerrado no pinta nada: en el servidor no existe y no hay nada que hidratar', async () => {
    await render(Anfitrion);

    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('abierto se anuncia como diálogo modal', async () => {
    await abierto();
    const dialogo = screen.getByRole('dialog');

    expect(dialogo.getAttribute('aria-modal')).toBe('true');
  });

  // Sin el vínculo, un lector de pantalla anuncia "diálogo" y nada más.
  it('toma su nombre accesible del título', async () => {
    await abierto();

    expect(screen.getByRole('dialog', { name: 'Borrar el set de rotación' })).toBeTruthy();
  });

  it('proyecta el contenido y las acciones', async () => {
    await abierto();

    expect(screen.getByText('Esta acción no se puede deshacer.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Borrar' })).toBeTruthy();
  });

  it('el botón de cerrar tiene nombre accesible, aunque solo muestre un icono', async () => {
    await abierto();

    expect(screen.getByRole('button', { name: 'Cerrar' })).toBeTruthy();
  });

  it('el botón de cerrar emite cerrar', async () => {
    const { fixture } = await abierto();

    fireEvent.click(screen.getByRole('button', { name: 'Cerrar' }));

    expect(fixture.componentInstance.cerrado()).toBe(true);
  });

  it('Escape emite cerrar', async () => {
    const { fixture } = await abierto();

    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });

    expect(fixture.componentInstance.cerrado()).toBe(true);
  });

  // El diálogo se pinta en un portal del CDK, así que el fondo ya no es el
  // padre del panel: es el backdrop que crea el overlay. Cerrar al pulsarlo lo
  // trae Spartan por omisión (`closeOnOutsidePointerEvents`), no lo escribimos
  // nosotros.
  it('un clic en el fondo emite cerrar', async () => {
    const { fixture } = await abierto();
    const fondo = document.querySelector('.cdk-overlay-backdrop');
    expect(fondo).toBeTruthy();

    fireEvent.click(fondo!);
    await fixture.whenStable();

    expect(fixture.componentInstance.cerrado()).toBe(true);
  });

  // El caso que rompe los modales caseros: arrastrar texto dentro y soltar
  // burbujea un clic hasta el fondo, que no debe cerrar.
  it('un clic dentro del panel NO cierra', async () => {
    const { fixture } = await abierto();

    fireEvent.click(screen.getByText('Esta acción no se puede deshacer.'));

    expect(fixture.componentInstance.cerrado()).toBe(false);
  });

  // El `role="dialog"` lo lleva el contenedor del CDK; el chaflán es del panel
  // de marca que va dentro.
  it('el panel lleva el chaflán de la marca', async () => {
    await abierto();

    expect(screen.getByRole('dialog').querySelector('.chaflan')).toBeTruthy();
  });

  // Lo que el portal aporta y la versión anterior no tenía: el diálogo se pinta
  // fuera del árbol del componente, así que ningún `z-index` de un ancestro
  // puede taparlo.
  it('se pinta en el overlay del CDK, no dentro del componente', async () => {
    const { container } = await abierto();

    expect(container.querySelector('[role="dialog"]')).toBeNull();
    expect(document.querySelector('.cdk-overlay-container [role="dialog"]')).toBeTruthy();
  });
});
