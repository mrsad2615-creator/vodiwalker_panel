package ir.aegis.core.maps

import ir.aegis.core.crypto.CryptoEngine
import ir.aegis.core.model.LatLonPoint
import ir.aegis.core.model.MapMarker
import ir.aegis.core.model.MarkerCategory
import ir.aegis.core.model.OfflineMapPackage
import ir.aegis.core.model.SavedRoute

object OfflineMapSeeds {
    private const val SEED_TIME = 1763400000000L

    fun bundledPackages(): List<OfflineMapPackage> {
        val raw = listOf(
            OfflineMapPackage("pack-tehran-grid", "تهران مرکزی و اتوبان‌ها (Tehran)", "IR-THR", "نقشه وکتور کامل تهران: بزرگراه‌ها، مراکز امدادی، امنیتی و پناهگاه‌ها", 35.6400, 35.7550, 51.3100, 51.4600, 14_820_000L, 420, "2026.11-PRO-V3", "", true, true, SEED_TIME),
            OfflineMapPackage("pack-karaj-grid", "کرج و البرز مرکزی (Karaj)", "IR-KRJ", "خیابان‌های اصلی کرج، گوهردشت، عظیمیه، بیمارستان مدنی و نقاط امن", 35.7800, 35.8700, 50.8700, 51.0500, 8_240_000L, 210, "2026.11-PRO-V3", "", true, true, SEED_TIME),
            OfflineMapPackage("pack-mashhad-grid", "مشهد مقدس (Mashhad)", "IR-MHD", "وکیل‌آباد، احمدآباد، کوهسنگی، بیمارستان امام رضا و نقاط تجمع", 36.2400, 36.3700, 59.4600, 59.6800, 9_120_000L, 245, "2026.11-PRO-V3", "", true, true, SEED_TIME),
            OfflineMapPackage("pack-isfahan-grid", "اصفهان (Isfahan)", "IR-IFN", "زاینده‌رود، چهارباغ، نقش جهان، بیمارستان الزهرا و پارک ناژوان", 32.5850, 32.6850, 51.6000, 51.7350, 7_850_000L, 198, "2026.11-PRO-V3", "", true, true, SEED_TIME),
            OfflineMapPackage("pack-shiraz-grid", "شیراز (Shiraz)", "IR-SYZ", "بلوار زند، چمران، باغ ارم، بیمارستان نمازی و پارک آزادی", 29.5950, 29.6700, 52.4750, 52.5750, 7_420_000L, 184, "2026.11-PRO-V3", "", true, true, SEED_TIME),
            OfflineMapPackage("pack-tabriz-grid", "تبریز (Tabriz)", "IR-TBZ", "امام خمینی، پاسداران، آبرسان، بیمارستان امام رضا و ائل‌گلی", 38.0150, 38.1150, 46.2450, 46.3850, 7_110_000L, 176, "2026.11-PRO-V3", "", true, true, SEED_TIME),
            OfflineMapPackage("pack-alborz-corridor", "محور کوهستانی البرز و دربند", "IR-ALB", "خطوط تراز ارتفاع ۱۶۵۰ تا ۲۷۵۰ متر، دربند، ولنجک، شیرپلا", 35.7600, 35.8700, 51.3400, 51.4900, 9_640_000L, 268, "2026.11-PRO-V3", "", true, true, SEED_TIME),
            OfflineMapPackage("pack-bavaria-nuremberg", "نورنبرگ باواریا (Nuremberg)", "DE-NUE", "رودخانه پگنیتز، آلتشتات، کلینیکوم نورد و کایزربورگ", 49.4000, 49.5000, 11.0100, 11.1500, 11_450_000L, 312, "2026.11-PRO-V3", "", true, true, SEED_TIME),
        )
        return raw.map { pkg ->
            pkg.copy(sha256Checksum = CryptoEngine.sha256Hex("${pkg.id}|${pkg.regionCode}|${pkg.versionTag}|${pkg.tileCount}"))
        }
    }

