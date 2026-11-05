/* ===================================================================
 * TRABAJADOR DE SERVICIO
 *
 * Es lo que convierte la página en una aplicación de teléfono: guarda la
 * pantalla, el mapa y las imágenes de satélite ya vistas, para que en la
 * finca abra aunque no haya señal.
 *
 * Tres reglas, en orden de importancia:
 *
 *   1. NUNCA toca las llamadas al servidor. Los datos del módulo viajan
 *      como POST a script.google.com y pasan de largo: aquí no se guarda
 *      ni un dato de ensayo, ni una clave, ni una sesión.
 *   2. La pantalla se pide primero al servidor y solo se usa la copia
 *      guardada si no hay señal. Así una versión nueva entra sola y
 *      nadie se queda trabajando con una pantalla vieja.
 *   3. Las imágenes del mapa sí se guardan de una vez (son siempre
 *      iguales), con un tope para no llenar el teléfono.
 *
 * Al publicar una versión nueva, suba el número de VERSION. Con eso se
 * borra lo guardado de la versión anterior y, además, el teléfono se da
 * cuenta de que este archivo cambió: ahí es cuando la pantalla saca el
 * botón «Actualizar».
 *
 * Este trabajador NO se adelanta a entrar. Se queda esperando hasta que
 * la persona toca «Actualizar», porque entrar solo a media captura en el
 * campo es peor que esperar.
 * =================================================================== */

var VERSION = 'ensayos-v6';
var CACHE_APP    = VERSION + '-app';     // la pantalla y sus archivos
var CACHE_MAPA   = VERSION + '-mapa';    // Leaflet
var CACHE_IMAGEN = VERSION + '-imagen';  // las imágenes de satélite
var TOPE_IMAGENES = 1500;                // ~150 MB en el peor caso

/** Lo mínimo para que la aplicación abra sin señal la primera vez. */
var BASICOS = [
  './',
  './index.html',
  './manifest.json',
  './icono-192.png',
  './icono-512.png',
  './apple-touch-icon.png'
];

/* ------------------------- instalación ------------------------- */

self.addEventListener('install', function (ev) {
  ev.waitUntil(
    caches.open(CACHE_APP).then(function (c) {
      // Si alguno falla (por ejemplo un icono que todavía no se subió) la
      // instalación no se cae: se guarda lo que sí esté.
      return Promise.all(BASICOS.map(function (u) {
        return c.add(new Request(u, { cache: 'reload' })).catch(function () {});
      }));
    })
    // Sin skipWaiting: si ya había una versión corriendo, esta se queda
    // esperando y la pantalla ofrece el botón «Actualizar». La primera
    // vez que se instala no hay a quién esperar y entra sola.
  );
});

/* Al activarse, se borra lo guardado por versiones anteriores. */
self.addEventListener('activate', function (ev) {
  ev.waitUntil(
    caches.keys().then(function (nombres) {
      return Promise.all(nombres.map(function (n) {
        return n.indexOf(VERSION) === 0 ? null : caches.delete(n);
      }));
    }).then(function () { return self.clients.claim(); })
  );
});

/* La pantalla puede pedir que la versión nueva entre de una vez. */
self.addEventListener('message', function (ev) {
  if (ev.data === 'actualizar') self.skipWaiting();
});

/* --------------------------- peticiones --------------------------- */

function esImagenDeMapa(url) {
  return /(^|\.)google\.com$/.test(url.hostname) && url.pathname.indexOf('/vt') === 0 ||
         /arcgisonline\.com$/.test(url.hostname) ||
         /tile\.openstreetmap\.org$/.test(url.hostname);
}

function esLeaflet(url) {
  return /^unpkg\.com$/.test(url.hostname) && url.pathname.indexOf('/leaflet@') === 0;
}

self.addEventListener('fetch', function (ev) {
  var pet = ev.request;

  // Regla 1: todo lo que no sea un GET pasa de largo. Ahí van las
  // llamadas al servidor con los datos de los ensayos.
  if (pet.method !== 'GET') return;

  var url;
  try { url = new URL(pet.url); } catch (e) { return; }

  // El servidor del módulo tampoco se toca, ni para leer.
  if (/script\.google(usercontent)?\.com$/.test(url.hostname)) return;

  // La pantalla: primero el servidor, y la copia guardada si no hay señal.
  if (pet.mode === 'navigate' || (url.origin === self.location.origin && /\.html$/.test(url.pathname))) {
    ev.respondWith(primeroLaRed(pet));
    return;
  }

  // Las imágenes del mapa y Leaflet: de una vez lo guardado.
  if (esImagenDeMapa(url)) { ev.respondWith(primeroLoGuardado(pet, CACHE_IMAGEN, true)); return; }
  if (esLeaflet(url))      { ev.respondWith(primeroLoGuardado(pet, CACHE_MAPA, false)); return; }

  // Los archivos propios (iconos, manifiesto): lo guardado y se refresca
  // por detrás para la próxima vez.
  if (url.origin === self.location.origin) {
    ev.respondWith(primeroLoGuardado(pet, CACHE_APP, false));
  }
});

/**
 * Primero el servidor. Si tarda más de 6 segundos o no hay señal, se
 * entrega la copia guardada: en la finca es mejor abrir con lo de ayer
 * que quedarse viendo una pantalla en blanco.
 */
function primeroLaRed(pet) {
  return new Promise(function (listo) {
    var contestado = false;
    function responder(r) { if (!contestado) { contestado = true; listo(r); } }

    var reloj = setTimeout(function () {
      caches.match(pet, { ignoreSearch: true }).then(function (g) {
        if (g) responder(g);
      });
    }, 6000);

    fetch(pet).then(function (r) {
      clearTimeout(reloj);
      if (r && r.ok) {
        var copia = r.clone();
        caches.open(CACHE_APP).then(function (c) { c.put('./index.html', copia); });
      }
      responder(r);
    }).catch(function () {
      clearTimeout(reloj);
      caches.match(pet, { ignoreSearch: true }).then(function (g) {
        responder(g || caches.match('./index.html').then(function (x) {
          return x || new Response('<h1>Sin señal</h1><p>Abra la aplicación una vez con señal para poder usarla sin ella.</p>',
            { headers: { 'Content-Type': 'text/html; charset=utf-8' } });
        }));
      });
    });
  });
}

/** Lo guardado de una vez; si no está, se pide y se guarda. */
function primeroLoGuardado(pet, nombreCache, conTope) {
  return caches.match(pet).then(function (g) {
    if (g) return g;
    return fetch(pet).then(function (r) {
      if (r && (r.ok || r.type === 'opaque')) {
        var copia = r.clone();
        caches.open(nombreCache).then(function (c) {
          c.put(pet, copia);
          if (conTope) recortar(c, TOPE_IMAGENES);
        });
      }
      return r;
    }).catch(function () { return g; });
  });
}

/** Deja el guardado de imágenes en su tope, borrando las más viejas. */
function recortar(cache, tope) {
  cache.keys().then(function (llaves) {
    if (llaves.length <= tope) return;
    var sobran = llaves.length - tope;
    for (var i = 0; i < sobran; i++) cache.delete(llaves[i]);
  });
}
