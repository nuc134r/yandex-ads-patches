package app.revanced.patches.yandex.weather.misc

import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val PACKAGE_NAME = "ru.yandex.weatherplugin"

@Suppress("unused")
val removeSharedPermissionsPatch = resourcePatch(
    name = "Remove shared permissions",
    description = "Removes the declarations of permissions shared with other Yandex apps and the original app, " +
        "such as com.yandex.permission.READ_CREDENTIALS. Otherwise, the patched app can not be installed " +
        "next to them, because they are signed with a different key. " +
        "Signing in with an account from other Yandex apps will not work.",
) {
    compatibleWith(PACKAGE_NAME)

    apply {
        document("AndroidManifest.xml").use { document ->
            val permissions = document.getElementsByTagName("permission")

            (0 until permissions.length)
                .map { permissions.item(it) as Element }
                .filterNot { it.getAttribute("android:name").startsWith(PACKAGE_NAME) }
                .forEach { it.parentNode.removeChild(it) }
        }
    }
}
