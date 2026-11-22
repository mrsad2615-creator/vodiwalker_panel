package app.kairos.productivity;

import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.BridgeActivity;

/**
 * KAIROS native shell.
 *
 * Android 15+ (targetSdk 35/36) forces every app edge-to-edge, so the WebView is
 * stretched behind the status bar, the display cutout and the gesture/navigation
 * bar. Capacitor's SystemBars plugin normally compensates, but that path depends
 * on the WebView version and on `viewport-fit` negotiation inside the page, which
 * is exactly where this app kept breaking.
 *
 * So the window insets are handled here, in the app itself:
 *   - the content is laid out edge-to-edge,
 *   - the decor view is padded by the real system-bar + cutout insets,
 *   - the insets are then consumed for the WebView, so `env(safe-area-inset-*)`
 *     reports 0 and the CSS layer never adds a second inset,
 *   - IME (keyboard) insets are passed through untouched, so `adjustResize`
 *     keeps working for text fields.
 *
 * This is identical on Android 7 … 16 and independent of the WebView build.
 * SystemBars inset handling is disabled in capacitor.config.ts so there is only
 * one owner of the insets.
 */
public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Registered before super.onCreate so the bridge picks it up during init.
        registerPlugin(ChronoPlugin.class);
        registerPlugin(KairosAudioPlugin.class);
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);

        final View decorView = window.getDecorView();
        ViewCompat.setOnApplyWindowInsetsListener(decorView, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);

            // Hand the WebView a system-bar-free viewport (so CSS safe-area
            // variables resolve to 0) while keeping the IME insets so the
            // keyboard can still resize the web view.
            return new WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(
                            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout(),
                            Insets.NONE)
                    .build();
        });

        ViewCompat.requestApplyInsets(decorView);
    }
}
