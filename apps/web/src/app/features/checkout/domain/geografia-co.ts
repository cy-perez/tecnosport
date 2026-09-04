import { DEPARTAMENTOS, Departamento, MUNICIPIOS, Municipio } from './geografia-co.datos';

export { DEPARTAMENTOS, MUNICIPIOS };
export type { Departamento, Municipio };

export function municipiosDeDepartamento(codigoDepartamento: string): readonly Municipio[] {
  return MUNICIPIOS.filter((municipio) => municipio.codigoDepartamento === codigoDepartamento);
}
