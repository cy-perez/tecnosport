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
    });

    expect(proveedor.ordenDePublicacion).toBe('TEXTO_PRIMERO');
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
    });

    expect(peticion.ordenDePublicacion).toBe('FOTOS_PRIMERO');
  });
});
