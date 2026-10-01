# Ica Stay — Autenticación frontend

## Estado

Acordado

## Objetivo

Implementar la autenticación del frontend Angular 22 utilizando el sistema JWT existente en el backend Spring Boot.

La autenticación debe integrarse con la identidad visual existente de Ica Stay y respetar estrictamente los contratos reales del backend.

Spring Security continúa siendo la autoridad final para autenticación y autorización. Las comprobaciones realizadas en Angular tienen como objetivo gestionar sesión, navegación y experiencia de usuario, pero no sustituyen la seguridad del backend.

---

## Contexto

Actualmente funcionan:

- Angular 22.
- Spring Boot.
- PostgreSQL.
- Integración Angular ↔ API.
- CORS para `http://localhost:4200`.
- Spring Security.
- Autenticación JWT en backend.
- Roles del sistema.
- Endpoint público de hoteles.
- Home consumiendo información real del backend.

No modificar los contratos existentes del backend para adaptarlos al frontend.

---

## Contrato confirmado del backend

### Login

Endpoint:

`POST /api/v1/auth/login`

Content-Type:

`application/json`

Request:

```json
{
  "email": "usuario@example.com",
  "password": "contraseña"
}
```

El backend normaliza el email eliminando espacios exteriores y convirtiéndolo a minúsculas.

Response HTTP 200:

```json
{
  "token": "<JWT>",
  "tokenType": "Bearer"
}
```

El backend no devuelve:

- objeto de usuario;
- nombre del usuario;
- rol separado;
- `expiresIn`;
- refresh token.

El frontend debe obtener la información disponible de sesión a partir de los claims del JWT.

---

## JWT

El JWT es generado exclusivamente por Spring Boot.

El frontend:

- no genera JWT;
- no modifica JWT;
- no intenta validar criptográficamente su firma;
- no simula autenticación.

El token utiliza HS256 en el backend.

### Claims disponibles

El JWT contiene:

- `sub`: email del usuario.
- `userId`: UUID del usuario como string.
- `role`: rol del usuario.
- `iat`: fecha de emisión en segundos Unix.
- `exp`: fecha de expiración en segundos Unix.

El frontend puede leer estos claims para mantener su estado local.

La lectura local de claims no demuestra que la firma sea válida y no sustituye la validación realizada por Spring Security.

---

## Expiración

La duración actual del JWT es:

`3600 segundos`

equivalente a:

`1 hora`

El token debe considerarse expirado cuando:

`fecha actual >= exp`

No existe renovación automática.

Cuando el token expire, el usuario deberá iniciar sesión nuevamente.

---

## Roles oficiales

Los únicos roles válidos del sistema son:

- `USER`
- `HOTEL_ADMIN`
- `SUPER_ADMIN`

Estos nombres deben utilizarse exactamente en Angular.

No utilizar:

- `ADMIN`
- `ADMIN_HOTEL`
- `SUPERADMIN`
- `ROLE_USER`
- `ROLE_HOTEL_ADMIN`
- `ROLE_SUPER_ADMIN`

El prefijo `ROLE_` es utilizado internamente por Spring Security y no forma parte del valor almacenado en el JWT.

---

## Registro

El backend no implementa actualmente un endpoint de registro.

Por tanto, en esta iteración:

- no crear `/register`;
- no crear `/signup`;
- no crear formulario de registro;
- no simular registro desde Angular.

El registro podrá implementarse en una iteración posterior si se añade el contrato correspondiente al backend.

---

## Refresh token

El backend no implementa refresh token.

No existen:

- refresh token;
- endpoint de renovación;
- renovación silenciosa de sesión.

Al expirar el JWT será necesario iniciar sesión nuevamente.

No inventar mecanismos de refresh en el frontend.

---

## Logout

El backend no implementa un endpoint propio de logout.

El logout será realizado exclusivamente en el cliente.

Debe eliminar:

- JWT almacenado;
- estado local de autenticación.

Un JWT emitido anteriormente puede continuar siendo técnicamente válido en el backend hasta alcanzar su fecha de expiración.

