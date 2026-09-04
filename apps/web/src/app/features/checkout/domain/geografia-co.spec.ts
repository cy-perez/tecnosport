import { DEPARTAMENTOS, MUNICIPIOS, municipiosDeDepartamento } from './geografia-co';

describe('geografia-co', () => {
  it('trae los 33 departamentos de la DIVIPOLA', () => {
    expect(DEPARTAMENTOS.length).toBe(33);
  });

  it('trae los 1122 municipios de la DIVIPOLA', () => {
    expect(MUNICIPIOS.length).toBe(1122);
  });

  it('cada municipio referencia un código de departamento real', () => {
    const codigosValidos = new Set(DEPARTAMENTOS.map((d) => d.codigo));
    expect(MUNICIPIOS.every((m) => codigosValidos.has(m.codigoDepartamento))).toBe(true);
  });
});

describe('municipiosDeDepartamento', () => {
  it('filtra los municipios de Antioquia, incluida Medellín', () => {
    const municipios = municipiosDeDepartamento('05');

    expect(municipios.every((m) => m.codigoDepartamento === '05')).toBe(true);
    expect(municipios.some((m) => m.codigo === '05001' && m.nombre === 'Medellín')).toBe(true);
  });

  it('un código de departamento inexistente no trae municipios', () => {
    expect(municipiosDeDepartamento('00')).toEqual([]);
  });
});
