# Instrucciones para asistentes de IA

## Contexto y fuentes

Proyecto universitario grupal Ica Stay. Leer `README.md`, `docs/architecture/architecture.md`, `docs/decisions/architecture-decisions.md` y la spec pertinente antes de modificar código.

- [Notion](https://app.notion.com/p/3e82918d49f4817f94f1c329d2ff194f?pvs=204): alcance, arquitectura conceptual, responsables y estado general.
- `docs/` en este repositorio: contratos técnicos, specs y decisiones implementables.
- Código y pruebas: comportamiento efectivo. Si discrepan, informar el conflicto y acordar la corrección; no asumir que uno invalida al otro.

## Arquitectura acordada

- Angular + TypeScript; núcleo modular Spring Boot + Security + JPA; PostgreSQL como fuente transaccional.
- Roles `USER`, `HOTEL_ADMIN`, `SUPER_ADMIN`. El administrador de hotel accede solo a recursos de sus hoteles autorizados; comprobar pertenencia en backend además del rol.
- Servicios periféricos previstos: `payment-service`, `billing-service`, `notification-service`.
- Redis: caché temporal; Ollama: generación local con modelo configurable; MongoDB: historial conversacional planificado. Incorporarlos por etapas, sin asumir que ya están implementados.
- El modelo de IA no accede directamente a la base de datos ni decide permisos. El núcleo valida identidad y selecciona datos autorizados antes de construir el contexto.

## Forma de trabajo

1. Delimitar la etapa y revisar su spec y criterios de aceptación. Si falta una spec, crear una breve con `docs/features/TEMPLATE.md`.
2. Revisar el código y advertir contradicciones concretas antes de alterar contratos o arquitectura.
3. Modificar solo lo necesario para el objetivo; justificar dependencias nuevas y documentar decisiones relevantes.
4. Actualizar pruebas pertinentes y ejecutar las validaciones disponibles; informar resultados y límites.
5. Resumir archivos modificados, decisiones y pendientes. No afirmar que una integración externa funciona sin verificarla.
6. No realizar commits, pushes, merges ni publicar cambios salvo instrucción del usuario para la acción correspondiente. No guardar secretos, credenciales ni datos personales en el repositorio.

Flujo orientativo: **spec → implementación → pruebas → revisión → commit**. Notion y la spec se actualizan cuando una decisión cambia.