El frontend no debe afirmar que el token fue revocado.

---

## Alcance de esta iteración

Implementar:

- página Login;
- modelos TypeScript de autenticación;
- AuthService;
- envío real de credenciales;
- recepción del JWT;
- almacenamiento de sesión;
- restauración de sesión;
- lectura tipada de claims;
- comprobación local de expiración;
- interceptor HTTP para Bearer token;
- estado global básico de autenticación;
- logout local;
- actualización del Header;
- guard de autenticación;
- soporte inicial de roles;
- pruebas correspondientes.

No implementar todavía:

- registro;
- recuperación de contraseña;
- refresh token;
- panel `HOTEL_ADMIN`;
- panel `SUPER_ADMIN`;
- gestión de hoteles desde Angular;
- flujo completo de reservas;
- pagos.

---

## Modelos TypeScript

Crear los modelos necesarios basándose exclusivamente en el contrato real.

Como mínimo:

### LoginRequest

Debe representar:

```json
{
  "email": "string",
  "password": "string"
}
```

### LoginResponse

Debe representar:

```json
{
  "token": "string",
  "tokenType": "Bearer"
}
```

### AuthSession

Debe representar únicamente la información necesaria de una sesión válida obtenida del JWT.

Puede incluir:

- token;
- email;
- userId;
- role;
- issuedAt;
- expiresAt.

No duplicar información innecesariamente.

---

## Persistencia de sesión

En esta iteración se utilizará:

`sessionStorage`

Persistir únicamente el JWT.

No almacenar:

- contraseña;
- secretos;
- credenciales completas;
- información sensible adicional innecesaria.

Utilizar una clave claramente identificable con Ica Stay.

Ejemplo conceptual:

`icastay.auth.token`

El nombre exacto puede definirse durante la implementación.

---

## Restauración de sesión

Al iniciar Angular:

1. comprobar si existe JWT en `sessionStorage`;
2. intentar leer su estructura;
3. extraer claims requeridos;
4. comprobar el rol;
5. comprobar `exp`;
6. restaurar la sesión únicamente cuando los datos sean coherentes.

Eliminar el token almacenado cuando:

- esté malformado;
- no pueda decodificarse;
- falten claims requeridos;
- el rol sea desconocido;
- haya expirado.

La restauración local no valida la firma del JWT.

El backend sigue siendo la autoridad sobre la validez real del token.

---

## AuthService

Crear dentro de:

`core/services/`

Responsabilidades:

- realizar login;
- realizar logout local;
- almacenar JWT;
- restaurar sesión;
- exponer estado de autenticación;
- exponer token vigente;
- exponer email;
- exponer userId;
- exponer rol;
- comprobar roles.

Preferir signals para el estado de autenticación cuando encaje con la arquitectura Angular actual.

Exponer estado de solo lectura cuando sea posible.

No mezclar innecesariamente:

- presentación;
- mensajes visuales;
- navegación;
- componentes.

---

## Utilidad JWT

Crear una utilidad pequeña y tipada para leer el payload del JWT.

No agregar una dependencia externa únicamente para decodificar el token.

Debe poder interpretar:

- `sub`;
- `userId`;
- `role`;
- `iat`;
- `exp`.

Debe rechazar tokens estructuralmente inválidos.

No implementar validación criptográfica de la firma en Angular.

---

## Interceptor HTTP

Crear un interceptor funcional utilizando las APIs actuales de Angular 22.

Registrar mediante la configuración standalone actual.

Cuando exista una sesión válida, agregar:

`Authorization: Bearer <token>`

El Bearer debe añadirse únicamente a solicitudes correspondientes a la API de Ica Stay.

No añadir Authorization a:

- imágenes;
- archivos estáticos;
- recursos externos;
- dominios diferentes;
- endpoint de login.

No utilizar `withCredentials`, ya que la autenticación utiliza explícitamente el header Authorization.

---

## Manejo global de 401 y 403

### 401

Si una petición autenticada utilizando el token vigente responde `401`:

