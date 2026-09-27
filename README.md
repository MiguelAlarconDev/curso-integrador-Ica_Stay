# Ica Stay

Plataforma web de reservas hoteleras orientada inicialmente a Ica. Proyecto grupal del Curso Integrador I – Software.

## Objetivo

Permitir a los huéspedes buscar habitaciones y realizar reservas; a cada hotel gestionar su oferta y reservas; y a la administración supervisar la plataforma. El asistente local responderá preguntas con datos que el backend autorice.

## Arquitectura

- Frontend: Angular y TypeScript.
- Núcleo modular: Spring Boot, Spring Security, Spring Data JPA y PostgreSQL.
- Servicios especializados: pagos, comprobantes y notificaciones.
- Integraciones progresivas: Redis para caché, Ollama para respuestas en lenguaje natural y MongoDB para historial de conversaciones.
- Roles: `USER`, `HOTEL_ADMIN` y `SUPER_ADMIN`.

Consulta [la arquitectura](docs/architecture/architecture.md), [la estrategia de IA](docs/architecture/ai-architecture.md) y [las decisiones](docs/decisions/architecture-decisions.md). La documentación refleja el diseño objetivo; todavía no hay aplicación ejecutable.

## Organización prevista

```text
frontend/                  Aplicación Angular
backend/                   Núcleo Spring Boot
services/payment-service/  Integración de pagos
services/billing-service/  Comprobantes
services/notification-service/  Correos y avisos
database/                  Migraciones y diagramas
ai/                        Prompts y configuración no secreta
docs/                      Arquitectura, specs y decisiones
```

Las carpetas de código se crearán al implementar cada componente.

## Trabajo en equipo

1. Consultar [Notion: arquitectura y seguimiento](https://app.notion.com/p/3e82918d49f4817f94f1c329d2ff194f?pvs=204) para alcance, responsables y estado.
2. Consultar `docs/` y la spec de la feature antes de implementar. La [plantilla](docs/features/TEMPLATE.md) sirve para nuevas features.
3. Registrar cambios técnicos mediante Issues, ramas y PR en GitHub cuando el equipo acuerde ese flujo.
4. Actualizar documentación, probar, revisar y hacer commits acotados. Ver [AGENTS.md](AGENTS.md) para trabajo con IA.

## Primer hito

Definir el modelo de datos, los contratos básicos de la API y el flujo mínimo de búsqueda → disponibilidad → reserva. Integrar servicios adicionales después de demostrar el núcleo.
