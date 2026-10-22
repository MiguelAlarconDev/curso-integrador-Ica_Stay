# IA-04.1 — Chat flotante público

Estado: en revisión. Actualizado: 2026-10-21.

Componente standalone en App, habilitado por data.aiChat=true en Home, catálogo,
detalle, login y registro. Cerrar oculta el panel y conserva mensajes. Navegar
entre páginas públicas conserva la instancia; entrar a una ruta privada destruye
el componente, cancela solicitudes y descarta la memoria. Sin storage del navegador.

Servicio dedicado con HttpClient y API_BASE_URL: POST /api/v1/ai/chat,
{message} → {answer}. Solo este POST se excluye de JWT y del logout por 401.
Timeout cliente: 70 segundos, superior al defecto de 60 segundos del backend.
503 se presenta como ocupado o temporalmente no disponible: el contrato no
distingue saturación de caída de Ollama. Reintentos manuales sin duplicar pregunta.

Bienvenida, mensajes diferenciados, carga, una petición a la vez. Texto no blanco
y máximo 1000 unidades UTF-16 antes de recortar. Enter envía, Shift+Enter añade
línea; no se envía durante composición IME. Historial solo visual: cada petición
es independiente y no transmite mensajes anteriores.

Panel no modal con Escape, foco al campo al abrir y al botón al cerrar. Una sola
región aria-live anuncia estados/respuestas; historial no vivo. Interpolación de
texto sin HTML del modelo. Tokens de color, botones y campos globales existentes.

Aceptación: apertura/cierre, bienvenida, envío, validación, carga/doble envío,
errores/reintento, teclado, acceso público sin JWT, cancelación, visibilidad por
rutas, regresión HTTP, build y git diff --check. Sin dependencias nuevas ni cambios
backend. Conexión real y revisión visual móvil se reportan separadas de los tests.

## Validación ejecutada

72 pruebas aprobadas en 5 archivos (widget, rutas, servicio HTTP, autenticación y
App), sin fallos. `npm.cmd run build` correcto y dentro de los budgets configurados.
`git diff --check` sin errores de espacios. Comandos desde frontend:

```text
npm.cmd test -- --watch=false --include=src/app/features/ai/**/*.spec.ts --include=src/app/core/services/ai-chat.service.spec.ts --include=src/app/core/auth/auth.spec.ts --include=src/app/app.spec.ts
npm.cmd run build
```

Las pruebas usan HttpTestingController y DOM simulado. Pendiente revisión visual
en navegador móvil/escritorio y comprobación contra backend/Ollama reales.

## IA-05.3 — Recuperación anónima (2026-10-22)

Este incremento sustituye el comportamiento de historial exclusivamente visual
de IA-04.1. Reutiliza IA-05.2, validado con 89 pruebas, sin modificar backend.

- Al montar el widget, GET `/api/v1/ai/conversations/current?page=0&size=50`.
  DTO real: `{conversationId: string|null, content: [{question,answer,createdAt,expiresAt}], page,size,hasNext}`.
  Se valida la respuesta y se invierte el orden descendente del servidor, incluidos
  sus desempates. Se muestra una sola bienvenida y hasta 50 intercambios previos;
  `hasNext` muestra un aviso de historial limitado. No hay paginación adicional en esta etapa.
- GET y POST `/api/v1/ai/chat` usan `withCredentials:true` y `API_BASE_URL`.
  Solo esos métodos/rutas exactos omiten JWT y logout por 401. No se habilitan
  credenciales globales, no se lee cookie ni se guarda historial/token en storage.
- El backend usa Origin/Fetch Metadata y JSON como defensa CSRF, sin token adicional.
  Angular no fabrica Origin, Cookie ni cabeceras CSRF. El navegador gestiona la cookie HttpOnly.
- Durante recuperación se bloquea envío, con timeout de 10 segundos. Error de red,
  503 o sesión rechazada muestra un aviso y permite reintentar o seguir consultando.
  Sesión ausente, vencida o inválida es un GET correcto vacío: solo queda bienvenida.
  La revocación por cambio de clave se presenta igual; no existe endpoint de revocación.
- La recuperación no se repite al cerrar/abrir ni tras cada envío. Una vez iniciados
  mensajes locales, el reintento de recuperación se pospone hasta recargar: el DTO
  no incluye ID de intercambio ni el POST confirmación de persistencia y mezclar
  snapshots podría duplicar mensajes o perder respuestas no guardadas. Cada montaje
  parte de memoria nueva. Reintentar POST reutiliza la misma pregunta visual.
- No se eliminan mensajes por igualdad de texto: repetir legítimamente una pregunta
  produce intercambios distintos. El backend no ofrece clave de idempotencia, por lo
  que no se puede garantizar ausencia de duplicados persistidos tras una respuesta
  perdida en la red. El cliente evita duplicados de representación y envíos simultáneos.
- Cancelación GET/POST al destruir, interpolación segura, teclado y responsive
  existentes. Una sola región aria-live anuncia el resultado de recuperación,
  nunca todos los mensajes históricos. Se conserva foco al campo y al lanzador.

Desarrollo: ejecutar backend con `AI_SESSION_COOKIE_SECURE=false`, frontend en
`http://localhost:4200` y CORS exacto del mismo origen. La variable no está definida
en el proceso de Codex inspeccionado; no se presupone que otro proceso la herede.
No se cambia el valor seguro de producción (`true`), ni su clave de firma.
Producción requiere frontend/backend same-site y HTTPS conforme a IA-05.2.

Aceptación: restauración tras remontaje, orden, bienvenida única, errores/reintento,
degradación a chat básico, cookies HTTP sin JWT, límites, cancelación, regresión de
autenticación y rutas públicas, compilación y revisión del diff.

### Validación IA-05.3

- 89 pruebas frontend aprobadas en cinco archivos: widget (incluido remontaje que
  representa recarga), servicio HTTP, rutas del widget, autenticación y App.
  Se ejecutó el comando de pruebas indicado en IA-04.1 con los casos nuevos.
- `npm.cmd run build`: correcto, bundle inicial 362.61 kB dentro del presupuesto.
- `git diff --check`: correcto. Sin dependencias nuevas ni cambios del backend
  durante IA-05.3; se conservan sus cambios previos de IA-05.2 sin commit.
- Comprobación local: `/actuator/health` del backend y `/api/version` de Ollama
  responden HTTP 200. Sin embargo, GET público `/api/v1/ai/conversations/current?page=0&size=50`
  con Origin `http://localhost:4200` devuelve 401, también con cookie inválida.
  Esto contradice el contrato/código validado de IA-05.2: se debe comprobar la
  versión y configuración del proceso activo. No se cambió ni reinició ese proceso.
  No fue posible verificar sus atributos de cookie ni `Secure=false` en ejecución.
- No se escribieron conversaciones de prueba en Mongo real. Las respuestas POST
  se verificaron con HttpTestingController; queda pendiente prueba integral de
  navegador, Ollama y recuperación persistida tras levantar IA-05.2 actualizado
  con `AI_SESSION_COOKIE_SECURE=false` en HTTP local. También queda pendiente
  revisión visual móvil/escritorio en navegador; el CSS responsive no cambió.
