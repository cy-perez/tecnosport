import { formatoComun } from './formato-de-fotograma';

const blob = (tipo: string): Blob => new Blob(['x'], { type: tipo });

describe('formatoComun', () => {
  it('devuelve el formato del set cuando todos coinciden', () => {
    expect(formatoComun([blob('image/webp'), blob('image/webp')])).toBe('image/webp');
    expect(formatoComun([blob('image/jpeg'), blob('image/jpeg')])).toBe('image/jpeg');
  });

  it('no acepta un formato que no se sube: el PNG en que cae toBlob sin WebP', () => {
    expect(formatoComun([blob('image/png')])).toBeNull();
  });

  it('no acepta un set mezclado: se firma con un solo tipo', () => {
    expect(formatoComun([blob('image/webp'), blob('image/jpeg')])).toBeNull();
  });

  it('un set vacío no tiene formato', () => {
    expect(formatoComun([])).toBeNull();
  });
});
