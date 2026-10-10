import {
  ArchivoDeIngesta,
  formatearTamano,
  sePuedeBorrarArchivo,
} from './archivo-de-ingesta.model';

function archivo(cambios: Partial<ArchivoDeIngesta> = {}): ArchivoDeIngesta {
  return {
    id: 'a-1',
    proveedorId: 'p-1',
    nombreOriginal: 'chat.zip',
    tamanoBytes: 1024,
    subidoEn: '2026-10-10T15:00:00Z',
    borradoEn: null,
    lotes: 1,
    enUso: false,
    ...cambios,
  };
}

describe('archivo de ingesta', () => {
  it('se puede borrar el que sigue ahí y nadie está leyendo', () => {
    expect(sePuedeBorrarArchivo(archivo())).toBe(true);
    expect(sePuedeBorrarArchivo(archivo({ enUso: true }))).toBe(false);
    expect(sePuedeBorrarArchivo(archivo({ borradoEn: '2026-10-11T00:00:00Z' }))).toBe(false);
  });

  it('el tamaño sube de unidad en base 1024 y se escribe en el idioma de la pantalla', () => {
    expect(formatearTamano(900, 'es')).toBe('900 B');
    expect(formatearTamano(1536, 'es')).toBe('1,5 kB');
    expect(formatearTamano(18.4 * 1024 * 1024, 'es')).toBe('18,4 MB');
    expect(formatearTamano(18.4 * 1024 * 1024, 'en')).toBe('18.4 MB');
  });
});