- limpiar la sesión local;
- eliminar el JWT almacenado.

Esto indica que el token ya no puede utilizarse como autenticación válida.

El `401` producido específicamente por credenciales incorrectas durante `/auth/login` debe ser manejado por el Login y no confundirse con expiración de una sesión existente.

### 403

Un `403` representa falta de autorización para el recurso solicitado.

Ante `403`:

- conservar la sesión;
- no eliminar automáticamente el JWT;
- permitir que la interfaz informe que el usuario no tiene permisos.

---

## Guard de autenticación

Crear un guard funcional utilizando las APIs actuales de Angular.

Cuando una ruta requiera autenticación y no exista una sesión local vigente:

- impedir la navegación;
- redirigir a `/login`.

El guard debe quedar preparado para comprobar roles permitidos en futuras rutas.

No crear dashboards ficticios únicamente para probar guards.

Los guards mejoran navegación y UX.

No sustituyen las reglas de Spring Security.

---

## Login

Crear ruta:

`/login`

Debe conservar la identidad visual:

`Ica contemporáneo`

y mantener coherencia con la Home existente.

### Evitar

No utilizar:

- card genérica flotante centrada;
- gradientes azul/morado;
- glassmorphism;
- neomorphism;
- ilustraciones SaaS;
- sombras exageradas;
- grandes bordes redondeados;
- diseño típico generado por IA.

Preferir una composición editorial relacionada visualmente con la Home.

---

## Formulario de Login

Campos:

- email;
- contraseña.

Ambos campos deben tener:

- label visible;
- validación;
- accesibilidad;
- estados de foco coherentes con `styles.css`.

Validar antes de enviar:

- email no vacío;
- formato razonable de email;
- contraseña no vacía.

No enviar formularios evidentemente inválidos al backend.

---

## Estados del Login

### Estado inicial

Formulario disponible.

### Enviando

Mientras se procesa el login:

- evitar envíos duplicados;
- mostrar un estado discreto;
- no bloquear innecesariamente toda la página.

### Credenciales incorrectas

Ante HTTP `401` del endpoint de login mostrar un mensaje propio del frontend, por ejemplo:

`El correo o la contraseña no son correctos.`

No depender del texto interno enviado por Spring Boot.

### Error de conexión

Mostrar un mensaje comprensible cuando el backend no esté disponible.

No mostrar:

- stack traces;
- excepciones Java;
- información técnica innecesaria.

---

## Login correcto

Después de recibir HTTP `200`:

1. obtener `token`;
2. comprobar estructura básica;
3. leer claims;
4. almacenar JWT;
5. actualizar estado global;
6. navegar a `/`.

No crear dashboards todavía.

---

## Header

Actualmente el Header muestra:

`Iniciar sesión`

### Sin sesión

Mantener:

`Iniciar sesión`

como acceso a `/login`.

### Con sesión

Sustituirlo por una representación discreta de la sesión.

Como el backend no proporciona nombre del usuario, utilizar el email disponible en `sub`.

Incluir una acción:

`Cerrar sesión`

El logout debe:

- eliminar el JWT;
- limpiar estado;
- actualizar inmediatamente el Header.

No rediseñar completamente el Header.

Mantener su estética actual.

---

## Navegación

Rutas públicas actuales:

- `/`
- `/login`

Las futuras rutas protegidas utilizarán el guard.

Después de login correcto:

- navegar a `/`.

Después de logout:

- mantener o regresar a una ruta pública razonable.

No crear rutas ficticias para paneles todavía.

---

## Endpoint protegido disponible

Actualmente existe:

`POST /api/v1/reservations`

Requiere:

`USER`

Comportamiento de seguridad conocido:

| Situación | Resultado |
|---|---|
| Sin JWT | `401` |
| JWT inválido | `401` |
| JWT válido `USER` con body inválido | supera seguridad y puede responder `400` |
| JWT `HOTEL_ADMIN` | `403` |
| JWT `SUPER_ADMIN` | `403` |

No crear reservas reales únicamente para probar autenticación.

Las pruebas automatizadas no deben producir reservas como efecto secundario.

