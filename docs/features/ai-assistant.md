# IA-03.1 — Chat público con Ollama

**Estado:** en revisión. **Actualizado:** 2026-10-21.

## Objetivo y alcance

Visitantes, sin login, pueden consultar orientación hotelera en español mediante
Ollama local. IcaStay es una plataforma de búsqueda y reservas hoteleras en Ica.
Este incremento no consulta PostgreSQL, no guarda conversaciones, no incorpora
MongoDB ni frontend y no ejecuta acciones de negocio. Cada petición es independiente.

La arquitectura conceptual de IA describe un futuro asistente autenticado con
datos por rol. IA-03.1 establece un alcance público distinto, por instrucción del
proyecto: incluso con JWT, no se incorporan identidad, reservas ni datos privados.

## Contrato

`POST /api/v1/ai/chat`, Content-Type `application/json`.

Entrada: `{"message":"¿Cómo puedo buscar alojamiento en Ica?"}`.
Salida 200: `{"answer":"..."}`.

`message` debe ser texto no nulo, no blanco y de hasta 1000 caracteres Java
(unidades UTF-16), medidos antes de recortar espacios exteriores. No admite
números, arrays u objetos como mensaje. El servidor controla modelo, URL,
instrucciones y opciones; no acepta historial ni roles del cliente.

- 400: mensaje inválido, cuerpo ausente o JSON inválido.
- 503: Ollama inaccesible, generación interrumpida o capacidad local ocupada.
- 504: timeout de conexión o respuesta.
- 502: HTTP no exitoso de Ollama, JSON inválido o respuesta sin contenido textual.

Los errores usan ResponseStatusException con mensajes públicos genéricos;
no se devuelven cuerpos de error de Ollama, direcciones internas ni excepciones.
El formato del cuerpo de error depende del tratamiento estándar de Spring.

## Integración y configuración

HttpClient de Java 21 y Jackson 3 existentes; ninguna dependencia nueva.
POST `/api/chat` con `stream:false`, mensajes system/user y
`options.num_predict`; se extrae `message.content` y se recortan extremos.
El cliente reutilizado no sigue redirecciones. No hay reintentos automáticos.

Propiedades bajo `icastay.ai`:

| Propiedad | Variable de entorno | Defecto |
|---|---|---|
| base-url | AI_BASE_URL | http://localhost:11434 |
| model | AI_MODEL | qwen2.5:1.5b |
| connect-timeout | AI_CONNECT_TIMEOUT | 3s |
| response-timeout | AI_RESPONSE_TIMEOUT | 60s |
| max-tokens | AI_MAX_TOKENS | 256 |
| max-concurrent-requests | AI_MAX_CONCURRENT_REQUESTS | 1 |

URL HTTP(S) absoluta sin credenciales, query, fragmento ni ruta distinta de `/`;
modelo no blanco; tiempos y límites positivos. Configuración inválida impide
crear el cliente. Un semáforo por instancia rechaza inmediatamente exceso de
concurrencia, sin cola de espera; libera capacidad también al fallar.
El timeout de respuesta limita el intercambio completo, incluido el cuerpo;
al vencer o interrumpirse la espera se cancela la petición HTTP pendiente.

El prompt limita al ámbito hotelero de Ica, exige reconocer la falta de datos
verificados y prohíbe inventar hoteles, tarifas o disponibilidad. No hay datos
verificados en este incremento. El prompt no garantiza obediencia del modelo:
puede alucinar o desviarse; no constituye un mecanismo de autorización.

## Seguridad y límites

Solo el POST exacto se añade a permitAll. Se mantienen filtro JWT, CORS,
sesiones stateless y permisos existentes. Sin acceso a bases ni herramientas,
el modelo no puede modificar hoteles, reservas, pagos ni usuarios.
No se registran preguntas, respuestas ni credenciales en el módulo.
El límite es local por proceso, no un rate limit distribuido. La configuración
HTTP debe apuntar a un servicio de confianza. El límite de tokens solicitado no
es un límite de bytes de respuesta frente a un servidor malicioso.

## Criterios de aceptación y pruebas

- Petición anónima válida devuelve únicamente answer.
- Mensajes inválidos se rechazan antes de llamar a Ollama; 1000 se admite.
- Petición a Ollama contiene el modelo configurado, prompt y stream:false.
- Fallos HTTP, JSON, contenido, conexión y timeout tienen errores sanitizados.
- Saturación rechaza sin esperar; éxito, fallo e interrupción liberan capacidad.
- POST exacto público; otras rutas/métodos AI y rutas privadas siguen protegidos.
- CORS permite Angular y rechaza otros orígenes.
- Pruebas aisladas de PostgreSQL, Flyway y Ollama real.

La verificación manual contra Ollama real es independiente de las pruebas
automatizadas; no se afirma conectividad real sin ejecutarla.

## Validación ejecutada

2026-10-21: 50 pruebas aprobadas, sin fallos, errores ni omitidas:

```text
mvn.cmd -Dtest=AiChatEndpointTest,AiChatServiceTest,AiConfigurationTest,OllamaClientTest,OllamaHttpContractTest,JwtAuthenticationFilterTest test
```

Incluye un servidor HTTP local simulado y la cadena SecurityConfig real con
dependencias de autenticación simuladas. No se arrancaron PostgreSQL, Flyway
ni Ollama real. `git diff --check` sin errores. Queda pendiente la comprobación
manual con la instalación local de Ollama y qwen2.5:1.5b.
