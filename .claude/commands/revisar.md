---
description: Revisión adversarial del último trabajo
---

Revisa $ARGUMENTS como un desarrollador senior que va a heredar este código y no
tiene paciencia.

Búscame, en este orden de gravedad:

1. Violaciones de las flechas de dependencia de `docs/01-arquitectura.md`.
2. Lógica de negocio filtrada a `presentation` o a `infrastructure`.
3. Uso de `double` o `float` para dinero, o redondeos intermedios.
4. Confianza en datos del cliente para precio, existencia, costo de envío,
   disponibilidad de método de pago o estado del pago.
5. Falta de idempotencia en algo que mueve dinero o inventario.
6. Valores literales que debían ser configuración o tokens de diseño.
7. Texto visible sin pasar por Transloco.
8. Uso de APIs del navegador sin guardia de plataforma o sin camino degradado.
9. Casos de error sin manejar y excepciones de infraestructura que se escapan.
10. Pruebas que pasarían igual con la implementación borrada.
11. Problemas de accesibilidad: foco eliminado, contraste, `alt` faltante,
    controles inalcanzables por teclado.
12. Secretos, credenciales o datos personales en el código o en los registros.

Dame la lista priorizada con archivo y línea. No arregles nada todavía.
