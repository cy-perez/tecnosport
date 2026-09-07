import { clsx, type ClassValue } from 'clsx';
import { extendTailwindMerge } from 'tailwind-merge';

/**
 * `tailwind-merge` viene configurado para la escala por omisión de Tailwind, y
 * en este proyecto esa escala se borró y se reemplazó por los nombres de los
 * tokens de marca (`src/tailwind.css`). Sin enseñarle ese vocabulario, agrupa
 * mal, y de dos formas distintas — las dos silenciosas:
 *
 *  1. **Fusiona lo que no debe.** `font-medio` no está en su lista de pesos
 *     conocidos (espera `medium`, `bold`…), así que lo tomaba por una familia
 *     tipográfica, lo metía en el mismo grupo que `font-texto` y descartaba
 *     esta última. El botón perdió IBM Plex Sans y se pintaba en Arial.
 *     Encontrado recorriendo el sitio en el navegador: ninguna prueba de
 *     Vitest lo veía, porque la clase sí estaba en el componente — desaparecía
 *     al fusionar.
 *  2. **No fusiona lo que sí debe.** `leading-titulares`, `max-w-contenido` o
 *     `min-h-tactil` no los reconoce como valores de su grupo, así que quedan
 *     sueltos y dos de ellos sobreviven juntos. Ahí gana el orden del CSS
 *     compilado, no quien llama — que es justo la promesa que `cn` existe para
 *     cumplir.
 *
 * De ahí esta configuración. **Cuando se añada un token con nombre no numérico
 * hay que registrarlo aquí**, o vuelve el punto 2 sin avisar. Los colores no
 * hacen falta: `twMerge` agrupa `bg-*`, `text-*`, `border-*` y `outline-*` por
 * prefijo y acierta con `ts-*` sin ayuda (comprobado en `cn.spec.ts`).
 */
const FAMILIAS = ['texto', 'display'];
const PESOS = ['regular', 'medio', 'fuerte'];
const INTERLINEADOS = ['titulares', 'texto'];
const ANCHOS = ['contenido', 'formulario', 'formulario-lg', 'filtro'];

const fusionar = extendTailwindMerge({
  extend: {
    classGroups: {
      'font-family': [{ font: FAMILIAS }],
      'font-weight': [{ font: PESOS }],
      leading: [{ leading: INTERLINEADOS }],
      'max-w': [{ 'max-w': ANCHOS }],
      'min-h': [{ 'min-h': ['tactil'] }],
    },
  },
});

/**
 * Une clases de Tailwind resolviendo los conflictos a favor de la última.
 *
 * Existe por una sola razón: un componente de `shared/ui/` trae sus clases
 * base y quien lo usa puede querer cambiar una. Sin resolver conflictos,
 * `class="p-16"` sobre una base `p-24` deja las dos en el atributo y gana la
 * que el CSS haya puesto después — un resultado que depende del orden del
 * archivo compilado, no de lo que pidió quien llama.
 *
 * `clsx` primero, para aceptar condicionales y arreglos; la fusión después,
 * sobre la cadena ya plana.
 *
 * Resuelve conflictos; **no valida que la clase exista**. Una clase inventada
 * pasa intacta y no hace nada, porque Tailwind no genera CSS para una utilidad
 * que no existe. Eso solo se detecta leyendo el CSS compilado.
 */
export function cn(...clases: ClassValue[]): string {
  return fusionar(clsx(clases));
}
