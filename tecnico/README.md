# Cel-Fii Técnico para Android

Aplicación independiente de servicio técnico: `com.celfii.tecnico`.
Ingreso y edición de reparaciones, búsqueda, estados, señas, historial, clientes y transiciones Matrix.

La interfaz y Pin se empaquetan dentro de la APK. La conexión con Google Sheets se realiza desde Android mediante HTTPS. No se incluye información de clientes, claves ni una sesión de Google en el código o en la APK.

## Compilar

Java 17, Gradle 8.13 y Android SDK 36:

```sh
gradle :app:assembleRelease
```

El flujo `tecnico-apk.yml` obtiene el recurso original de Pin y produce un APK sin firmar. La firma de distribución se aplica fuera del repositorio, sin publicar la clave.

## Activación

En Conexión, configurar la URL HTTPS de Apps Script terminada en `/exec` y una clave por dispositivo emitida desde la planilla autorizada. Se guarda cifrada con Android Keystore. El servidor debe verificar la clave antes de leer o escribir.

## Protocolo

POST JSON: `{ "token": "...", "action": "getData|saveOrder", "args": {} }`.
Respuesta: `{ "ok": true, "result": ... }` o `{ "ok": false, "error": "..." }`.

Cada guardado lleva una clave idempotente. No se reintentan escrituras automáticamente tras un error de conexión. La app no conserva copias locales de clientes u órdenes.

## Estado

Proyecto Android en preparación. La publicación del servicio, firma, verificación del paquete y conexión real deben confirmarse antes de considerarlo listo para uso.
