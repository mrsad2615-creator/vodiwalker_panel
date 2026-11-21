package app.kairos.productivity;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

/**
 * Widget receiver. Placement, resize and the service's per-minute tick all land
 * here; every path ends in the same render, so there is one definition of what the
 * widget shows.
 */
public class ChronoWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        ChronoWidget.refresh(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ChronoWidget.ACTION_TICK.equals(intent.getAction())) {
            ChronoWidget.refresh(context);
        }
    }
}
