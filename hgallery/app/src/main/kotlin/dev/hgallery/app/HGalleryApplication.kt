package dev.hgallery.app

import android.app.Application

/**
 * Application entry point.
 *
 * No cloud component, no account, no network permission: the gallery reads the
 * device's own MediaStore and stores its own bookkeeping locally.
 */
class HGalleryApplication : Application()
