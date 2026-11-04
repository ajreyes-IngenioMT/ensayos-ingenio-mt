# El APK de Android — cómo funciona y cómo se publica

## Lo primero, porque cambia todo lo demás

La aplicación es **una envoltura**: por dentro muestra el mismo módulo que está publicado en
GitHub Pages. No es un capricho técnico, es la única forma de lograr lo que se pidió:

> *«cada vez que actualice mis ensayos ingenio mt debes actualizar la aplicación, para que el
> usuario que lo tenga pueda actualizar sin necesidad de volver a instalarla»*

Con esta arquitectura eso se cumple solo:

| Qué cambia | Qué tiene que hacer la gente |
|---|---|
| El módulo (pantallas, muestreos, cálculos, reportes) | **Nada.** Cierran y abren la aplicación y ya está la versión nueva. |
| La envoltura (permisos de Android, cámara, GPS) | Instalar el APK nuevo encima. Pasa muy de vez en cuando. |

Si en lugar de esto se hiciera una aplicación "de verdad" con las pantallas metidas adentro,
**cada corrección obligaría a los cinco usuarios a instalar un APK nuevo**, que es justo lo que
no se quiere.

---

## Qué hay en esta carpeta

```
android/
  app/src/main/java/gt/madretierra/ensayos/MainActivity.java   la aplicación
  app/src/main/AndroidManifest.xml                             permisos y arranque
  app/src/main/res/                                            el logo y los colores
  app/build.gradle, build.gradle, settings.gradle              cómo se compila
```

El APK **no se compila aquí**: lo compila GitHub solo, con el flujo
`.github/workflows/apk.yml`. Así ninguno de los dos necesita tener instalado Android Studio.

## Qué le agrega la aplicación a la página

- Abre a pantalla completa, con su icono, sin la barra del navegador.
- El botón de regresar del teléfono regresa dentro del módulo.
- **Cámara**: la foto del muestreo abre la cámara del teléfono.
- **GPS**: el mapa muestra dónde está parado; el permiso se pide la primera vez y el módulo
  funciona igual si se niega.
- La sesión y lo capturado sin señal se guardan aunque se cierre la aplicación.
- Los enlaces de Drive y de los correos se abren afuera, en el navegador o en la aplicación que
  corresponda, no dentro del módulo.

## Hasta qué Android llega

Desde **Android 5.0 (2014)** en adelante, que en la práctica es cualquier teléfono que siga en
uso. Más atrás no es realista: esas versiones ya no reciben parches de seguridad y el navegador
que traen por dentro no entiende el módulo. El icono está en todas las densidades y además como
**icono adaptable**, que es lo que piden Android 8 en adelante para darle la forma de cada marca
(círculo, cuadrado redondeado, gota).

## La llave de firma — esto es lo importante

El APK va firmado siempre con **la misma llave**. Eso es lo que permite instalar una versión
encima de la otra sin desinstalar. Si la llave cambiara, Android rechazaría la actualización con
un error de "paquete en conflicto" y habría que desinstalar y volver a instalar en los cinco
teléfonos.

Por eso:

- **Guarde el archivo `ensayos.jks` y su contraseña** donde no se pierdan. Son la identidad de la
  aplicación.
- No van en el repositorio (es público): viven en **Settings ▸ Secrets and variables ▸ Actions**.

## Para compilar a mano, si alguna vez hace falta

Con Android Studio o con el SDK instalado:

```bash
cd android
gradle assembleRelease \
  -PcodigoVersion=1001 -PnombreVersion=1.1 \
  -PalmacenFirma=/ruta/ensayos.jks -PclaveAlmacen=LA_CLAVE \
  -PaliasFirma=ensayosmt -PclaveClave=LA_CLAVE
```

El APK queda en `android/app/build/outputs/apk/release/`.

## Si alguna vez cambia la dirección del módulo

En `MainActivity.java` están las dos líneas `INICIO` y `HOST`. Se cambian, se sube, y GitHub
compila un APK nuevo solo.
