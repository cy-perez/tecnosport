/**
 * Lo que hay que escribir en el formulario "Cotizar y crear" de la plataforma para crear a mano la
 * guía de un pedido (`ADR-0071`): un paquete por renglón, en orden.
 */
export interface PaqueteParaGuia {
  /** Kilos enteros, ya redondeados hacia arriba: es lo único que acepta el formulario. */
  readonly pesoKg: number;
  readonly largoCm: number;
  readonly anchoCm: number;
  readonly altoCm: number;
  readonly valorDeclarado: { readonly valor: number; readonly moneda: string };
  readonly contenido: string;
}

export interface PaquetesDePedido {
  readonly paquetes: readonly PaqueteParaGuia[];
  /**
   * Contraentrega: los valores declarados ya llevan repartido el flete, y su suma es lo que la
   * transportadora cobra en la puerta. Escribir "el total" en cada paquete cobraría de más.
   */
  readonly conRecaudo: boolean;
}
