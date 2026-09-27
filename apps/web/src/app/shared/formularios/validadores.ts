import { AbstractControl, ValidationErrors } from '@angular/forms';

/**
 * Los validadores de contenido que comparten los formularios públicos. Viven en `shared/` porque
 * los usan el registro, el checkout y las cinco pantallas de correo, y porque no saben nada de
 * ninguna funcionalidad: entran cadenas, salen errores.
 *
 * <p>Los cuatro comparten una decisión: <b>un campo vacío es válido aquí</b>. Quien dice que un
 * campo hace falta es `Validators.required`, y si estos también lo dijeran, un campo vacío tendría
 * dos errores y la pantalla tendría que desempatarlos para no decir "escribe un nombre válido"
 * cuando lo que pasa es que no hay nada escrito. Lo que sí rechazan es el campo con solo espacios,
 * que `required` deja pasar —solo mira el largo— y que llega al servidor como un nombre en blanco.
 */

/**
 * El correo. <b>Es a propósito la misma expresión que `CorreoElectronico.java`</b>, y esa copia es
 * el arreglo, no la deuda: mientras aquí corría `Validators.email` —que acepta `juan@correo`, sin
 * punto— y allá se exigía el punto, había una franja de correos que el formulario daba por buenos
 * y el servidor rechazaba con un 422. Quien escribía uno de esos leía "No se pudo crear la cuenta.
 * Intenta de nuevo." al final del formulario, sin saber qué campo corregir ni por qué, y reintentar
 * le iba a dar exactamente lo mismo.
 *
 * <p>Es laxa a propósito, igual que la del servidor: validar correos con una expresión estricta es
 * una carrera que no se gana —hay direcciones legales que ningún regex razonable admite— y el
 * único juez de verdad es el enlace de verificación que sale al buzón. Lo que atrapa son las
 * formas que no pueden ser un correo: sin arroba, sin dominio, sin punto en el dominio, o con
 * espacios.
 */
export function correoValido(control: AbstractControl): ValidationErrors | null {
  const valor = String(control.value ?? '');
  if (valor === '') {
    return null;
  }
  return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(valor.trim()) ? null : { correoInvalido: true };
}

// Letras de cualquier alfabeto (`\p{L}`) y las marcas diacríticas sueltas (`\p{M}`), que es como
// llegan las tildes cuando el teclado las compone en dos pulsaciones en vez de en un carácter ya
// precompuesto. Sin `\p{M}`, "José" tecleado en un Mac se rechaza y quien lo escribe no ve por qué.
const NOMBRE = /^[\p{L}\p{M}'’. -]+$/u;
const DIRECCION = /^[\p{L}\p{M}0-9 #.,\-°º/()]+$/u;
const BARRIO = /^[\p{L}\p{M}0-9 .,'’()-]+$/u;

/** ¿Cuántas letras tiene, ignorando espacios y signos? */
function letras(valor: string): number {
  return (valor.match(/\p{L}/gu) ?? []).length;
}

/**
 * El nombre de quien recibe. Va impreso en la guía de la transportadora y es el nombre por el que
 * el mensajero pregunta en la puerta, así que un `@#$%` ahí no es un dato feo: es una entrega que
 * no se puede hacer.
 *
 * <p>Admite el apóstrofo y el guion —"D'Angelo", "Ruiz-Mejía" son nombres, no errores— y el punto
 * de una inicial abreviada. Rechaza los dígitos: un nombre de persona no los lleva, y cuando
 * aparecen es que alguien se equivocó de campo.
 *
 * <p>Exige <b>dos letras</b> y no una. Con una pasaba "a", que no es un nombre y sí es lo que se
 * escribe para saltarse un campo obligatorio.
 */
export function nombreDePersona(control: AbstractControl): ValidationErrors | null {
  const valor = String(control.value ?? '');
  if (valor === '') {
    return null;
  }
  const limpio = valor.trim();
  return NOMBRE.test(limpio) && letras(limpio) >= 2 ? null : { nombreInvalido: true };
}

/**
 * La dirección de entrega. Aquí los dígitos y la almohadilla <b>hacen falta</b>: una dirección
 * colombiana es "Cra 43A #7-50 Apto 902" y prohibirle el `#` sería prohibir la dirección. Se
 * admiten también la coma, la barra —"Calle 10 Sur / Vereda El Salado"— y los dos signos de grado,
 * porque hay teclados que escriben el ordinal con `º` y otros con `°` y quien lo teclea no
 * distingue cuál le salió.
 *
 * <p>Lo que se rechaza es lo que no puede formar parte de una dirección: `@ $ % & * < > [ ] { }`,
 * la barra invertida, las comillas. Y se exige <b>al menos una letra</b>, porque una dirección que
 * son solo números y guiones no dice a dónde ir.
 */
export function textoDeDireccion(control: AbstractControl): ValidationErrors | null {
  const valor = String(control.value ?? '');
  if (valor === '') {
    return null;
  }
  const limpio = valor.trim();
  return DIRECCION.test(limpio) && letras(limpio) >= 1 ? null : { direccionInvalida: true };
}

/**
 * El barrio. Sigue siendo opcional —`Direccion.java` explica por qué y esto no lo cambia: un campo
 * vacío pasa—, pero si se escribe tiene que poder imprimirse en una guía.
 *
 * <p>Admite dígitos, porque los barrios los tienen ("20 de Julio", "Doce de Octubre"), y no admite
 * la almohadilla: eso ya es una dirección y va en el campo de al lado.
 */
export function nombreDeBarrio(control: AbstractControl): ValidationErrors | null {
  const valor = String(control.value ?? '');
  if (valor === '') {
    return null;
  }
  const limpio = valor.trim();
  return BARRIO.test(limpio) && letras(limpio) >= 1 ? null : { barrioInvalido: true };
}
