# ADR-0106: `develop` para dev y `main` para producción

Fecha: 2026-11-05
Estado: aceptada
Reemplaza: en ADR-0105 y en `docs/07-infra-gcp.md`, producción a demanda con el SHA y con
aprobación del ambiente; y `main` como la rama que despliega dev.

## Contexto

Hasta aquí todo entraba por `main`: al fusionar se desplegaba dev, y producción se promovía a mano
pasándole a `desplegar-prod.yml` el SHA completo de un commit que había pasado la salud en dev. Con
producción en vivo (5 de noviembre de 2026) eso tiene dos problemas: `main` no dice qué está
publicado —puede ir varios PR por delante de tecnosport.co—, y publicar exige buscar un SHA,
copiarlo y aprobar el ambiente, tres pasos manuales donde basta uno.

## Decisión

1. **Dos ramas protegidas con un papel cada una.** `develop` es lo que corre en dev; `main` es lo
   que corre en producción. `develop` es la rama por omisión, así que un PR nuevo apunta ahí.
2. **`feat/`, `fix/`, `refactor/`, `docs/`, `chore/` → `develop`, con fusión automática.**
   `fusion-automatica.yml` le pide a GitHub que fusione cuando las comprobaciones obligatorias de
   `develop` (`web` y `api`) queden en verde. Con commit de fusión, para que los commits de cada
   unidad coherente sobrevivan. El push a `develop` dispara `verificar` otra vez —dos PR verdes por
   separado pueden romper juntos—, los `recorridos` y `desplegar dev`.
3. **El PR de liberación `develop → main` se abre solo y se fusiona a mano.** Cuando dev queda sano,
   el trabajo `liberacion` de `desplegar-dev.yml` abre o actualiza ese PR con la lista de lo que
   entró. Fusionarlo es la decisión de publicar. `main` exige sobre la punta de `develop`: `web`,
   `api`, `construir, subir y desplegar` (dev quedó sano con ese SHA), `recorridos` y
   `origen · el PR a main viene de develop`.
4. **Al fusionar a `main`, producción se despliega sola, sin reconstruir.** Fusionar crea un commit
   que dev nunca vio. `desplegar-prod.yml` promueve el segundo padre —la punta de `develop`— y solo
   si el árbol de `main` es idéntico al suyo; si no, falla sin tocar nada. Así lo publicado es byte
   a byte lo que corrió en dev.
5. **El ambiente `produccion` deja de pedir aprobación** y conserva la política de solo `main`. La
   fusión ya es la decisión humana; dos clics por liberación no agregaban control, solo espera.
   La federación de producción sigue exigiendo ese ambiente desde `main`.
6. **Nada llega a `main` sin pasar por `develop`**, tampoco una corrección urgente: `origen-de-main`
   lo rechaza. La urgente va a `develop` y se libera enseguida.
7. **La fusión automática y el PR de liberación usan `TOKEN_FUSION`**, un token de acceso de grano
   fino acotado a este repositorio con Contenido y Pull requests en escritura. Con `GITHUB_TOKEN`,
   lo que el flujo fusiona no dispara `desplegar dev`, y el PR que abre no dispara `origen de main`:
   las dos cosas fallarían en silencio.

## Consecuencias

- **Las migraciones tienen que convivir con el código anterior más tiempo.** Dev migra cuando el
  cambio entra a `develop`; producción, cuando alguien libera, quizá días después, y migra antes de
  mover el tráfico. Toda migración se escribe para que la versión que sigue corriendo funcione con
  el esquema nuevo: primero se agrega, y lo que sobra se quita en una liberación posterior.
- **`develop → main` nunca con squash ni rebase.** Los dos dejan un commit sin segundo padre que
  producción se niega a promover, y además desalinean las historias: la liberación siguiente
  arrastraría conflictos.
- **`main` no se toca por su cuenta.** Si algo llegara a `main` sin pasar por `develop`, la
  liberación siguiente falla por árboles distintos hasta fusionar `main` en `develop`.
- **Una liberación vieja puede no encontrar sus imágenes.** El registro de dev borra lo de más de
  siete días salvo las tres últimas versiones; si el PR de liberación espera más que eso con
  despliegues nuevos encima, se libera la punta actual, que es lo que el PR ya muestra.
- **Volver atrás sigue siendo a demanda**: `desplegar producción` con el SHA de `develop` que se
  quiere poner, que tiene que haber llegado a `main`.
- **Infraestructura no cambia de forma de aplicarse.** Terraform se sigue aplicando a mano, y un PR
  que solo toca `infra/` también se libera, aunque redespliegue las mismas imágenes. `terraform plan`
  en la CI sigue pendiente (`docs/07-infra-gcp.md`).
- `TOKEN_FUSION` caduca. Cuando caduque, los PR a `develop` dejan de fusionarse solos y el PR de
  liberación deja de actualizarse; el trabajo `liberacion` falla en rojo, que es el aviso.
