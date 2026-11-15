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
    description = "Prevents the Yandex Mobile Ads SDK from loading banner, native, feed, interstitial, " +
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

        // Block the app-specific FeedAd entry point.
        // It must return br.b(false), which AdManager handles as a normal feed-ad load failure.
        val feedLoadMethods = classDefs.flatMap { classDef ->
            classDef.methods
                .filter { method ->
                    method.name == "a" &&
                        AccessFlags.PUBLIC.isSet(method.accessFlags) &&
                        !AccessFlags.STATIC.isSet(method.accessFlags) &&
                        method.returnType == "Ljava/lang/Object;" &&
                        method.parameterTypes.size == 7 &&
                        method.parameterTypes.take(5).map { it.toString() } == listOf(
                            "Landroid/view/ViewGroup;",
                            "I",
                            "Ljava/lang/String;",
                            "Landroid/location/Location;",
                            "Ljava/util/Map;",
                        ) &&
                        method.implementation != null
                }
                .map { classDef to it }
        }

        if (feedLoadMethods.size != 1) {
            throw PatchException(
                "Expected exactly one FeedAd loader entry point, found ${feedLoadMethods.size}",
            )
        }

        val failedResultClass = "Lbr\$b;"
        val failedResultConstructorExists = classDefs.any { classDef ->
            classDef.type == failedResultClass &&
                classDef.methods.any { method ->
                    method.name == "<init>" &&
                        method.parameterTypes.map { it.toString() } == listOf("Z")
                }
        }

        if (!failedResultConstructorExists) {
            throw PatchException("Could not find the FeedAd failure result constructor")
        }

        feedLoadMethods.forEach { (classDef, method) ->
            val mutableClass = classDefs.getOrReplaceMutable(classDef)
            mutableClass.methods
                .first { MethodUtil.methodSignaturesMatch(it, method) }
                .blockFeedAdLoad(failedResultClass)
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

private fun MutableMethod.blockFeedAdLoad(resultClass: String) {
    // Return the app's normal "feed ad failed to load" result immediately.
    // p0/p1 are reused because the original method body is bypassed completely.
    addInstructions(
        0,
        """
            new-instance p0, $resultClass
            const/4 p1, 0x0
            invoke-direct {p0, p1}, $resultClass-><init>(Z)V
            return-object p0
        """,
    )
}
