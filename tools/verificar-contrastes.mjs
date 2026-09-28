#!/usr/bin/env node
// Comprueba el contraste de los pares de color que el sitio usa de verdad,
// en tema claro y en oscuro, contra WCAG 2.2 nivel AA — el mínimo que exige
// `docs/04-ui-marca.md`.
//
// Lee `packages/marca/tokens.css`, que es la fuente de verdad generada desde
// `tokens.json`. Los pares se declaran abajo a mano y a propósito: la lista es
// lo que hay que revisar cuando alguien inventa una combinación nueva.
import { readFileSync } from "node:fs";

const UMBRAL_TEXTO = 4.5; // texto normal
const UMBRAL_GRANDE = 3.0; // texto grande y componentes de interfaz

const css = readFileSync("packages/marca/tokens.css", "utf8");

/** Lee un bloque `:root { ... }` o `[data-tema="oscuro"] { ... }`. */
function leerBloque(selector) {
  const i = css.indexOf(selector);
  const abre = css.indexOf("{", i);
  const cierra = css.indexOf("}", abre);
  const cuerpo = css.slice(abre + 1, cierra);
  const vars = {};
  for (const m of cuerpo.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    vars[m[1].trim()] = m[2].trim();
  }
  return vars;
}

const claro = leerBloque(":root");
const oscuro = { ...claro, ...leerBloque('[data-tema="oscuro"]') };

function aRgb(hex) {
  const h = hex.replace("#", "").trim();
  const n = h.length === 3 ? h.split("").map((c) => c + c).join("") : h;
  return [0, 2, 4].map((i) => parseInt(n.slice(i, i + 2), 16));
}

