package app.revanced.patches.yandex.weather.misc

import app.revanced.patcher.extensions.removeInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.util.MethodUtil

private const val VERIFICATION_ERROR = "Passport library verification error"

@Suppress("unused")
val disableAccountLibraryVerificationPatch = bytecodePatch(
    name = "Disable account library verification",
    description = "Prevents the Yandex account library from closing the app on start, " +
        "because the patched app is not signed by Yandex.",
) {
    compatibleWith("ru.yandex.weatherplugin")

    apply {
        // The method verifying the setup of the account library, which exits the app if a check fails.
        val methods = classDefs.flatMap { classDef ->
            classDef.methods
                .filter { method ->
                    method.implementation?.instructions?.any {
                        (it as? ReferenceInstruction)?.reference.let { reference ->
                            reference is StringReference && reference.string == VERIFICATION_ERROR
                        }
                    } == true
                }
                .map { classDef to it }
        }

        var removed = 0

        methods.forEach { (classDef, method) ->
            val mutableMethod = classDefs.getOrReplaceMutable(classDef).methods.first {
                MethodUtil.methodSignaturesMatch(it, method)
            }

            mutableMethod.implementation!!.instructions
                .withIndex()
                .filter { (_, instruction) ->
                    ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                        it.definingClass == "Ljava/lang/System;" && it.name == "exit"
                    } == true
                }
                .reversed()
                .forEach { (index, _) ->
                    mutableMethod.removeInstruction(index)
                    removed++
                }
        }

        if (removed == 0) throw PatchException("Could not find the account library verification")
    }
}