---

## Endpoints públicos relevantes

Actualmente son públicos:

- `POST /api/v1/auth/login`
- `GET /api/v1/hotels`
- `GET /api/v1/hotels/**`
- `/actuator/health`

Los endpoints públicos de hoteles no sirven para demostrar por sí solos que un Bearer fue aceptado.

---

## CORS

La configuración actual permite desde:

`http://localhost:4200`

los métodos:

- GET;
- POST;
- PUT;
- PATCH;
- DELETE;
- OPTIONS.

Y los headers:

- `Content-Type`;
- `Authorization`;
- `Accept`.

Por tanto, el frontend puede enviar:

`Authorization: Bearer <token>`

sin utilizar `withCredentials`.

---

## Errores

### 401 en Login

Interpretar como credenciales inválidas y mostrar un mensaje comprensible.

### 401 en petición autenticada

Limpiar sesión cuando corresponda al token vigente.

### 403

Mantener sesión.

Informar que el usuario no posee permisos cuando corresponda.

### Error de red

Mantener la aplicación utilizable.

Mostrar un mensaje comprensible.

No mostrar detalles internos.

---

## Seguridad

No registrar JWT en consola.

No registrar contraseñas.

No almacenar contraseñas.

No incluir secretos en el repositorio.

No validar firmas JWT en el frontend.

No confiar en roles del frontend como mecanismo real de autorización.

No debilitar Spring Security.

No modificar contratos del backend.

No convertir endpoints protegidos en públicos.

No agregar secretos JWT al frontend.

---

## Diseño

Reutilizar:

`frontend/src/styles.css`

Mantener:

- paleta Ica;
- tipografía;
- espacios;
- lenguaje editorial;
- responsive;
- accesibilidad;
- estados `focus-visible`.

La autenticación debe sentirse como parte de Ica Stay y no como una aplicación independiente.

---

## Responsive

Login y Header deben funcionar correctamente en:

- desktop;
- tablet;
- mobile.

No debe existir scroll horizontal.

La composición editorial debe reorganizarse correctamente en pantallas pequeñas.

---

## Pruebas

Añadir pruebas relevantes para:

- AuthService;
- login correcto;
- credenciales incorrectas;
- restauración de sesión;
- JWT válido;
- JWT expirado;
- JWT malformado;
- claims incompletos;
- rol desconocido;
- logout;
- interceptor sin token;
- interceptor con token;
- exclusión del endpoint de login;
- respuesta 401 autenticada;
- respuesta 403;
- guard sin sesión;
- guard con sesión;
- comportamiento básico del Login.

No crear reservas reales como efecto secundario de las pruebas.

---

## Validación

Al finalizar:

1. `ng build` debe finalizar correctamente.
2. Todas las pruebas Angular deben aprobar.
3. Login real contra Spring Boot debe funcionar.
4. El JWT recibido debe almacenarse correctamente.
5. Los claims deben restaurar la sesión.
6. Una recarga debe conservar la sesión mientras `sessionStorage` continúe disponible.
7. Un token expirado no debe restaurar sesión.
8. Logout debe eliminar la sesión frontend.
9. El Header debe reaccionar al estado de autenticación.
10. Las peticiones protegidas deben poder incorporar Bearer.
11. Un `401` autenticado debe invalidar la sesión cuando corresponda.
12. Un `403` no debe cerrar la sesión.
13. Una ruta protegida sin sesión debe redirigir a `/login`.
14. Los roles deben utilizar exactamente `USER`, `HOTEL_ADMIN` y `SUPER_ADMIN`.
15. Spring Security debe continuar siendo responsable de la autorización real.

---

## Fuera de alcance

No implementar todavía:

- registro de usuarios;
- recuperación de contraseña;
- refresh token;
- revocación de JWT;
- panel completo `HOTEL_ADMIN`;
- panel completo `SUPER_ADMIN`;
- gestión administrativa de hoteles;
- flujo completo de reservas;
- pagos;
- chatbot;
- nuevas funcionalidades de backend.