package gt.madretierra.ensayos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * MÓDULO DE ENSAYOS · INGENIO MADRE TIERRA
 *
 * Esta aplicación es la envoltura del módulo: lo que se ve adentro es la
 * misma pantalla publicada en GitHub Pages. Se hizo así a propósito,
 * porque es la única forma de lograr lo que se pidió: que al actualizar
 * el módulo la gente vea los cambios SIN volver a instalar nada. El
 * contenido se actualiza solo; esta envoltura casi nunca cambia.
 *
 * Lo que la aplicación le agrega a la página:
 *   · entra a pantalla completa, con su icono, como cualquier aplicación;
 *   · el botón de regresar del teléfono funciona dentro del módulo;
 *   · deja tomar fotos con la cámara y usar el GPS, pidiendo permiso;
 *   · guarda la sesión y lo capturado aunque se cierre;
 *   · los enlaces de Drive y de los correos se abren afuera, en el
 *     navegador o en la aplicación que corresponda.
 */
public class MainActivity extends AppCompatActivity {

    /** Donde vive el módulo. Si algún día cambia la dirección, es lo único que se toca. */
    private static final String INICIO = "https://ajreyes-ingeniomt.github.io/ensayos-ingenio-mt/";
    private static final String HOST = "ajreyes-ingeniomt.github.io";

    private WebView web;
    private ValueCallback<Uri[]> esperandoArchivo;
    private ActivityResultLauncher<Intent> elegirArchivo;
    private ActivityResultLauncher<String[]> pedirPermisos;
    private GeolocationPermissions.Callback gpsCallback;
    private String gpsOrigen;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        setContentView(R.layout.activity_main);
        web = findViewById(R.id.web);

        registrarAyudantes();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // aquí viven la sesión y lo capturado sin señal
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);           // la página no necesita leer el teléfono
        s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setTextZoom(100);                    // el tamaño lo manda el diseño, no el sistema

        // Sin señal se usa lo guardado; con señal se pide al servidor.
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false);
        }

        web.setWebViewClient(new Cliente());
        web.setWebChromeClient(new Chrome());

        if (guardado != null) {
            web.restoreState(guardado);
        } else {
            web.loadUrl(INICIO);
        }
    }

    /** Los dos ayudantes del sistema: escoger archivo y pedir permisos. */
    private void registrarAyudantes() {
        elegirArchivo = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                resultado -> {
                    if (esperandoArchivo == null) return;
                    Uri[] uris = null;
                    if (resultado.getResultCode() == RESULT_OK && resultado.getData() != null) {
                        Intent d = resultado.getData();
                        if (d.getClipData() != null) {
                            int n = d.getClipData().getItemCount();
                            uris = new Uri[n];
                            for (int i = 0; i < n; i++) uris[i] = d.getClipData().getItemAt(i).getUri();
                        } else if (d.getData() != null) {
                            uris = new Uri[]{ d.getData() };
                        }
                    }
                    esperandoArchivo.onReceiveValue(uris);
                    esperandoArchivo = null;
                });

        pedirPermisos = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                concedidos -> {
                    if (gpsCallback == null) return;
                    Boolean ok = concedidos.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    if (ok == null) ok = concedidos.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                    gpsCallback.invoke(gpsOrigen, Boolean.TRUE.equals(ok), false);
                    if (!Boolean.TRUE.equals(ok)) {
                        Toast.makeText(this,
                                "Sin permiso de ubicación el mapa no puede mostrar dónde está parado.",
                                Toast.LENGTH_LONG).show();
                    }
                    gpsCallback = null;
                    gpsOrigen = null;
                });
    }

    private boolean tienePermiso(String permiso) {
        return ContextCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_GRANTED;
    }

    /* ------------------------- navegación ------------------------- */

    private class Cliente extends WebViewClient {

        /** Lo de afuera del módulo (Drive, correos, mapas) se abre afuera. */
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest p) {
            return abrirAfuera(p.getUrl());
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, String url) {
            return abrirAfuera(Uri.parse(url));
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest p, android.webkit.WebResourceError e) {
            // Solo importa si falló la página misma, no una imagen suelta.
            if (p != null && p.isForMainFrame()) sinSenal();
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView v, int codigo, String desc, String url) {
            sinSenal();
        }
    }

    private boolean abrirAfuera(Uri u) {
        if (u == null) return false;
        String host = u.getHost();
        if (host != null && host.equalsIgnoreCase(HOST)) return false;   // es el propio módulo
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception e) {
            Toast.makeText(this, "No hay con qué abrir ese enlace.", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /**
     * Si el módulo no está guardado todavía y no hay señal, la pantalla
     * queda en blanco y nadie entiende por qué. Mejor decirlo.
     */
    private void sinSenal() {
        web.loadDataWithBaseURL(null,
                "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>body{font-family:system-ui,sans-serif;margin:0;padding:40px 24px;color:#2B2B2B;" +
                "text-align:center}h1{color:#0D9440;font-size:22px}p{color:#4A4A4A;line-height:1.5}" +
                "button{margin-top:18px;background:#0D9440;color:#fff;border:0;border-radius:8px;" +
                "padding:14px 22px;font-size:16px;font-weight:600}</style></head><body>" +
                "<h1>Sin señal</h1>" +
                "<p>No se pudo alcanzar el módulo y todavía no está guardado en este teléfono.</p>" +
                "<p>Ábralo una vez donde haya señal; después sirve también sin ella.</p>" +
                "<button onclick=\"location.href='" + INICIO + "'\">Intentar otra vez</button>" +
                "</body></html>",
                "text/html", "UTF-8", null);
    }

    /* ------------------- cámara, archivos y GPS ------------------- */

    private class Chrome extends WebChromeClient {

        /** La foto del muestreo: abre la cámara o la galería. */
        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (esperandoArchivo != null) esperandoArchivo.onReceiveValue(null);
            esperandoArchivo = callback;
            try {
                elegirArchivo.launch(params.createIntent());
                return true;
            } catch (Exception e) {
                esperandoArchivo = null;
                Toast.makeText(MainActivity.this,
                        "No se pudo abrir la cámara.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        /** El mapa pide dónde está parado el usuario. */
        @Override
        public void onGeolocationPermissionsShowPrompt(String origen,
                                                       GeolocationPermissions.Callback callback) {
            if (tienePermiso(Manifest.permission.ACCESS_FINE_LOCATION)
                    || tienePermiso(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                callback.invoke(origen, true, false);
                return;
            }
            gpsCallback = callback;
            gpsOrigen = origen;
            pedirPermisos.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION });
        }

        /** Si algún día se usa la cámara en vivo (no la foto suelta). */
        @Override
        public void onPermissionRequest(final PermissionRequest p) {
            runOnUiThread(() -> p.deny());
        }
    }

    /* ----------------------- ciclo de la pantalla ----------------------- */

    /** El botón de regresar del teléfono regresa dentro del módulo. */
    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle estado) {
        super.onSaveInstanceState(estado);
        if (web != null) web.saveState(estado);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            ((View) web.getParent()).setVisibility(View.GONE);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
