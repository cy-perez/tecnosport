import { indiceCircular, indiceDesdeDesplazamiento, indiceOpuesto, ordenDePrecarga } from './rotacion-360';

describe('indiceCircular', () => {
  it('deja intacto un índice dentro del rango', () => {
    expect(indiceCircular(3, 8)).toBe(3);
  });

  it('pasado el último vuelve al primero', () => {
    expect(indiceCircular(8, 8)).toBe(0);
    expect(indiceCircular(9, 8)).toBe(1);
  });

  it('antes del primero llega al último', () => {
    expect(indiceCircular(-1, 8)).toBe(7);
    expect(indiceCircular(-9, 8)).toBe(7);
  });

  it('aguanta varias vueltas completas en los dos sentidos', () => {
    expect(indiceCircular(25, 8)).toBe(1);
    expect(indiceCircular(-25, 8)).toBe(7);
  });

  it('sin fotogramas no hay índice', () => {
    expect(indiceCircular(3, 0)).toBe(0);
    expect(indiceCircular(3, -1)).toBe(0);
  });
});

describe('indiceDesdeDesplazamiento', () => {
  it('sin mover el puntero el fotograma no cambia', () => {
    expect(indiceDesdeDesplazamiento(0, 400, 8, 2)).toBe(2);
  });

  it('arrastrar hacia la izquierda avanza el índice', () => {
    // Un octavo del ancho con 8 fotogramas es exactamente un paso.
    expect(indiceDesdeDesplazamiento(-50, 400, 8, 0)).toBe(1);
  });

  it('arrastrar hacia la derecha retrocede, y da la vuelta por el final', () => {
    expect(indiceDesdeDesplazamiento(50, 400, 8, 0)).toBe(7);
  });

  it('un arrastre de un ancho completo es un giro completo: vuelve al mismo fotograma', () => {
    expect(indiceDesdeDesplazamiento(-400, 400, 8, 3)).toBe(3);
    expect(indiceDesdeDesplazamiento(400, 400, 8, 3)).toBe(3);
  });

  it('un arrastre de más de un ancho sigue girando, no se topa', () => {
    expect(indiceDesdeDesplazamiento(-450, 400, 8, 0)).toBe(1);
  });

  it('el medio paso cuesta lo mismo hacia los dos lados', () => {
    // La mitad de un paso (25 px de 50) redondea al siguiente en ambas direcciones.
    expect(indiceDesdeDesplazamiento(-25, 400, 8, 0)).toBe(1);
    expect(indiceDesdeDesplazamiento(25, 400, 8, 0)).toBe(7);
  });

  it('un desplazamiento menor a medio paso todavía no gira', () => {
    expect(indiceDesdeDesplazamiento(-20, 400, 8, 4)).toBe(4);
  });

  it('la sensibilidad es relativa al ancho: el mismo gesto en un contenedor mitad de ancho gira el doble', () => {
    expect(indiceDesdeDesplazamiento(-100, 800, 8, 0)).toBe(1);
    expect(indiceDesdeDesplazamiento(-100, 400, 8, 0)).toBe(2);
  });

  it('sin ancho medido todavía, el índice se queda donde estaba', () => {
    expect(indiceDesdeDesplazamiento(-200, 0, 8, 5)).toBe(5);
  });

  it('con un solo fotograma no hay a dónde girar', () => {
    expect(indiceDesdeDesplazamiento(-500, 400, 1, 0)).toBe(0);
  });

  it('sin fotogramas devuelve 0 en vez de un índice imposible', () => {
    expect(indiceDesdeDesplazamiento(-500, 400, 0, 0)).toBe(0);
  });
});

describe('indiceOpuesto', () => {
  it('con 8 fotogramas el opuesto del frontal es el cuarto', () => {
    expect(indiceOpuesto(8)).toBe(4);
  });

  it('con 4 fotogramas es el posterior', () => {
    expect(indiceOpuesto(4)).toBe(2);
  });

  it('con un número impar no hay opuesto exacto: elige el anterior', () => {
    expect(indiceOpuesto(5)).toBe(2);
  });

  it('sin fotogramas devuelve 0', () => {
    expect(indiceOpuesto(0)).toBe(0);
  });
});

describe('ordenDePrecarga', () => {
  it('alterna vecinos a un lado y al otro, empezando por los más cercanos', () => {
    expect(ordenDePrecarga(0, 8)).toEqual([1, 7, 2, 6, 3, 5, 4]);
  });

  it('el orden es circular: desde el último sigue por el primero', () => {
    expect(ordenDePrecarga(7, 8)).toEqual([0, 6, 1, 5, 2, 4, 3]);
  });

  it('nunca incluye el fotograma actual, que ya está pedido', () => {
    expect(ordenDePrecarga(3, 8)).not.toContain(3);
  });

  it('cubre todos los fotogramas menos el actual, sin repetir', () => {
    const orden = ordenDePrecarga(2, 5);

    expect(orden).toHaveLength(4);
    expect(new Set(orden).size).toBe(4);
  });

  it('con un número impar de fotogramas también termina', () => {
    expect(ordenDePrecarga(0, 5)).toEqual([1, 4, 2, 3]);
  });

  it('con un solo fotograma no hay nada que precargar', () => {
    expect(ordenDePrecarga(0, 1)).toEqual([]);
    expect(ordenDePrecarga(0, 0)).toEqual([]);
  });
});
