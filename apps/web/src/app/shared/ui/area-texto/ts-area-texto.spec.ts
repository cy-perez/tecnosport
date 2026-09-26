import { Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { fireEvent, render, screen } from '@testing-library/angular';
import { TsAreaTexto } from './ts-area-texto';

/**
 * Anfitrión con un `FormControl` real: lo que hay que demostrar de un `ControlValueAccessor` es que
 * un formulario reactivo puede ligarlo, no que el componente se construye.
 */
@Component({
  imports: [ReactiveFormsModule, TsAreaTexto],
  template: `
    <ts-area-texto
      idCampo="prueba"
      label="Tu mensaje"
      [maximo]="maximo()"
      [textoContador]="textoContador"
      [error]="error()"
      [obligatorio]="true"
      [formControl]="control"
    />
  `,
})
class Anfitrion {
  readonly control = new FormControl('', { nonNullable: true });
  // Señales y no campos llanos: la aplicación es zoneless, así que cambiar una propiedad del
  // anfitrión no despierta a nadie y el `input()` del hijo se queda con el valor con el que nació.
  readonly maximo = signal<number | null>(20);
  readonly error = signal<string | null>(null);
  textoContador = (restantes: number) => `Quedan ${restantes}`;
}

describe('TsAreaTexto', () => {
  it('la etiqueta nombra el control y declara que es obligatorio', async () => {
    await render(Anfitrion);

    const area = screen.getByLabelText('Tu mensaje');
    expect(area.tagName).toBe('TEXTAREA');
    expect(area.getAttribute('aria-required')).toBe('true');
  });

  it('escribir actualiza el control del formulario', async () => {
    const { fixture } = await render(Anfitrion);

    fireEvent.input(screen.getByLabelText('Tu mensaje'), { target: { value: 'hola' } });

    expect(fixture.componentInstance.control.value).toBe('hola');
  });

  it('el control escribe en el área', async () => {
    const { fixture } = await render(Anfitrion);

    fixture.componentInstance.control.setValue('desde el formulario');
    fixture.detectChanges();

    expect((screen.getByLabelText('Tu mensaje') as HTMLTextAreaElement).value).toBe(
      'desde el formulario',
    );
  });

  /**
   * El contador y el `maxlength` van juntos y no se sustituyen: el segundo impide pasarse y el
   * primero explica por qué el campo dejó de aceptar teclas. Un campo que se queda mudo parece roto.
   */
  it('cuenta los caracteres que quedan y pone el tope en el elemento', async () => {
    await render(Anfitrion);

    expect(screen.getByText('Quedan 20')).toBeTruthy();
    expect(screen.getByLabelText('Tu mensaje').getAttribute('maxlength')).toBe('20');

    fireEvent.input(screen.getByLabelText('Tu mensaje'), { target: { value: 'hola' } });

    expect(screen.getByText('Quedan 16')).toBeTruthy();
  });

  /**
   * Los tres textos de apoyo describen el control a la vez. Sin esto, enseñar un error dejaba el
   * contador fuera del nombre accesible justo cuando más falta hace.
   */
  it('ata el contador y el error al control con aria-describedby', async () => {
    const { fixture } = await render(Anfitrion);

    fixture.componentInstance.error.set('Escribe algo');
    fixture.detectChanges();

    expect(screen.getByLabelText('Tu mensaje').getAttribute('aria-describedby')).toBe(
      'prueba-contador prueba-error',
    );
    expect(screen.getByLabelText('Tu mensaje').getAttribute('aria-invalid')).toBe('true');
  });

  it('sin tope no pinta contador ni maxlength', async () => {
    const { fixture } = await render(Anfitrion);

    fixture.componentInstance.maximo.set(null);
    fixture.detectChanges();

    expect(screen.queryByText(/Quedan/)).toBeNull();
    expect(screen.getByLabelText('Tu mensaje').getAttribute('maxlength')).toBeNull();
  });
});
