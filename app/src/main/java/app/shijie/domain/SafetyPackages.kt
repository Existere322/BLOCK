package app.shijie.domain

object SafetyPackages {
    val exact: Set<String> = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.settings.intelligence",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.incallui",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.oplus.dialer",
        "com.coloros.dialer",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.oplus.appdetail",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.coloros.oppoguardelf",
        "com.oplus.battery",
    )

    val knownHomes: Set<String> = setOf(
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.oplus.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.sec.android.app.launcher",
    )

    fun isSafety(
        packageName: String,
        selfPackage: String,
        homePackages: Set<String> = emptySet(),
        dialerPackage: String? = null,
    ): Boolean {
        if (packageName.isBlank()) return true
        if (packageName == selfPackage) return true
        if (packageName == dialerPackage) return true
        if (packageName in exact) return true
        if (packageName in knownHomes || packageName in homePackages) return true
        if (packageName.endsWith(".launcher") || packageName.endsWith(".launcher3")) return true
        return false
    }
}
