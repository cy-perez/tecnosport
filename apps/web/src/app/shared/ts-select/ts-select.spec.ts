import { Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { fireEvent, render, screen } from '@testing-library/angular';
import { OpcionSelect, TsSelect } from './ts-select';

@Component({
  imports: [ReactiveFormsModule, TsSelect],
  template: `
    <ts-select
      idCampo="linea"
      label="Línea"
      placeholder="Todas"
      [opciones]="opciones"
      [formControl]="control"
    />
  `,
})
class AnfitrionDePrueba {
  readonly control = new FormControl('');
  readonly opciones: OpcionSelect[] = [
    { valor: 'BOLSOS', etiqueta: 'Bolsos' },
    { valor: 'CELULARES', etiqueta: 'Celulares' },
  ];
}

@Component({
  imports: [ReactiveFormsModule, TsSelect],
  template: `
    <ts-select idCampo="marca" label="Marca" placeholder="Selecciona" [opciones]="opciones()" [formControl]="control" />
  `,
})
class AnfitrionConOpcionesTardias {
  readonly control = new FormControl('');
  readonly opciones = signal<OpcionSelect[]>([]);
}

describe('TsSelect', () => {
  it('elegir una opción actualiza el FormControl', async () => {
    const { fixture } = await render(AnfitrionDePrueba);
    const control = fixture.componentInstance.control;

    fireEvent.change(screen.getByLabelText('Línea'), { target: { value: 'CELULARES' } });

    expect(control.value).toBe('CELULARES');
  });

  it('incluye la opción vacía como placeholder', async () => {
    await render(AnfitrionDePrueba);

    expect(screen.getByText('Todas')).toBeTruthy();
  });

  // El valor y las opciones pueden venir de consultas distintas: en editar
  // producto, el producto (que trae el id de la marca) llegaba antes que la
  // lista de marcas, y el control se quedaba en el placeholder para siempre.
  it('conserva el valor cuando las opciones llegan después', async () => {
    const { fixture } = await render(AnfitrionConOpcionesTardias);
    const anfitrion = fixture.componentInstance;

    anfitrion.control.setValue('m1');
    await fixture.whenStable();

    anfitrion.opciones.set([
      { valor: 'm1', etiqueta: 'TecnoSport' },
      { valor: 'm2', etiqueta: 'Under Trail' },
    ]);
    await fixture.whenStable();

    expect((screen.getByLabelText('Marca') as HTMLSelectElement).value).toBe('m1');
  });
});
