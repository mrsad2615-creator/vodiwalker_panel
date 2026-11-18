package ai.dana.media

import ai.dana.MainActivity
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/** Opens the review/consent UI only. The tile alone cannot authorize a capture. */
class ScreenTile : TileService() {
    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val launch = Intent(this, MainActivity::class.java).setAction(OneFrameCaptureService.ACTION_OPEN_MEDIA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 32, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else startActivityAndCollapse(launch)
    }
}
