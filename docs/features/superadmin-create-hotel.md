# Creación de hotel y HOTEL_ADMIN — 7.3.2

**Estado:** implementado y aprobado manualmente por el responsable  
**Responsable:** por asignar  
**Actualizado:** 2026-10-16

## Objetivo y permisos

POST `/api/v1/superadmin/hotels`, exclusivo SUPER_ADMIN, crea hotel y cuenta nueva
HOTEL_ADMIN en una sola transacción. Ambos nacen ACTIVE. Sin JWT: 401; otros roles: 403.

## Contrato y validaciones

JSON: `{name, description, address, city, admin: {name, email, password}}`.
Hotel: nombre/dirección/ciudad obligatorios, recortados, máximos 180/255/120;
descripción opcional, recortada, máximo 5000; blanco se guarda null.
Administrador: nombre obligatorio recortado, máximo 160; correo obligatorio,
normalizado con trim y minúsculas, máximo 254, mismo patrón que registro público.
Contraseña inicial: mínimo 8 caracteres y máximo 72 bytes UTF-8 (límite BCrypt);
no se recorta. Solo se guarda hash BCrypt mediante PasswordEncoder existente.
Se reutilizan validadores de HotelService y AuthService sin cambiar sus contratos.
Campos adicionales se ignoran explícitamente: nunca se asignan rol, estado, ID
ni adminUserId enviados por cliente. La respuesta 201 es SuperadminHotelView
(los mismos ocho campos seguros del listado); no devuelve contraseña, hash ni JWT.

## Errores y atomicidad

400: cuerpo ausente/malformado o campos inválidos. 409: correo ya registrado,
incluyendo cuentas inactivas y cualquier rol; la restricción uq_users_email
resuelve carreras concurrentes. Restricción de asignación única preservada.
La transacción externa incluye las dos inserciones; cualquier fallo revierte ambas.
No hay reintento automático del POST. DTO de entrada con toString redactado;
errores de lectura JSON de este controlador se responden sin registrar el cuerpo.
El POST lee con el ObjectMapper configurado y transforma errores de parsing en
400 sin propagar su mensaje/causa: la resolución automática de argumentos MVC
puede registrar el valor inválido antes de un ExceptionHandler, incluso con DTO
redactado. El resto de endpoints mantiene su deserialización existente.

## Interfaz y criterios de aceptación

- `/superadmin/hotels/new`, guard SUPER_ADMIN y enlace desde el listado.
- Formularios reactivos, validaciones coherentes, confirmación de contraseña,
  mensajes por campo, error seguro 400/403/409/conexión, carga y doble envío bloqueado.
- Éxito: borra formulario, vuelve al listado sin filtros y muestra confirmación.
- No guarda credenciales en almacenamiento ni URL; limpia contraseña tras error.
- Pruebas: BCrypt, estados/rol, duplicados, validaciones, permisos, unicidad,
  rollback real y concurrencia; interfaz, guard y navegación.

## Fuera de alcance

Edición, desactivación, eliminación, asignación de cuentas existentes, envío de
credenciales y cambio obligatorio de contraseña. Sin migraciones ni dependencias.

## Validación ejecutada

- Backend: CreateHotelIntegrationTest, SuperadminHotelIntegrationTest,
  AdminHotelHttpIntegrationTest y AuthHttpIntegrationTest: 36 pruebas aprobadas
  sobre PostgreSQL 17.11, seleccionadas con -Dtest en Maven.
- Tras reforzar los errores JSON se repitió solo CreateHotelIntegrationTest:
  9 aprobadas. La comprobación ampliada detectó inicialmente una filtración en
  DEBUG; se corrigió y la repetición pasó. Verifica creación/DTO, BCrypt, acceso
  del administrador, validaciones, permisos, duplicados concurrentes, unicidad
  y rollback real. Se limpian los fixtures propios.
- Angular: create-hotel.spec.ts, hotels.spec.ts y register.spec.ts, seleccionados
  con --include y --watch=false: 51 pruebas aprobadas. Incluye doble envío,
  validaciones, errores, navegación, guard y confirmación.
- npm run build y git diff --check: correctos. Sin suites completas.
- Validación manual de 7.3.2 aprobada por el responsable antes de 7.5.
