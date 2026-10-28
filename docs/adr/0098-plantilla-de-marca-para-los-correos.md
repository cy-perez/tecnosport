# ADR-0098: los correos salen con la plantilla de marca, en HTML y en texto plano, con Reply-To

Fecha: 2026-10-27
Estado: aceptada.
Relacionados: `adr/0045` (la bandeja de salida), `adr/0025` (los textos fuera del código),
`docs/04-ui-marca.md`, `packages/marca/tokens.json`.

## Contexto

Los treinta y tantos correos del sistema eran fragmentos HTML sueltos —`<p>…</p>` resueltos desde
`correos_*.properties`— sin logo, sin estructura y sin pie. Un comprobante de compra se veía igual
que un mensaje escrito a mano en un cliente de correo de 2005, y nada en él decía, salvo el texto,
que venía de Tecno Sport.

Al revisarlo apareció además un defecto real: el remitente es `no-responder@tecnosport.co`, sin
`Reply-To`, y cuatro correos le pedían al cliente «respóndenos este correo». Uno es el acuse de
**retracto**, que pide el medio para el reintegro. Esas respuestas iban a un buzón que nadie lee.

## Decisión

1. **Una plantilla, en un solo sitio.** `resources/correo/plantilla.html` y `PlantillaDeCorreo`,
   en `infrastructure/correo`. `EnviadorDeCorreoBandejaDeSalida` envuelve el fragmento **al
   encolar**: la fila de la bandeja guarda el correo tal como sale, que es la constancia de lo que
   se le dijo a quien compró. Ningún caso de uso cambió. El precio: lo que ya estaba encolado al
   desplegar sale sin plantilla.
2. **El título sale del asunto**, sin el « - Tecno Sport» final; la vista previa, del comienzo
   del texto. Un párrafo que solo contiene un enlace se pinta como botón: la regla es por forma, así
   que un correo nuevo escrito igual sale igual sin marcar nada.
3. **La marca, del kit**: cabecera grafito con el logo en negativo, franja ámbar —el único
   elemento de acento—, botón grafito como el del sitio, fondos y bordes de `tokens.json`. Los
   colores van en HEX y en línea porque Gmail no lee variables CSS; `tools/verificar-correo.mjs`,
   dentro de `npm run verificar`, falla si alguno no es de `tokens.json`. Sin fuentes web: casi
   ningún cliente las carga y pedirlas a un tercero le dice quién abrió el correo.
4. **El logo es un PNG publicado por el sitio** (`/imagenes/correo/logo-tecnosport.png`, lo copia
   `copiar-marca.mjs`), con la URL armada sobre `APP_URL_PUBLICA`. Se descartó incrustarlo como
   adjunto `cid:`: algunos clientes lo muestran como adjunto, y pesa en cada correo.
5. **El pie** lleva el bloque de ayuda (WhatsApp, correo, horario), el sitio, las redes, los
   legales y el contacto, y la identificación del vendedor que pide la Ley 1480. Los textos viven
   en `correos_*.properties` (llaves `plantilla.*`), donde `npm run datos-negocio` ya mira el
   teléfono, el NIT, el correo y la dirección; desde hoy también las URL de las redes y de WhatsApp.
   Las rutas se pegan a `APP_URL_PUBLICA`: en dev enlazan a dev.
6. **Texto plano junto al HTML** (`multipart/alternative`), sacado del mismo HTML por
   `TextoPlanoDeCorreo` para que las dos versiones no puedan decir cosas distintas. A mano y no con
   una librería: el HTML de entrada es nuestro y usa seis etiquetas.
7. **`Reply-To: contacto@tecnosport.co`** (`CORREO_RESPONDER_A`), validado al arrancar.

Los correos internos —saldo bajo, revisión pendiente, sugerencias— llevan la misma plantilla.

## El saludo (añadido el mismo 27 de octubre)

Todos los correos abren con «Hola, María:» o, sin nombre, con «Hola:». `EnviadorDeCorreo` recibe el
nombre como argumento opcional; el método de tres argumentos queda como método por omisión sin
nombre, así que solo cambiaron los doce casos de uso que lo tienen: los del pedido, que pasan
`Pedido.nombreDeQuienRecibe()`. Del nombre se usa el primero, escapado.

**Es el nombre de quien recibe**, porque es el único que el checkout pide. Quien compra un regalo
recibe sus correos saludando a quien lo recibe: se aceptó, porque la mayoría compra para sí
misma. Pedir el nombre de quien compra es un cambio del checkout, del contrato y de la base, y no
entró aquí. Las cuentas, las PQR y los avisos internos no tienen nombre y saludan con «Hola:».

## Lo que queda fuera

- **El nombre de quien compra**, distinto del de quien recibe (ver arriba).
- **Modo oscuro propio.** La plantilla se declara clara (`color-scheme: light`); los clientes que
  invierten colores por su cuenta lo siguen haciendo.
- **Iconos de redes.** Enlaces de texto: no se rompen con las imágenes bloqueadas.

## Cómo se verificó

Pruebas de la plantilla y del texto plano sin Spring; la del transporte contra un Mailpit real
comprueba el `Reply-To` y la parte de texto. A ojo, en Mailpit: comprobante, despacho, verificación
y retracto, a 600 px y a 390 px, sin desplazamiento horizontal. El chequeo de compatibilidad de
Mailpit da 87 % de soporte completo y 1 % sin soporte —lo de siempre en correo: `opacity`, `<style>`
en algunos clientes—, y nada de eso rompe la lectura.
