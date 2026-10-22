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
