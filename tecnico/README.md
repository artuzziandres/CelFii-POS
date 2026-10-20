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

En la planilla autorizada: Cel-Fii Técnico → Activar APK. Copiar el código completo `CELFII1:…` y pegarlo en Conexión en la APK. Android comprueba el acceso antes de guardar la conexión cifrada con Android Keystore. El servidor verifica la clave antes de leer o escribir.

## Protocolo

POST JSON: `{ "token": "...", "action": "getData|saveOrder", "args": {} }`.
Respuesta: `{ "ok": true, "result": ... }` o `{ "ok": false, "error": "..." }`.

Cada guardado lleva una clave idempotente. No se reintentan escrituras automáticamente tras un error de conexión. La app no conserva copias locales de clientes u órdenes.

## Estado

Versión 1.1.0: tarjetas de órdenes en celular, activación con un código, estado inicial sin datos ficticios y respeto de las áreas de sistema y teclado de Android. La publicación del conector y la conexión real siguen pendientes; no se verificó en un teléfono físico.