    fun bundledMarkers(): List<MapMarker> = listOf(
        // Tehran
        MapMarker("mk-thr-hosp-1", "pack-tehran-grid", "بیمارستان امام خمینی (تهران)", "مرکز تروما و اورژانس ۲۴ ساعته — بلوار کشاورز غربی", MarkerCategory.HOSPITAL, 35.7062, 51.3775, 1198.0, true, false, SEED_TIME),
        MapMarker("mk-thr-hosp-2", "pack-tehran-grid", "بیمارستان شریعتی", "مرکز اورژانس و مراقبت‌های ویژه — کارگر شمالی", MarkerCategory.HOSPITAL, 35.7220, 51.3885, 1214.0, true, false, SEED_TIME),
        MapMarker("mk-thr-hosp-3", "pack-tehran-grid", "بیمارستان سینا", "مرکز اورژانس مرکزی — خیابان امام خمینی", MarkerCategory.HOSPITAL, 35.6862, 51.4182, 1168.0, true, false, SEED_TIME),
        MapMarker("mk-thr-sec-1", "pack-tehran-grid", "مرکز امنیت و پاسخ سایبری / فتا (مرکز)", "نقطه امن هماهنگی امنیتی و گزارش تهدیدات سایبری", MarkerCategory.POLICE, 35.7145, 51.4180, 1210.0, true, false, SEED_TIME),
        MapMarker("mk-thr-sec-2", "pack-tehran-grid", "پایگاه امنیتی بهارستان", "مرکز انتظامی و هماهنگی مدنی محدوده مرکزی", MarkerCategory.POLICE, 35.6898, 51.4305, 1179.0, true, false, SEED_TIME),
        MapMarker("mk-thr-shelter-1", "pack-tehran-grid", "پارک لاله (پناهگاه روباز)", "فضای باز امن دور از نمای شیشه‌ای برج‌ها", MarkerCategory.SHELTER, 35.7106, 51.3919, 1208.0, true, false, SEED_TIME),
        MapMarker("mk-thr-shelter-2", "pack-tehran-grid", "پناهگاه مستحکم جنوب نواب", "پناهگاه زیرزمینی تقویت‌شده محدوده نواب", MarkerCategory.SHELTER, 35.6762, 51.3678, 1142.0, true, false, SEED_TIME),
        MapMarker("mk-thr-shelter-3", "pack-tehran-grid", "پارک شهر (نقطه تجمع)", "فضای باز گسترده و دسترسی مستقیم از ولیعصر", MarkerCategory.ASSEMBLY_POINT, 35.6836, 51.4112, 1158.0, true, false, SEED_TIME),
        MapMarker("mk-thr-fire-1", "pack-tehran-grid", "ایستگاه آتش‌نشانی آزادی / نواب", "تیم امداد و نجات شهری", MarkerCategory.FIRE_STATION, 35.6941, 51.3755, 1185.0, true, false, SEED_TIME),
        MapMarker("mk-thr-water-1", "pack-tehran-grid", "مخزن آب اضطراری ولیعصر", "نقطه ذخیره آب شرب اضطراری", MarkerCategory.WATER_POINT, 35.7010, 51.4068, 1192.0, true, false, SEED_TIME),

        // Karaj
        MapMarker("mk-krj-hosp-1", "pack-karaj-grid", "بیمارستان شهید مدنی کرج", "مرکز تروما و اورژانس اصلی — خیابان مطهری", MarkerCategory.HOSPITAL, 35.8360, 50.9710, 1320.0, true, false, SEED_TIME),
        MapMarker("mk-krj-sec-1", "pack-karaj-grid", "مرکز امنیت و امداد آزادگان کرج", "واحد پاسخ امنیتی و امدادی کرج", MarkerCategory.POLICE, 35.8340, 50.9960, 1325.0, true, false, SEED_TIME),
        MapMarker("mk-krj-shelter-1", "pack-karaj-grid", "پارک باغ فاتح (پناهگاه امن)", "فضای باز و نقطه امن تجمع گوهردشت / جهانشهر", MarkerCategory.SHELTER, 35.8300, 50.9780, 1330.0, true, false, SEED_TIME),

        // Mashhad
        MapMarker("mk-mhd-hosp-1", "pack-mashhad-grid", "بیمارستان امام رضا مشهد", "بزرگ‌ترین مرکز درمانی و اورژانس شرق کشور", MarkerCategory.HOSPITAL, 36.2830, 59.6020, 992.0, true, false, SEED_TIME),
        MapMarker("mk-mhd-hosp-2", "pack-mashhad-grid", "بیمارستان قائم (عج)", "مرکز اورژانس و تروما بلوار احمدآباد", MarkerCategory.HOSPITAL, 36.2950, 59.5910, 995.0, true, false, SEED_TIME),
        MapMarker("mk-mhd-shelter-1", "pack-mashhad-grid", "پارک ملت مشهد (نقطه امن)", "فضای باز امن در بلوار وکیل‌آباد", MarkerCategory.SHELTER, 36.3180, 59.5380, 1020.0, true, false, SEED_TIME),
        MapMarker("mk-mhd-sec-1", "pack-mashhad-grid", "مرکز امنیتی و هماهنگی میدان مرکزی", "مرکز پشتیبانی امنیتی و انتظامی مشهد", MarkerCategory.POLICE, 36.2880, 59.6160, 990.0, true, false, SEED_TIME),

        // Isfahan
        MapMarker("mk-ifn-hosp-1", "pack-isfahan-grid", "مرکز پزشکی الزهرا اصفهان", "بیمارستان فوق تخصصی و اورژانس صفه", MarkerCategory.HOSPITAL, 32.6100, 51.6680, 1580.0, true, false, SEED_TIME),
        MapMarker("mk-ifn-shelter-1", "pack-isfahan-grid", "مجموعه ناژوان / سی‌وسه‌پل", "فضای باز امن حاشیه زاینده‌رود", MarkerCategory.SHELTER, 32.6440, 51.6670, 1575.0, true, false, SEED_TIME),
        MapMarker("mk-ifn-sec-1", "pack-isfahan-grid", "مرکز امنیت و حفاظت نقش جهان", "پایگاه امنیتی مرکز شهر اصفهان", MarkerCategory.POLICE, 32.6570, 51.6770, 1576.0, true, false, SEED_TIME),

        // Shiraz
        MapMarker("mk-syz-hosp-1", "pack-shiraz-grid", "بیمارستان نمازی شیراز", "مرکز تروما و اورژانس میدان نمازی", MarkerCategory.HOSPITAL, 29.6200, 52.5230, 1505.0, true, false, SEED_TIME),
        MapMarker("mk-syz-shelter-1", "pack-shiraz-grid", "پارک آزادی شیراز (پناهگاه امن)", "فضای باز امن مرکزی", MarkerCategory.SHELTER, 29.6270, 52.5520, 1510.0, true, false, SEED_TIME),

        // Tabriz
        MapMarker("mk-tbz-hosp-1", "pack-tabriz-grid", "بیمارستان امام رضا تبریز", "مرکز اورژانس و ترومای دانشگاهی", MarkerCategory.HOSPITAL, 38.0580, 46.3250, 1365.0, true, false, SEED_TIME),
        MapMarker("mk-tbz-shelter-1", "pack-tabriz-grid", "پارک ائل‌گلی (منطقه امن)", "فضای باز مرتفع و ایمن تبریز", MarkerCategory.SHELTER, 38.0280, 46.3660, 1410.0, true, false, SEED_TIME),

        // Nuremberg
        MapMarker("mk-nue-hosp-1", "pack-bavaria-nuremberg", "Klinikum Nord Emergency", "اورژانس منطقه‌ای نورنبرگ", MarkerCategory.HOSPITAL, 49.4590, 11.0630, 315.0, true, false, SEED_TIME),
        MapMarker("mk-nue-shelter-1", "pack-bavaria-nuremberg", "Wöhrder Wiese Safe Zone", "منطقه امن مرتفع بالای سطح رودخانه پگنیتز", MarkerCategory.SHELTER, 49.4525, 11.0955, 328.0, true, false, SEED_TIME),
        MapMarker("mk-nue-sec-1", "pack-bavaria-nuremberg", "Polizei / Hauptbahnhof Hub", "ایستگاه مرکزی امنیت و امداد", MarkerCategory.POLICE, 49.4458, 11.0825, 312.0, true, false, SEED_TIME),

        // Alborz
        MapMarker("mk-alb-shelter-1", "pack-alborz-corridor", "پناهگاه کوهستانی شیرپلا", "پناهگاه مستحکم ارتفاع ۲۷۵۰ متر", MarkerCategory.SHELTER, 35.8445, 51.4185, 2750.0, true, false, SEED_TIME),
        MapMarker("mk-alb-assembly-1", "pack-alborz-corridor", "پارک جمشیدیه (فلات امن)", "پناهگاه سنگی باز در شمال نیاوران", MarkerCategory.ASSEMBLY_POINT, 35.8170, 51.4585, 1820.0, true, false, SEED_TIME),
    )

    fun bundledRoutes(): List<SavedRoute> {
        val thrPts = listOf(
            LatLonPoint(35.6892, 51.3890, "مرکز شهر"),
            LatLonPoint(35.7062, 51.3775, "بیمارستان امام خمینی"),
            LatLonPoint(35.7106, 51.3919, "پارک لاله"),
        )
        val thrDist = GeoMath.computeRouteDistanceMeters(thrPts)
        return listOf(
            SavedRoute(
                id = "route-thr-evac",
                packageId = "pack-tehran-grid",
                name = "کریدور امن بلوار کشاورز تا پارک لاله",
                description = "مسیر پهن و ایمن دسترسی به مرکز درمانی و پناهگاه روباز",
                waypoints = thrPts,
                totalDistanceMeters = thrDist,
                estimatedWalkMinutes = GeoMath.estimateWalkingMinutes(thrDist),
                hazardLevel = "LOW",
                createdAtMillis = SEED_TIME,
            ),
        )
    }
}
