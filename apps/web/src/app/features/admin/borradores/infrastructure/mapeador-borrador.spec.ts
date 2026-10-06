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
    expect(aFoto({ mensajeId: 'f1', url: 'u', origen: 'PANEL' }).origen).toBe('PANEL');
    expect(aFoto({ mensajeId: 'f2', url: 'u', origen: 'PROVEEDOR' }).origen).toBe('PROVEEDOR');
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

  it('al aprobar traduce las tallas y omite el tono de una foto que vale para todos', () => {
    const peticion = aAprobarPeticion({
      marcaId: 'm1',
      categoriaId: 'c1',
      precioVenta: 60000,
      tallas: { tipo: 'UNICA', sirveHasta: 'L', valores: [] },
      fotos: [
        { mensajeId: 'f1', tono: 'Negro', colorHex: null },
        { mensajeId: 'f2', tono: null, colorHex: null },
      ],
      altEs: 'Bolso negro',
      altEn: 'Black bag',
      existenciaInicial: 2,
      fotosGeneralesEnCadaColor: false,
    });

    expect(peticion.fotosGeneralesEnCadaColor).toBe(false);
    expect(peticion.tallas).toEqual({ tipo: 'UNICA', sirveHasta: 'L', valores: [] });
    expect(peticion.fotos).toEqual([{ mensajeId: 'f1', tono: 'Negro' }, { mensajeId: 'f2' }]);
    expect(peticion.existenciaInicial).toBe(2);
  });
});
