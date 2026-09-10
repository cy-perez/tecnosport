import { Component, signal } from '@angular/core';
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

@Component({
  imports: [ReactiveFormsModule, TsCampo],
  template: `
    <ts-campo idCampo="correo" label="Correo" tipo="email" [error]="error()" [formControl]="control" />
  `,
})
class AnfitrionConError {
  readonly control = new FormControl('');
  readonly error = signal<string | null>(null);
}

describe('TsCampo con error', () => {
  it('sin error no marca el campo como inválido ni describe nada', async () => {
    await render(AnfitrionConError);
    const campo = screen.getByLabelText('Correo');

    expect(campo.hasAttribute('aria-invalid')).toBe(false);
    expect(campo.hasAttribute('aria-describedby')).toBe(false);
  });

  it('el mensaje de error se anuncia y queda ligado al campo', async () => {
    const { fixture } = await render(AnfitrionConError);
    fixture.componentInstance.error.set('Correo no válido');
    await fixture.whenStable();

    const campo = screen.getByLabelText('Correo');
    const mensaje = screen.getByRole('alert');

    expect(campo.getAttribute('aria-invalid')).toBe('true');
    // El vínculo real: sin esto el lector de pantalla no relaciona el mensaje
    // con el campo, aunque se vea justo debajo.
    expect(campo.getAttribute('aria-describedby')).toBe(mensaje.id);
    expect(mensaje.textContent?.trim()).toBe('Correo no válido');
  });

  it('deshabilitar el FormControl deshabilita el input real', async () => {
    const { fixture } = await render(AnfitrionConError);
    fixture.componentInstance.control.disable();
    await fixture.whenStable();

    expect((screen.getByLabelText('Correo') as HTMLInputElement).disabled).toBe(true);
  });

  // docs/04-ui-marca.md: foco visible en el 100% de los enfocables.
  it('conserva el anillo de foco y el objetivo táctil', async () => {
    await render(AnfitrionConError);
    const clases = screen.getByLabelText('Correo').className;

    // `anillo-foco` es la utilidad del proyecto que compone las tres de
    // Tailwind (`src/tailwind.css`): existe para que no se olvide ninguna.
    expect(clases).toContain('anillo-foco');
    expect(clases).toContain('min-h-tactil');
  });
});

@Component({
  imports: [ReactiveFormsModule, TsCampo],
  template: `
    <ts-campo
      idCampo="fecha"
      label="Fecha en que llegó"
      ayuda="La del correo, no la de hoy."
      [error]="error()"
      [formControl]="control"
    />
  `,
})
class AnfitrionConAyuda {
  readonly control = new FormControl('');
  readonly error = signal<string | null>(null);
}

describe('TsCampo con ayuda', () => {
  /**
   * La ayuda tiene que quedar atada al control, no solo cerca. Escrita como un `<p>` suelto antes
   * del componente se veía pegada al campo anterior y un lector de pantalla no la relacionaba con
   * nada — que es como estaba antes de que el recorrido en el navegador lo dejara a la vista.
   */
  it('describe el control con la ayuda', async () => {
    await render(AnfitrionConAyuda);
    const campo = screen.getByLabelText('Fecha en que llegó');

    expect(campo.getAttribute('aria-describedby')).toBe('fecha-ayuda');
    expect(screen.getByText('La del correo, no la de hoy.').id).toBe('fecha-ayuda');
  });

  /**
   * Con error, los dos: sin esto, mostrar un error dejaba la ayuda fuera de la descripción del
   * control justo cuando más falta hace.
   */
  it('con error describe la ayuda y el error a la vez', async () => {
    const { fixture } = await render(AnfitrionConAyuda);
    fixture.componentInstance.error.set('Fecha inválida');
    await fixture.whenStable();

    expect(screen.getByLabelText('Fecha en que llegó').getAttribute('aria-describedby')).toBe(
      'fecha-ayuda fecha-error',
    );
  });
});
