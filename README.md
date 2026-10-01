# Ica Stay

Plataforma web de reservas de alojamientos orientada inicialmente a Ica, Perú. Proyecto grupal del Curso Integrador I – Software.

## Objetivo

Permitir a los huéspedes explorar alojamientos y habitaciones, iniciar sesión y realizar reservas; a los hoteles gestionar su oferta y reservas; y a la administración supervisar la plataforma. El proyecto contempla además un asistente local que responderá preguntas utilizando únicamente información autorizada por el backend.

## Estado actual

Ica Stay ya cuenta con una base funcional de frontend y backend:

- Frontend Angular con identidad visual inspirada en Ica y diseño responsive.
- Página principal con catálogo de alojamientos consumido desde la API real.
- Backend Spring Boot conectado a PostgreSQL.
- Migraciones de base de datos administradas con Flyway.
- Persistencia mediante Spring Data JPA.
- API REST para consulta de hoteles.
- Flujo de reservas implementado en backend.
- Autenticación mediante JWT.
- Sesiones stateless con Spring Security.
- Roles `USER`, `HOTEL_ADMIN` y `SUPER_ADMIN`.
- Inicio y cierre de sesión desde Angular.
- Persistencia del JWT en `sessionStorage` y restauración de sesión en el frontend.
- Interceptor HTTP y guard preparados para endpoints protegidos y control por roles.
- CORS configurado para la integración local Angular ↔ Spring Boot.
- Pruebas automatizadas del backend: 47 tests actualmente validados.

## Arquitectura

### Frontend

- Angular 22
- TypeScript
- Arquitectura standalone
- Organización por `core/`, `features/` y `shared/`
- Comunicación con el backend mediante `HttpClient`
- Autenticación JWT e interceptor HTTP

### Backend

- Java
- Spring Boot
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- API REST

### Componentes previstos

La arquitectura continuará incorporando progresivamente:

- Servicio de pagos.
- Servicio de comprobantes/facturación.
- Servicio de notificaciones.
- Redis para caché.
- Ollama para el asistente local.
- MongoDB para información no relacional, como historial de conversaciones.

Consulta [la arquitectura](docs/architecture/architecture.md), [la estrategia de IA](docs/architecture/ai-architecture.md) y [las decisiones de arquitectura](docs/decisions/architecture-decisions.md).

## Estructura del repositorio

```text
frontend/                       Aplicación Angular
backend/                        API y núcleo Spring Boot
services/payment-service/       Integración de pagos (previsto)
services/billing-service/       Comprobantes (previsto)
services/notification-service/  Correos y avisos (previsto)
database/                       Recursos de base de datos
ai/                             Prompts y configuración no secreta
docs/                           Arquitectura, specs y decisiones
```

Las carpetas correspondientes a componentes futuros se incorporarán a medida que sean implementados.

## Ejecución local

### Requisitos

- Node.js y npm
- JDK compatible con el proyecto
- Maven
- PostgreSQL

### Backend

La configuración sensible se proporciona mediante variables de entorno. No se deben almacenar contraseñas ni secretos JWT en el repositorio.

Variables utilizadas durante el desarrollo local:

```text
DB_PASSWORD
JWT_SECRET
```

La base de datos local utiliza por defecto:

```text
jdbc:postgresql://localhost:5433/ica_stay
```

Desde `backend/`:

```bash
mvn spring-boot:run
```

Consulta también [las instrucciones específicas del backend](backend/README.md).

### Frontend

Desde `frontend/`:

```bash
npm install
npm start
```

La aplicación se sirve durante el desarrollo en:

```text
http://localhost:4200
```

y consume la API local del backend.

## Autenticación

El backend expone autenticación JWT mediante:

```text
POST /api/v1/auth/login
```

El JWT contiene la identidad y el rol del usuario. Actualmente se manejan los siguientes roles:

- `USER`: huésped/usuario de la plataforma.
- `HOTEL_ADMIN`: administración de un hotel.
- `SUPER_ADMIN`: administración general de Ica Stay.

No se almacenan contraseñas en el frontend. El token de sesión se mantiene en `sessionStorage` y el cierre de sesión elimina la sesión local.

## API implementada

Entre las operaciones disponibles actualmente se encuentran:

```text
GET  /api/v1/hotels
POST /api/v1/auth/login
POST /api/v1/reservations
```

El catálogo de hoteles es público. La creación de reservas está protegida y requiere un usuario con rol `USER`.

## Documentación y specs

Antes de implementar una funcionalidad se documenta su contrato y comportamiento esperado en `docs/`. Entre las specs actuales se encuentran las correspondientes a la página principal, integración con la API y autenticación.

La documentación de arquitectura representa tanto el estado actual como la evolución prevista del sistema; las funcionalidades futuras deben distinguirse de las ya implementadas.

## Trabajo en equipo

1. Consultar [Notion: arquitectura y seguimiento](https://app.notion.com/p/3e82918d49f4817f94f1c329d2ff194f?pvs=204) para alcance, responsables y estado.
2. Revisar `docs/` y la spec correspondiente antes de implementar.
3. Trabajar mediante ramas de Git para separar los incrementos principales.
4. Ejecutar pruebas y revisar los cambios antes de integrarlos en `main`.
5. Mantener la documentación sincronizada con el código.
6. Consultar [AGENTS.md](AGENTS.md) para las reglas de trabajo asistido por IA.

## Ramas de trabajo

El desarrollo utiliza `main` como rama integrada y ramas `feature/*` para incrementos específicos. Entre las ramas utilizadas durante la implementación se encuentran:

- `feature/bootstrap-spring-postgres`
- `feature/frontend`

Los cambios validados se integran posteriormente en `main`.

## Próximos incrementos

El desarrollo continuará con las vistas y flujos funcionales de alojamientos, habitaciones y reservas, seguido por los paneles correspondientes a `HOTEL_ADMIN` y `SUPER_ADMIN`. Posteriormente se incorporarán los servicios especializados y las integraciones de IA definidas en la arquitectura.
