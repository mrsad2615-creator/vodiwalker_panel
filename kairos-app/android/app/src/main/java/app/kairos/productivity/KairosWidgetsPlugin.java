package app.kairos.productivity;

import android.content.Context;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Read-only projection from the local web database; never mutates user records. */
@CapacitorPlugin(name = "KairosWidgets")
public class KairosWidgetsPlugin extends Plugin {
    @PluginMethod
    public void sync(PluginCall call) {
        JSObject snapshot = call.getObject("snapshot");
        if (snapshot == null || snapshot.toString().length() > 20000) {
            call.reject("invalid_widget_snapshot");
            return;
        }
        getContext().getSharedPreferences("kairos_widgets", Context.MODE_PRIVATE)
                .edit().putString("snapshot", snapshot.toString()).apply();
        try {
            SectionWidget.refresh(getContext());
            call.resolve();
        } catch (Exception error) {
            call.reject("widget_refresh_failed", error);
        }
    }
}
