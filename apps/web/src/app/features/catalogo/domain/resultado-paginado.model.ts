export interface ResultadoPaginado<T> {
  readonly items: readonly T[];
  readonly cursorSiguiente: string | null;
}
