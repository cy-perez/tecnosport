import { aAprobarPeticion, aBorrador, aEditarPeticion, aFoto } from './mapeador-borrador';

describe('mapeador-borrador', () => {
  /**
   * Un borrador sin precio es el caso de la alerta `SIN_PRECIO`: si el mapeador convirtiera el
   * ausente en cero, la pantalla mostraría «$ 0» como si el proveedor lo hubiera dicho y la
   * alerta perdería su razón.
   */
  it('deja el precio en null cuando el proveedor no lo dijo, y no en cero', () => {
    const borrador = aBorrador({ id: 'b1', estado: 'EN_REVISION', alertas: ['SIN_PRECIO'] });

    expect(borrador.precioProveedor).toBeNull();
    expect(borrador.precioVentaSugerido).toBeNull();
    expect(borrador.alertas).toEqual(['SIN_PRECIO']);
    expect(borrador.tallas).toEqual({ tipo: 'DESCONOCIDA', sirveHasta: null, valores: [] });
  });

  /** Del origen depende qué promete eliminarla: que el archivo se queda, o que se borra. */
  it('conserva el origen de cada foto', () => {
    expect(aFoto({ mensajeId: 'f1', url: 'u', origen: 'PANEL', tonosVistos: [] }).origen).toBe(
      'PANEL',
    );
    expect(aFoto({ mensajeId: 'f2', url: 'u', origen: 'PROVEEDOR', tonosVistos: [] }).origen).toBe(
      'PROVEEDOR',
    );
  });

  /** Un borrador de antes del 10 de octubre de 2026 no trae ni tallas por tono ni otros precios. */
  it('lleva las tallas por tono, los otros precios y el tono sugerido, o vacíos si no vienen', () => {
    const conTodo = aBorrador({
      id: 'b1',
      tallasPorTono: [{ tono: 'cocoa', tallas: ['ML'] }],
      preciosAdicionales: [{ concepto: 'Gorra', precio: 35000 }],
    });

    expect(conTodo.tallasPorTono).toEqual([{ tono: 'cocoa', tallas: ['ML'] }]);
    expect(conTodo.preciosAdicionales).toEqual([{ concepto: 'Gorra', precio: 35000 }]);
    expect(aBorrador({ id: 'b2' }).tallasPorTono).toEqual([]);
    expect(aBorrador({ id: 'b2' }).preciosAdicionales).toEqual([]);
    expect(
      aFoto({
        mensajeId: 'f1',
        url: 'u',
        origen: 'PROVEEDOR',
        tonoSugerido: 'negro',
        tonosVistos: [],
      }).tonoSugerido,
    ).toBe('negro');
    expect(
      aFoto({ mensajeId: 'f2', url: 'u', origen: 'PROVEEDOR', tonosVistos: [] }).tonoSugerido,
    ).toBeNull();
  });

  /**
   * El PATCH es parcial: mandar `titulo: undefined` como clave presente haría que Jackson lo
   * leyera como nulo y el caso de uso lo interpretara como «borra el título». Solo viaja lo que
   * de verdad cambió.
   */
  it('al editar manda solo los campos que vinieron', () => {
    const peticion = aEditarPeticion({ precioVentaSugerido: 60000 });

    expect(peticion).toEqual({ precioVentaSugerido: 60000 });
    expect('titulo' in peticion).toBe(false);
  });

  it('al aprobar traduce las tallas y omite el tono y la prenda de una foto que vale para todos', () => {
    const peticion = aAprobarPeticion({
      marcaId: 'm1',
      categoriaId: 'c1',
      precioVenta: 60000,
      tallas: { tipo: 'UNICA', sirveHasta: 'L', valores: [] },
      fotos: [
        { mensajeId: 'f1', tono: 'Negro', colorHex: null, prenda: 1 },
        { mensajeId: 'f2', tono: null, colorHex: null, prenda: null },
      ],
      altEs: 'Bolso negro',
      altEn: 'Black bag',
      existenciaInicial: 2,
      prendas: [],
      principalEnLaFicha: false,
    });

    expect('prendas' in peticion).toBe(false);
    expect('principalEnLaFicha' in peticion).toBe(false);
    expect(peticion.tallas).toEqual({ tipo: 'UNICA', sirveHasta: 'L', valores: [] });
    expect(peticion.fotos).toEqual([
      { mensajeId: 'f1', tono: 'Negro', prenda: 1 },
      { mensajeId: 'f2' },
    ]);
    expect(peticion.existenciaInicial).toBe(2);
  });

  /**
   * La prenda sin foto propia viaja aparte de las fotos: si el mapeador la dejara atrás, la blusa
   * de tres tonos fotografiados juntos se aprobaría sin un solo color que elegir.
   */
  it('al aprobar manda las prendas, con sus tallas solo cuando se dijeron', () => {
    const peticion = aAprobarPeticion({
      marcaId: 'm1',
      categoriaId: 'c1',
      precioVenta: 58000,
      tallas: { tipo: 'LISTA', sirveHasta: null, valores: ['SM', 'ML'] },
      fotos: [{ mensajeId: 'f1', tono: null, colorHex: null, prenda: null }],
      altEs: 'Blusa',
      altEn: 'Top',
      existenciaInicial: 1,
      prendas: [
        { numero: 1, tono: 'Negro', colorHex: '#000000', tallas: null, seVeEn: null },
        { numero: 2, tono: 'Café', colorHex: null, tallas: ['ML'], seVeEn: null },
      ],
      principalEnLaFicha: false,
    });

    expect(peticion.prendas).toEqual([
      { numero: 1, tono: 'Negro', colorHex: '#000000' },
      { numero: 2, tono: 'Café', tallas: ['ML'] },
    ]);
  });

  /** La camiseta de cinco tonos en dos fotos, y la principal pedida en la ficha (ADR-0082). */
  it('al aprobar manda en qué foto se ve cada prenda y la casilla de la principal', () => {
    const peticion = aAprobarPeticion({
      marcaId: 'm1',
      categoriaId: 'c1',
      precioVenta: 48000,
      fotos: [
        { mensajeId: 'f1', tono: null, colorHex: null, prenda: null },
        { mensajeId: 'f2', tono: null, colorHex: null, prenda: null },
      ],
      altEs: 'Camiseta',
      altEn: 'T-shirt',
      existenciaInicial: 1,
      prendas: [
        { numero: 1, tono: 'Negro', colorHex: null, tallas: null, seVeEn: 'f1' },
        { numero: 2, tono: 'Cocoa', colorHex: null, tallas: null, seVeEn: 'f2' },
        { numero: 3, tono: 'Crema', colorHex: null, tallas: null, seVeEn: null },
      ],
      principalEnLaFicha: true,
    });

    expect(peticion.prendas).toEqual([
      { numero: 1, tono: 'Negro', seVeEn: 'f1' },
      { numero: 2, tono: 'Cocoa', seVeEn: 'f2' },
      { numero: 3, tono: 'Crema' },
    ]);
    expect(peticion.principalEnLaFicha).toBe(true);
  });

  it('lleva los tonos que la lectura vio en una foto de varios', () => {
    expect(
      aFoto({ mensajeId: 'f1', url: 'u', origen: 'PROVEEDOR', tonosVistos: ['negro', 'cocoa'] })
        .tonosVistos,
    ).toEqual(['negro', 'cocoa']);
  });
});
