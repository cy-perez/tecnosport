---
description: Cierra una fase verificando y actualizando la documentación
---

Vamos a cerrar la fase: $ARGUMENTS

1. Corre `npm run verificar` y repórtame el resultado sin adornos.
2. Dime qué decisiones tomamos durante la fase que no están escritas en `docs/`.
   Para cada una, propón el cambio de documento o el ADR nuevo.
3. **Verifica la navegación**: recorre el sitio desde la portada y confirma que
   cada pantalla nueva de la fase se alcanza con clics, sin teclear una URL
   (`docs/09-plan-de-arranque.md`, regla de cierre de fase). Las que dependen
   de un rol o de una sesión, con ese rol y esa sesión activos. Lista las que
   no se alcancen: no se cierra la fase hasta enlazarlas.
4. Dime qué quedó a medias, con `TODO` o con supuestos sin confirmar.
5. Actualiza la documentación afectada. Un documento que describe un proyecto que
   ya no existe envenena todo lo que venga después.
6. Propón el mensaje del commit de cierre.
