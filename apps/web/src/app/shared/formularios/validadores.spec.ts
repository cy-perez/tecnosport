import { describe, expect, it } from 'vitest';

import { FormControl } from '@angular/forms';
import { correoValido, nombreDeBarrio, nombreDePersona, textoDeDireccion } from './validadores';

/** Un control suelto, que es todo lo que estos validadores necesitan. */
function control(valor: string): FormControl<string> {
  return new FormControl(valor, { nonNullable: true });
}

describe('correoValido', () => {
  it.each(['juan@tecnosport.co', 'juan.perez+tienda@correo.com.co', '  juan@correo.co  '])(
    'acepta %s',
    (valor) => {
      expect(correoValido(control(valor))).toBeNull();
    },
  );

  it.each(['juan', '1234', '@#$%', 'juan@', '@correo.co', 'juan correo.co'])(
    'rechaza %s',
    (valor) => {
      expect(correoValido(control(valor))).toEqual({ correoInvalido: true });
    },
  );

  // La franja que dejaba pasar `Validators.email` y el servidor rechazaba con un 422: el dominio
  // sin punto. Es el caso que se veía como "No se pudo crear la cuenta. Intenta de nuevo.".
  it('rechaza el dominio sin punto, que es lo que aceptaba Validators.email', () => {
    expect(correoValido(control('juan@correo'))).toEqual({ correoInvalido: true });
  });

  it('el campo vacío es asunto de required, no suyo', () => {
    expect(correoValido(control(''))).toBeNull();
  });
});

describe('nombreDePersona', () => {
  it.each(['Juan Pérez', "María D'Angelo", 'Ruiz-Mejía', 'J. Gómez', 'Ana'])(
    'acepta %s',
    (valor) => {
      expect(nombreDePersona(control(valor))).toBeNull();
    },
  );

  it.each(['@#$%', 'Juan123', 'Juan <script>', 'a', '   ', 'Calle 10 #43-25'])(
    'rechaza %s',
    (valor) => {
      expect(nombreDePersona(control(valor))).toEqual({ nombreInvalido: true });
    },
  );

  it('el campo vacío es asunto de required, no suyo', () => {
    expect(nombreDePersona(control(''))).toBeNull();
  });
});

describe('textoDeDireccion', () => {
  it.each([
    'Cra 43A #7-50 Apto 902',
    'Calle 10 Sur / Vereda El Salado',
    'Transversal 1.ª, torre 2 (portería)',
    'Km 3 vía Las Palmas, casa 4°',
  ])('acepta %s', (valor) => {
    expect(textoDeDireccion(control(valor))).toBeNull();
  });

  it.each(['@#$%', '<script>alert(1)</script>', '43-25', '   ', 'Calle 10 & 11'])(
    'rechaza %s',
    (valor) => {
      expect(textoDeDireccion(control(valor))).toEqual({ direccionInvalida: true });
    },
  );

  it('el campo vacío es asunto de required, no suyo', () => {
    expect(textoDeDireccion(control(''))).toBeNull();
  });
});

describe('nombreDeBarrio', () => {
  it.each(['Laureles', '20 de Julio', 'Belén (Rincón)', 'San Antonio de Prado'])(
    'acepta %s',
    (valor) => {
      expect(nombreDeBarrio(control(valor))).toBeNull();
    },
  );

  it.each(['@#$%', 'Cra 43 #7-50', '<b>Laureles</b>', '   ', '123'])('rechaza %s', (valor) => {
    expect(nombreDeBarrio(control(valor))).toEqual({ barrioInvalido: true });
  });

  // El barrio no se exige (`Direccion.java`), y validarlo no lo convierte en obligatorio.
  it('vacío sigue siendo válido: el barrio es opcional', () => {
    expect(nombreDeBarrio(control(''))).toBeNull();
  });
});
