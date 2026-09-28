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

Consulta [la arquitectura](docs/architecture/architecture.md), [la estrategia de IA](docs/architecture/ai-architecture.md) y [las decisiones](docs/decisions/architecture-decisions.md). La arquitectura describe el diseño objetivo. El arranque de Spring Boot y PostgreSQL está en preparación; consulta [las instrucciones del backend](backend/README.md).

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

La carpeta `backend/` y la base local PostgreSQL ya están preparadas. Las demás carpetas se crearán al implementar sus componentes.

## Trabajo en equipo

1. Consultar [Notion: arquitectura y seguimiento](https://app.notion.com/p/3e82918d49f4817f94f1c329d2ff194f?pvs=204) para alcance, responsables y estado.
2. Consultar `docs/` y la spec de la feature antes de implementar. La [plantilla](docs/features/TEMPLATE.md) sirve para nuevas features.
3. Registrar cambios técnicos mediante Issues, ramas y PR en GitHub cuando el equipo acuerde ese flujo.
4. Actualizar documentación, probar, revisar y hacer commits acotados. Ver [AGENTS.md](AGENTS.md) para trabajo con IA.

## Estado de implementación

Primera etapa técnica: Maven, Spring Boot, Flyway y esquema inicial PostgreSQL. El proyecto aún no ofrece endpoints de negocio ni autenticación. El siguiente incremento mapeará entidades JPA y añadirá una primera operación del catálogo siguiendo las specs. El arranque y la migración requieren verificación en un entorno con JDK, Maven y Docker.
