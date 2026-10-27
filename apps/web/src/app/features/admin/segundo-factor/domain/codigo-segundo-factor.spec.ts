import {
  esCodigoDeLaAplicacion,
  esCodigoDeLaAplicacionODeRespaldo,
  normalizarCodigo,
} from './codigo-segundo-factor';
import { agruparDeACuatro, codigosComoTexto } from './segundo-factor.model';

describe('código del segundo factor', () => {
  it('quita espacios y guiones', () => {
    expect(normalizarCodigo(' 123 456 ')).toBe('123456');
    expect(normalizarCodigo('abcd-efgh-ijkl-mnop')).toBe('abcdefghijklmnop');
  });

  it('el de la aplicación son seis dígitos, ni uno más ni uno menos', () => {
    expect(esCodigoDeLaAplicacion('123 456')).toBe(true);
    expect(esCodigoDeLaAplicacion('12345')).toBe(false);
    expect(esCodigoDeLaAplicacion('1234567')).toBe(false);
    expect(esCodigoDeLaAplicacion('12a456')).toBe(false);
  });

  it('el de respaldo son 16 caracteres Base32, en cualquier caso y con o sin guiones', () => {
    expect(esCodigoDeLaAplicacionODeRespaldo('ABCD-EFGH-IJKL-MNOP')).toBe(true);
    expect(esCodigoDeLaAplicacionODeRespaldo('abcdefghijklmnop')).toBe(true);
    expect(esCodigoDeLaAplicacionODeRespaldo('123456')).toBe(true);
    // El 0, el 1, el 8 y el 9 no existen en Base32: quien los escribe copió mal.
    expect(esCodigoDeLaAplicacionODeRespaldo('ABCD-EFGH-IJKL-MN01')).toBe(false);
    expect(esCodigoDeLaAplicacionODeRespaldo('ABCD-EFGH-IJKL')).toBe(false);
  });
});

describe('la clave y los códigos como texto', () => {
  it('agrupa la clave de cuatro en cuatro', () => {
    expect(agruparDeACuatro('JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP')).toBe(
      'JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP',
    );
    expect(agruparDeACuatro('ABCDEF')).toBe('ABCD EF');
  });

  it('un código por línea', () => {
    expect(codigosComoTexto(['AAAA-BBBB-CCCC-DDDD', 'EEEE-FFFF-GGGG-HHHH'])).toBe(
      'AAAA-BBBB-CCCC-DDDD\nEEEE-FFFF-GGGG-HHHH\n',
    );
  });
});
