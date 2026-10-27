/**
 * Lo que el servidor acepta como código (ADR-0096): seis dígitos de la aplicación, o un código de
 * respaldo de 16 caracteres Base32 —se muestran `ABCD-EFGH-IJKL-MNOP`— en cualquier caso y con o
 * sin guiones. Se normaliza igual que allá para no rechazar aquí lo que allá entraría.
 */
const CODIGO_DE_LA_APLICACION = /^\d{6}$/;
const CODIGO_DE_RESPALDO = /^[A-Za-z2-7]{16}$/;

/** Sin espacios ni guiones: "123 456" copiado de la aplicación es un código bien escrito. */
export function normalizarCodigo(valor: string): string {
  return valor.replace(/[\s-]/g, '');
}

export function esCodigoDeLaAplicacion(valor: string): boolean {
  return CODIGO_DE_LA_APLICACION.test(normalizarCodigo(valor));
}

export function esCodigoDeLaAplicacionODeRespaldo(valor: string): boolean {
  const codigo = normalizarCodigo(valor);
  return CODIGO_DE_LA_APLICACION.test(codigo) || CODIGO_DE_RESPALDO.test(codigo);
}
