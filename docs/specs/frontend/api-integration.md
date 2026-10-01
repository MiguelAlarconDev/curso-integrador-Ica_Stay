# Ica Stay — Integración inicial Angular con API

## Estado
Acordado

## Objetivo

Preparar el frontend Angular 22 para consumir la API REST existente de Ica Stay desarrollada en Spring Boot.

Esta iteración establece la infraestructura HTTP y realiza una primera integración con los hoteles.

## Contexto

El proyecto contiene:

- `/backend`: Spring Boot
- `/frontend`: Angular 22

El backend ya implementa:

- usuarios
- autenticación JWT
- roles
- hoteles
- habitaciones
- reservas

El backend no debe modificarse salvo que se detecte un problema estrictamente necesario para permitir la comunicación con Angular. Si se detecta uno, reportarlo antes de cambiarlo.

## Alcance

Implementar:

- configuración de HttpClient;
- configuración centralizada de URL de API;
- modelos TypeScript necesarios;
- servicio para hoteles;
- consumo del endpoint real de hoteles;
- estados de carga;
- manejo básico de error.

No implementar todavía:

- login;
- registro;
- almacenamiento JWT;
- interceptor JWT;
- guards;
- reservas desde Angular;
- panel ADMIN_HOTEL;
- panel SUPERADMIN.

## Configuración HTTP

Configurar Angular usando los mecanismos actuales de Angular 22.

No utilizar módulos Angular antiguos cuando exista una API standalone actual equivalente.

`HttpClient` debe quedar disponible para la aplicación.

## URL del backend

No repetir URLs como:

`http://localhost:8080`

en distintos servicios.

Debe existir una configuración centralizada para la URL base de la API.

Para desarrollo local el backend se encuentra en:

`http://localhost:8080`

La solución debe quedar preparada para cambiar posteriormente la URL según el entorno.

## Estructura

Utilizar como base:

`frontend/src/app/core/`

Crear cuando corresponda:

- `core/models/`
- `core/services/`

Evitar sobrearquitectura.

## Hotel

Crear un modelo TypeScript que represente la respuesta REAL del backend.

No inventar propiedades.

Antes de definir el modelo, revisar el controlador, DTO o respuesta existente del backend.

## HotelService

Crear un servicio responsable del acceso HTTP relacionado con hoteles.

Debe utilizar `HttpClient`.

La URL debe construirse a partir de la configuración centralizada de API.

No colocar lógica visual dentro del servicio.

## Home

Conectar la sección existente:

"Hospedajes para descubrir Ica"

con el endpoint real de hoteles.

La Home debe conservar su diseño aprobado.

No rediseñar la página.

Los datos reales deben integrarse dentro de la composición editorial existente.

No convertir la sección en una cuadrícula genérica de cards.

## Fotografías

Las fotografías locales existentes pueden mantenerse temporalmente como imágenes editoriales/fallback mientras el backend no proporcione imágenes utilizables.

No eliminar las imágenes actuales únicamente porque los datos del hotel provengan de la API.

## Estados de interfaz

### Cargando

Mientras se consultan los hoteles:

- no bloquear toda la página;
- mostrar un estado discreto dentro de la sección correspondiente.

### Error

Si el backend no está disponible:

- la aplicación no debe romperse;
- mostrar un mensaje discreto;
- mantener utilizable el resto de la Home.

No mostrar stack traces ni errores técnicos al usuario.

### Sin hoteles

Si la API responde correctamente pero no existen hoteles:

mostrar un estado vacío apropiado.

## CORS

Si Angular no puede acceder al backend debido a CORS:

- identificar claramente el problema;
- reportar qué configuración del backend sería necesaria;
- no desactivar seguridad de forma global;
- no aplicar soluciones improvisadas.

## Calidad

- Mantener tipado TypeScript.
- Evitar `any` salvo que sea estrictamente necesario.
- No duplicar interfaces.
- No agregar librerías externas para HTTP.
- No modificar innecesariamente componentes existentes.

## Validación

Al finalizar:

1. `ng build` debe finalizar correctamente.
2. Las pruebas existentes deben continuar pasando.
3. Con backend activo, Angular debe poder obtener hoteles.
4. Con backend detenido, la Home debe seguir funcionando y mostrar el estado de error correspondiente.
5. No debe haber errores de TypeScript.

## Fuera de alcance

No implementar autenticación ni JWT en esta iteración.

## Resolución CORS

Durante la integración real se confirmó que Angular realiza correctamente:

GET http://localhost:8080/api/v1/hotels?page=0&size=20

El navegador bloquea actualmente la respuesta porque el backend no permite explícitamente el origen del frontend.

Para desarrollo local se debe permitir:

http://localhost:4200

La configuración debe:

- integrarse con Spring Security;
- permitir el origen exacto `http://localhost:4200`;
- permitir los métodos HTTP requeridos por la API;
- permitir headers necesarios, incluyendo `Authorization` para la posterior integración JWT;
- no utilizar `*` como origen;
- no desactivar CSRF/CORS como solución improvisada;
- mantener las reglas de autorización existentes;
- quedar preparada para configurar posteriormente otros orígenes según ambiente.