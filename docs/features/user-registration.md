# Registro publico de usuarios

**Estado:** en implementacion  
**Responsable:** por asignar  
**Actualizado:** 2026-10-10

## Objetivo

Permitir que un huesped cree una cuenta publica en Ica Stay mediante el backend, sin emitir automaticamente una sesion JWT.

## Actores y permisos

| Actor | Puede hacer | Alcance de datos |
|---|---|---|
| Visitante | Registrarse como usuario | Solo sus datos enviados en el registro |

## Flujo principal

1. El visitante envia nombre, correo y contrasena a `POST /api/v1/auth/register`.
2. El backend valida y normaliza los datos.
3. El backend crea un usuario `USER` con estado `ACTIVE` y contrasena almacenada como hash BCrypt.
4. El backend responde `201 Created` con id, nombre, correo y rol.

## Alternativas y errores

- Datos obligatorios ausentes, correo invalido, longitudes fuera de contrato o contrasena menor a 8 caracteres devuelven `400 Bad Request`.
- Un correo ya registrado, sin distinguir mayusculas/minusculas, devuelve `409 Conflict`.
- La unicidad se refuerza con la restriccion de base de datos para evitar duplicados bajo concurrencia.

## Datos y contratos

Endpoint:

`POST /api/v1/auth/register`

Request:

```json
{
  "name": "Usuario de Prueba",
  "email": "nuevo@icastay.com",
  "password": "ClaveSegura123!"
}
```

Response `201 Created`:

```json
{
  "id": "uuid-generado",
  "name": "Usuario de Prueba",
  "email": "nuevo@icastay.com",
  "role": "USER"
}
```

El cliente no puede seleccionar rol ni estado. El backend asigna siempre `USER` y `ACTIVE`.

## Estados

- Exito: usuario persistido y respuesta publica creada.
- Error de validacion: `400`.
- Conflicto por correo duplicado: `409`.

## Criterios de aceptacion

- [ ] Registro exitoso devuelve `201 Created`.
- [ ] La contrasena se almacena solo como BCrypt.
- [ ] El correo se normaliza a minusculas y se valida como unico.
- [ ] El rol creado es siempre `USER`.
- [ ] El endpoint es publico.
- [ ] Login posterior funciona con el usuario registrado.
- [ ] Endpoints protegidos existentes mantienen sus reglas.

## Fuera de alcance de esta etapa

- Registro de administradores.
- Emision automatica de JWT tras registrarse.
- Recuperacion de contrasena o verificacion de correo.

## Decisiones y pendientes

- 2026-10-15: se incorpora `/register` publico en Angular con formulario reactivo (nombre maximo 160, correo maximo 254, password minimo 8 y confirmacion coincidente). Solo se envian `name`, `email` y `password`. Se manejan 400, 409, errores de conexion y errores generales sin exponer detalles internos.
- Tras 201 se navega a `/login?registered=1` para mostrar una confirmacion informativa; no se persisten credenciales ni se inicia sesion. Login y registro enlazan entre si. Se reutilizan los estilos editoriales de Login y se excluye registro del Bearer por tratarse de un endpoint publico independiente de la sesion existente.

- 2026-10-15: se permiten los despachos internos `DispatcherType.ERROR` en Spring Security para conservar los errores 400/409 de registro. Las solicitudes externas a `/error` siguen sujetas a autenticacion; no se agrega una excepcion publica por URL.
- La regresion se verifica con `AuthHttpIntegrationTest`, servidor embebido en puerto aleatorio y cliente HTTP real sin autenticacion. Incluye registro 201, duplicado 409, datos invalidos 400, login incorrecto 401 y proteccion de reservas y `/error`. Los usuarios temporales se eliminan tras cada prueba.

- Se reutiliza la tabla `users` existente; no se requiere migracion nueva.
- Queda pendiente sincronizar la spec frontend de autenticacion cuando se implemente la pantalla de registro.

Referencias: [arquitectura](../architecture/architecture.md) | [registro de decisiones](../decisions/architecture-decisions.md) | [Notion](https://app.notion.com/p/3e82918d49f4817f94f1c329d2ff194f?pvs=204)
