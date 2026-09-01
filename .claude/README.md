# .claude

Configuración de Claude Code para este proyecto.

```
commands/    comandos de barra, se invocan con /nombre
agents/      subagentes con contexto propio
settings.json permisos del proyecto. Se versiona.
```

`settings.local.json` guarda permisos personales y no se versiona.

## Comandos

| Comando | Para qué |
|---|---|
| `/nueva-funcionalidad` | Implementar algo respetando las capas, con plan previo |
| `/revisar` | Revisión adversarial de lo recién construido |
| `/cerrar-fase` | Verificar, actualizar documentación y proponer ADR |

## Agentes

| Agente | Para qué |
|---|---|
| `revisor-arquitectura` | Verifica capas, SOLID y convenciones del proyecto |
| `auditor-accesibilidad` | Audita WCAG y fidelidad al sistema visual |
| `revisor-pagos` | Revisa lo que toca dinero, inventario e idempotencia |

## Servidores MCP recomendados

Se configuran con `claude mcp add`.

| Servidor | Para qué | Precaución |
|---|---|---|
| PostgreSQL | Leer el esquema y explicar consultas | Solo lectura y solo contra la base local |
| GitHub | Crear pull requests, leer issues, revisar la integración continua | Token con el mínimo alcance |
| Playwright | Verificar en un navegador real la interfaz recién escrita | Solo en local |
| Documentación de librerías | Angular 22 y Spring Boot 4.1 actualizados | Es lo que evita que invente APIs de versiones viejas |

Nunca conectes un MCP con credenciales de producción. Si Claude puede escribir en
la base real, un malentendido cuesta datos.
