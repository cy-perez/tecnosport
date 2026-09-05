import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { fireEvent, render, screen } from '@testing-library/angular';
import { TsCampo } from './ts-campo';

@Component({
  imports: [ReactiveFormsModule, TsCampo],
  template: `<ts-campo idCampo="precio" label="Precio" tipo="number" [formControl]="control" />`,
})
class AnfitrionDePrueba {
  readonly control = new FormControl<number | null>(null);
}

describe('TsCampo', () => {
  it('escribir en el campo actualiza el FormControl como número', async () => {
    const { fixture } = await render(AnfitrionDePrueba);
    const control = fixture.componentInstance.control;

    fireEvent.input(screen.getByLabelText('Precio'), { target: { value: '150000' } });

    expect(control.value).toBe(150000);
  });

  it('fijar el FormControl actualiza el valor mostrado', async () => {
    const { fixture } = await render(AnfitrionDePrueba);
    fixture.componentInstance.control.setValue(89900);
    fixture.detectChanges();

    expect((screen.getByLabelText('Precio') as HTMLInputElement).value).toBe('89900');
  });
});

@Component({
  imports: [ReactiveFormsModule, TsCampo],
  template: `<ts-campo idCampo="clave" label="Clave" tipo="password" [formControl]="control" />`,
})
class AnfitrionContrasenaDePrueba {
  readonly control = new FormControl('');
}

describe('TsCampo con tipo password', () => {
  it('renderiza un input type="password", para que el navegador la enmascare', async () => {
    await render(AnfitrionContrasenaDePrueba);

    expect((screen.getByLabelText('Clave') as HTMLInputElement).type).toBe('password');
  });
});
