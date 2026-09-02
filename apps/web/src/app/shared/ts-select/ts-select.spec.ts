import { Component } from '@angular/core';
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
});
