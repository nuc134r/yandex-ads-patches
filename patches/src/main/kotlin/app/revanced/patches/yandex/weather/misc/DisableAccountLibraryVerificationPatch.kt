package app.revanced.patches.yandex.weather.misc

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.removeInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.util.MethodUtil

private const val VERIFICATION_ERROR = "Passport library verification error"

/**
 * Suffix of the account library's process name.
 * The runnable that closes the app after a signature check failed kills this process first.
 */
private const val PASSPORT_PROCESS_SUFFIX = ":passport"

@Suppress("unused")
val disableAccountLibraryVerificationPatch = bytecodePatch(
    name = "Disable account library verification",
    description = "Prevents the Yandex account library from closing the app on start, " +
        "because the patched app is not signed by Yandex. Signing in to a Yandex account may not work.",
) {
    compatibleWith("ru.yandex.weatherplugin")

    apply {
        fun MethodReference.isMethod(definingClass: String, name: String) =
            this.definingClass == definingClass && this.name == name

        // The runnable that kills the account library's process and exits the app,
        // if the app is not signed by Yandex.
        val exitRunnables = classDefs.flatMap { classDef ->
            classDef.methods
                .filter { method ->
                    method.name == "run" && method.returnType == "V" && method.parameterTypes.isEmpty()
                }
                .filter { method ->
                    val references = method.implementation?.instructions
                        ?.mapNotNull { (it as? ReferenceInstruction)?.reference }
                        .orEmpty()

                    references.any { it is StringReference && it.string == PASSPORT_PROCESS_SUFFIX } &&
                        references.any { it is MethodReference && it.isMethod("Landroid/os/Process;", "killProcess") } &&
                        references.any { it is MethodReference && it.isMethod("Ljava/lang/System;", "exit") }
                }
                .map { classDef to it }
        }

        if (exitRunnables.isEmpty()) throw PatchException("Could not find the account library signature check")

        exitRunnables.forEach { (classDef, method) ->
            classDefs.getOrReplaceMutable(classDef).methods
                .first { MethodUtil.methodSignaturesMatch(it, method) }
                .addInstruction(0, "return-void")
        }

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
                    ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
                        ?.isMethod("Ljava/lang/System;", "exit") == true
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
