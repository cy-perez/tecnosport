import { AJUSTES_DE_INGESTA_POR_OMISION } from '../domain/proveedor.model';
import { aProveedor, aProveedorPeticion } from './mapeador-proveedor';

describe('mapeador de proveedores', () => {
  it('lee el orden en que publica el proveedor', () => {
    const proveedor = aProveedor({
      id: 'prov-1',
      nombre: 'La Riverah',
      nombreEnExportacion: 'La Riverah',
      telefonoWhatsApp: '573024697047',
      linea: 'ROPA',
      factorDeMargen: 1.38,
      ordenDePublicacion: 'TEXTO_PRIMERO',
      publicacionAutomatica: false,
      dosChatsEnUnZip: false,
      activo: true,
      tiposExcluidos: ['PANTALON', 'SUDADERA'],
      armaSets: true,
      ajustesDeIngesta: {},
      local: true,
    });

    expect(proveedor.ordenDePublicacion).toBe('TEXTO_PRIMERO');
    expect(proveedor.tiposExcluidos).toEqual(['PANTALON', 'SUDADERA']);
    expect(proveedor.armaSets).toBe(true);
    expect(proveedor.local).toBe(true);
    expect(proveedor.ajustesDeIngesta).toEqual(AJUSTES_DE_INGESTA_POR_OMISION);
    expect(aProveedor({ id: 'prov-2', tiposExcluidos: [], ajustesDeIngesta: {} }).armaSets).toBe(
      false,
    );
    expect(aProveedor({ id: 'prov-2', tiposExcluidos: [], ajustesDeIngesta: {} }).local).toBe(false);
  });

  /** Los ajustes de Tenis Al Pie (ADR-0089) van y vuelven. */
  it('lee y manda los ajustes de ingesta', () => {
    const proveedor = aProveedor({
      id: 'prov-3',
      tiposExcluidos: [],
      ajustesDeIngesta: {
        unaFotoUnProducto: true,
        ventaBajoConfirmacion: true,
        diasDeVigencia: 5,
        tallasPorGenero: { dama: ['36', '37'], caballero: ['40', '41'], ajuste: 1 },
      },
    });

    expect(proveedor.ajustesDeIngesta).toEqual({
      unaFotoUnProducto: true,
      ventaBajoConfirmacion: true,
      diasDeVigencia: 5,
      tallasPorGenero: { dama: ['36', '37'], caballero: ['40', '41'], ajuste: 1 },
    });
    expect(aProveedorPeticion(proveedor).ajustesDeIngesta).toEqual({
      unaFotoUnProducto: true,
      ventaBajoConfirmacion: true,
      diasDeVigencia: 5,
      tallasPorGenero: { dama: ['36', '37'], caballero: ['40', '41'], ajuste: 1 },
    });
  });

  /** El servidor lo exige al crear y al editar: si la petición lo pierde, el cuerpo no se lee. */
  it('manda el orden en la petición', () => {
    const peticion = aProveedorPeticion({
      nombre: 'D’Osman',
      nombreEnExportacion: "D'Osman",
      telefonoWhatsApp: '573175025915',
      linea: 'BOLSOS',
      factorDeMargen: 1.38,
      ordenDePublicacion: 'FOTOS_PRIMERO',
      publicacionAutomatica: false,
      dosChatsEnUnZip: false,
      activo: true,
      tiposExcluidos: ['SHORT'],
      armaSets: false,
      ajustesDeIngesta: AJUSTES_DE_INGESTA_POR_OMISION,
      local: true,
    });

    expect(peticion.ordenDePublicacion).toBe('FOTOS_PRIMERO');
    expect(peticion.tiposExcluidos).toEqual(['SHORT']);
    expect(peticion.armaSets).toBe(false);
    expect(peticion.local).toBe(true);
  });
});
