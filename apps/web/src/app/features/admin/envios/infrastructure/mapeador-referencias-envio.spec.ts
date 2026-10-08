import { aReferenciasDeEnvio } from './mapeador-referencias-envio';

describe('mapeador de las referencias de envío', () => {
  it('una respuesta vacía da medidas nulas y una lista vacía', () => {
    expect(aReferenciasDeEnvio({})).toEqual({ medidas: null, categorias: [] });
  });

  it('mapea las medidas y cada categoría con su rama y su peso', () => {
    const referencias = aReferenciasDeEnvio({
      medidas: { largoCm: 40, anchoCm: 30, altoCm: 10 },
      categorias: [
        { categoriaId: 'c1', nombre: 'Jeans', rama: 'Dama', linea: 'ROPA', pesoGramos: 700 },
        { categoriaId: 'c2', nombre: 'Unisex', linea: 'CALZADO' },
      ],
    });

    expect(referencias).toEqual({
      medidas: { largoCm: 40, anchoCm: 30, altoCm: 10 },
      categorias: [
        { categoriaId: 'c1', nombre: 'Jeans', rama: 'Dama', linea: 'ROPA', pesoGramos: 700 },
        { categoriaId: 'c2', nombre: 'Unisex', rama: null, linea: 'CALZADO', pesoGramos: null },
      ],
    });
  });

  /**
   * Una categoría sin peso no es una de peso cero: la primera se vende solo con recogida, y un
   * cero en el formulario se leería como si alguien lo hubiera puesto así.
   */
  it('una categoría sin peso queda en nulo y no en cero', () => {
    const referencias = aReferenciasDeEnvio({
      categorias: [{ categoriaId: 'c1', nombre: 'Polos', linea: 'ROPA' }],
    });

    expect(referencias.categorias[0].pesoGramos).toBeNull();
  });

  it('unas medidas a medias se tratan como ausentes', () => {
    expect(aReferenciasDeEnvio({ medidas: { largoCm: 40, anchoCm: 30 } }).medidas).toBeNull();
  });

  it('descarta las filas sin id o de una línea que no se promedia', () => {
    const referencias = aReferenciasDeEnvio({
      categorias: [
        { nombre: 'Sin id', linea: 'ROPA' },
        { categoriaId: 'c9', nombre: 'Audífonos', linea: 'TECNOLOGIA' },
        { categoriaId: 'c1', nombre: 'Jeans', linea: 'ROPA' },
      ],
    });

    expect(referencias.categorias.map((fila) => fila.categoriaId)).toEqual(['c1']);
  });
});
