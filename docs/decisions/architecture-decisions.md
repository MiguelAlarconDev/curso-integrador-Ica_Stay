# Registro de decisiones arquitectónicas

**Actualizado:** 2026-09-27. Las decisiones nuevas deben incluir fecha, motivo, consecuencias y estado.

| Decisión | Estado | Motivo y consecuencia |
|---|---|---|
| Núcleo modular Spring Boot y frontend Angular | Acordada | Concentrar el dominio hotelero y permitir una interfaz separada |
| PostgreSQL como fuente transaccional | Acordada | Integridad de datos de reservas y usuarios |
| Tres roles: `USER`, `HOTEL_ADMIN`, `SUPER_ADMIN` | Acordada | Permisos según actor; validación de pertenencia en backend |
| Servicios de pago, comprobantes y notificaciones | Objetivo acordado | Distribuir responsabilidades especializadas entre el equipo; integración progresiva |
| Redis para caché | Planeada | Acelerar consultas; invalidación y TTL por diseñar |
| Ollama con modelo configurable | Planeada | Respuestas naturales locales sobre contexto autorizado |
| MongoDB para conversaciones | Planeada | Historial documental en etapa posterior |
| Un repositorio y specs ligeras | Acordada | Facilitar colaboración y trazabilidad |
| Facturación electrónica SUNAT | Pendiente | Evaluar viabilidad y alcance real antes de prometer emisión válida |

Las decisiones conceptuales y estados de trabajo se mantienen en [Notion](https://app.notion.com/p/3e82918d49f4817f94f1c329d2ff194f?pvs=204). Este registro explica su traducción técnica. Cuando cambie una decisión, actualizar ambos lugares y la spec afectada.