function luminancia(hex) {
  const [r, g, b] = aRgb(hex).map((v) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contraste(a, b) {
  const [x, y] = [luminancia(a), luminancia(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
}

// Cada par es [frente, fondo, umbral, dónde se usa].
const PARES = [
  ["--color-texto", "--color-fondo", UMBRAL_TEXTO, "texto sobre el lienzo"],
  ["--color-texto", "--color-superficie", UMBRAL_TEXTO, "texto sobre tarjeta"],
  ["--color-texto", "--color-superficie-alt", UMBRAL_TEXTO, "texto sobre superficie elevada"],
  // `primario-suave` lleno las baldosas de linea entre el 26 y el 28 de septiembre de 2026, y su
  // par sigue aqui aunque hoy no lo pinte nadie: es el unico token del kit que cambia de familia
  // con el tema —gris casi neutro en claro, ambar apagado en oscuro— y el dia que vuelva a usarse
  // lo va a hacer sin que nadie se acuerde de comprobar que el texto encima se lee.
  ["--color-texto", "--color-primario-suave", UMBRAL_TEXTO, "relleno tenue de marca (sin uso hoy)"],
  ["--color-texto-suave", "--color-fondo", UMBRAL_TEXTO, "texto secundario sobre el lienzo"],
  ["--color-texto-suave", "--color-superficie", UMBRAL_TEXTO, "marca y SKU en la tarjeta"],
  ["--color-texto-suave", "--color-superficie-alt", UMBRAL_TEXTO, "etiqueta de agotado"],
  ["--color-sobre-primario", "--color-primario", UMBRAL_TEXTO, "botón primario"],
  ["--color-sobre-primario", "--color-primario-hover", UMBRAL_TEXTO, "botón primario en hover"],
  ["--color-primario", "--color-superficie", UMBRAL_TEXTO, "botón secundario y enlaces"],
  ["--color-primario", "--color-fondo", UMBRAL_TEXTO, "enlaces sobre el lienzo"],
  ["--color-sobre-acento", "--color-acento", UMBRAL_TEXTO, "CTA ámbar y contador del carrito"],
  // Los cuatro pasos de la escala de ámbar. Van con el mismo `sobre-acento` que el tono 1 y no con
  // uno propio, así que lo que hay que vigilar es justo eso: que el grafito siga alcanzando en el
  // tono más claro. Aclarar solo puede subir el contraste contra un texto oscuro, pero el día que
  // la escala cambie de dirección el guardián lo dirá aquí en vez de en el navegador.
  // **Hoy solo se pinta el tono 5**, que es el relleno de las baldosas de línea desde `ADR-0065`;
  // el 2, el 3 y el 4 quedaron sin uso al revertirse `ADR-0063` y se siguen vigilando porque el
  // generador los deriva igual y el día que vuelvan nadie va a mirar esto.
  ["--color-sobre-acento", "--color-acento-2", UMBRAL_TEXTO, "escala de ámbar, tono 2 (sin uso hoy)"],
  ["--color-sobre-acento", "--color-acento-3", UMBRAL_TEXTO, "escala de ámbar, tono 3 (sin uso hoy)"],
  ["--color-sobre-acento", "--color-acento-4", UMBRAL_TEXTO, "escala de ámbar, tono 4 (sin uso hoy)"],
  ["--color-sobre-acento", "--color-acento-5", UMBRAL_TEXTO, "texto de las baldosas de línea"],
  ["--color-sobre-marca", "--color-marca", UMBRAL_TEXTO, "pie, franjas de marca y pista del visor 360"],
  ["--color-error", "--color-fondo", UMBRAL_TEXTO, "mensajes de error"],
  ["--color-error", "--color-superficie", UMBRAL_TEXTO, "error dentro de un formulario"],
  // La fila expandida del panel de pedidos vive sobre la superficie elevada, y ahí pinta en rojo el
  // plazo de entrega vencido. Faltaba el par: el guardián miraba el rojo sobre fondo y sobre
  // superficie, pero no sobre esta.
  ["--color-error", "--color-superficie-alt", UMBRAL_TEXTO, "plazo vencido en la fila expandida"],
  ["--color-exito", "--color-superficie-alt", UMBRAL_TEXTO, "etiqueta de disponible"],
  // La insignia de estado de la lista de productos del panel, que vive dentro de una tarjeta: verde
  // como texto y como borde sobre `--color-superficie`. Faltaba el par — se miraba el verde sobre
  // superficie-alt y sobre el lienzo, pero no sobre la superficie elevada, que es donde vive la
  // unica pieza que lo usa como linea.
  ["--color-exito", "--color-superficie", UMBRAL_TEXTO, "insignia de publicado en la lista del panel"],
  // Los cuatro acuses del panel —publicado, retirado, existencia ajustada, medida corregida—
  // pintan en verde directamente sobre el lienzo, no sobre una superficie. El guardian solo
  // miraba el verde sobre superficie-alt, asi que ese par no lo vigilaba nadie.
  ["--color-exito", "--color-fondo", UMBRAL_TEXTO, "acuses del panel sobre el lienzo"],
  // El borde de las cajas de confirmacion en linea, que es lo unico que las separa de la fila de
  // arriba. `--color-borde` da 1,19:1 sobre el lienzo y era invisible.
  ["--color-borde-control", "--color-fondo", UMBRAL_GRANDE, "borde de una caja de confirmacion"],
  // El borde de hover de las baldosas de la portada, contra su propio relleno — que es lo que hay
  // que mirar desde `ADR-0065`, porque la baldosa ya no es del color del lienzo. Fue
  // `primario` contra `borde` mientras el relleno era `primario-suave`, y ese par se quedo al reves
  // en tema oscuro sin que nadie lo viera: `primario` es ambar ahi, asi que el hover sobre un
  // relleno amarillo *borraba* el borde en lugar de marcarlo. `sobre-acento` es grafito en los dos
  // temas. El borde en reposo es `acento` sobre `acento-5` y NO se declara aqui a proposito: 1,5:1,
  // es un filo decorativo y no una afordancia, y declararlo seria pedirle al guardian que falle por
  // algo que nadie tiene que poder leer.
  ["--color-sobre-acento", "--color-acento-5", UMBRAL_GRANDE, "borde de hover de las baldosas de linea"],
  ["--color-aviso", "--color-fondo", UMBRAL_TEXTO, "plazo de la transferencia"],
  ["--color-sobre-primario", "--color-error", UMBRAL_TEXTO, "botón de peligro y aviso bloqueante"],
  ["--color-sobre-primario", "--color-exito", UMBRAL_TEXTO, "aviso de éxito de la captura"],
  ["--color-sobre-deshabilitado", "--color-deshabilitado", UMBRAL_TEXTO, "botón deshabilitado"],
  // Componentes de interfaz: borde y anillo de foco. Umbral de 3:1.
  ["--color-borde-control", "--color-superficie", UMBRAL_GRANDE, "borde de un campo"],
  ["--color-foco", "--color-fondo", UMBRAL_GRANDE, "anillo de foco sobre el lienzo"],
  ["--color-foco", "--color-superficie", UMBRAL_GRANDE, "anillo de foco sobre tarjeta"],
  ["--color-sobre-marca", "--color-marca", UMBRAL_GRANDE, "anillo de foco sobre el pie"],
  ["--color-sobre-acento", "--color-acento", UMBRAL_GRANDE, "anillo de foco sobre ámbar"],
];

let fallos = 0;
for (const [nombre, tema] of [["claro", claro], ["oscuro", oscuro]]) {
  console.log(`\n== tema ${nombre} ==`);
  for (const [frente, fondo, umbral, donde] of PARES) {
    const a = tema[frente];
    const b = tema[fondo];
    if (!a || !b || !a.startsWith("#") || !b.startsWith("#")) continue;
    const ratio = contraste(a, b);
    const pasa = ratio >= umbral;
    if (!pasa) fallos++;
    const marca = pasa ? "ok  " : "FALLA";
    console.log(
      `${marca} ${ratio.toFixed(2).padStart(6)}:1  (min ${umbral})  ${donde}` +
        `  [${frente.replace("--color-", "")} sobre ${fondo.replace("--color-", "")}]`,
    );
  }
}

console.log(`\npares que no alcanzan el mínimo: ${fallos}`);
process.exitCode = fallos > 0 ? 1 : 0;
