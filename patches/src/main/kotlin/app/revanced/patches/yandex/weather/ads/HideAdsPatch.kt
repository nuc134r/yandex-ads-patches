package app.revanced.patches.yandex.weather.ads

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.util.MethodUtil

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/yandex/ads/AdsBlocker;"

/**
 * Package of the public API of the Yandex Mobile Ads SDK.
 * The SDK ships consumer ProGuard rules that keep these names, so they are stable across app versions.
 */
private const val ADS_SDK_PACKAGE = "Lcom/yandex/mobile/ads/"

/**
 * Whether the class is one of the SDK's ad loaders,
 * e.g. `NativeAdLoader`, `NativeBulkAdLoader`, `SliderAdLoader`, `InterstitialAdLoader`,
 * `RewardedAdLoader`, `AppOpenAdLoader`, `InstreamAdLoader`, or a banner view.
 */
private fun isAdLoader(type: String) = type.startsWith(ADS_SDK_PACKAGE) &&
    (type.endsWith("AdLoader;") || type.endsWith("BannerAdView;"))

@Suppress("unused")
val hideAdsPatch = bytecodePatch(
    name = "Hide ads",
    description = "Prevents the Yandex Mobile Ads SDK from loading banner, native, interstitial, " +
        "rewarded, app open and instream ads.",
) {
    compatibleWith("ru.yandex.weatherplugin")

    extendWith("extensions/extension.rve")

    apply {
        val loadMethods = classDefs
            .filter { isAdLoader(it.type) }
            .flatMap { classDef ->
                classDef.methods
                    .filter { method ->
                        method.name.startsWith("load") &&
                            AccessFlags.PUBLIC.isSet(method.accessFlags) &&
                            !AccessFlags.STATIC.isSet(method.accessFlags) &&
                            method.implementation != null &&
                            (method.returnType == "V" || method.returnType == "Ljava/lang/Object;")
                    }
                    .map { classDef to it }
            }

        if (loadMethods.isEmpty()) throw PatchException("Could not find any Yandex Mobile Ads loaders")

        loadMethods
            .groupBy({ it.first }, { it.second })
            .forEach { (classDef, methods) ->
                val mutableClass = classDefs.getOrReplaceMutable(classDef)
                methods.forEach { method ->
                    mutableClass.methods.first { MethodUtil.methodSignaturesMatch(it, method) }.blockLoad()
                }
            }
    }
}

private fun MutableMethod.blockLoad() {
    // Suspending variants return a LoadResult. Return a failure result instead of suspending.
    if (returnType == "Ljava/lang/Object;") {
        val parameterRegisters = parameters.sumOf { it.registerSize() } + 1
        // A free register is needed to hold the result.
        if (implementation!!.registerCount <= parameterRegisters) return

        addInstructionsWithLabels(
            0,
            """
                invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS_DESCRIPTOR->createFailedLoadResult(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v0
                if-eqz v0, :load
                return-object v0
            """,
            ExternalLabel("load", implementation!!.instructions.first()),
        )
        return
    }

    // Listener variants report the failure to the listener.
    // Variants without a listener (e.g. BannerAdView.loadAd) collapse the view, if the loader is one.
    val listenerIndex = parameterTypes.indexOfLast { it.toString().endsWith("Listener;") }
    val register = if (listenerIndex >= 0) {
        "p" + (1 + parameters.take(listenerIndex).sumOf { it.registerSize() })
    } else {
        "p0"
    }
    val extensionMethod = if (listenerIndex >= 0) "notifyLoadFailed" else "onLoadBlocked"

    addInstructions(
        0,
        """
            invoke-static/range { $register .. $register }, $EXTENSION_CLASS_DESCRIPTOR->$extensionMethod(Ljava/lang/Object;)V
            return-void
        """,
    )
}

private fun CharSequence.registerSize() = when (toString()) {
    "J", "D" -> 2
    else -> 1
}
