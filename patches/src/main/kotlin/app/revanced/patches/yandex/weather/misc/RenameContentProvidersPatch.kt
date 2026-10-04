package app.revanced.patches.yandex.weather.misc

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.util.MethodUtil
import org.w3c.dom.Element

private const val PACKAGE_NAME = "ru.yandex.weatherplugin"

/**
 * Appended to authorities that "Change package name" does not update,
 * because they do not contain the package name.
 */
private const val AUTHORITY_SUFFIX = ".revanced"

/**
 * For package names other than the official ones, the app builds the authority of its database provider
 * as "<package name>.weather.core", which does not match the authority declared in the manifest.
 */
private const val DATABASE_AUTHORITY_SUFFIX = ".weather.core"

private val renameContentProvidersResourcePatch = resourcePatch {
    apply {
        val stringNames = mutableSetOf<String>()

        document("AndroidManifest.xml").use { document ->
            val providers = document.getElementsByTagName("provider")

            (0 until providers.length).map { providers.item(it) as Element }.forEach { provider ->
                val authorities = provider.getAttribute("android:authorities")

                provider.setAttribute(
                    "android:authorities",
                    authorities.split(";").joinToString(";") { authority ->
                        when {
                            authority.startsWith("@string/") -> {
                                stringNames += authority.removePrefix("@string/")
                                authority
                            }
                            // Updated by "Change package name".
                            authority.contains(PACKAGE_NAME) -> authority
                            authority.startsWith("@") ->
                                throw PatchException("Unexpected provider authority reference: $authority")
                            else -> authority + AUTHORITY_SUFFIX
                        }
                    },
                )
            }
        }

        if (stringNames.isEmpty()) return@apply

        document("res/values/strings.xml").use { document ->
            val strings = document.getElementsByTagName("string")

            val renamed = (0 until strings.length)
                .map { strings.item(it) as Element }
                .filter { it.getAttribute("name") in stringNames }
                .onEach { it.textContent += AUTHORITY_SUFFIX }

            if (renamed.size != stringNames.size) {
                throw PatchException("Could not find all provider authority strings: $stringNames")
            }
        }
    }
}

@Suppress("unused")
val renameContentProvidersPatch = bytecodePatch(
    name = "Rename content providers",
    description = "Renames the content providers that \"Change package name\" does not rename, " +
        "and makes the app find its database under a changed package name. " +
        "This allows installing the patched app next to the original app.",
) {
    compatibleWith(PACKAGE_NAME)

    dependsOn(renameContentProvidersResourcePatch)

    apply {
        // Methods that choose the database authority based on the package name.
        val methods = classDefs.flatMap { classDef ->
            classDef.methods
                .filter { method ->
                    method.implementation?.instructions?.any {
                        (it as? ReferenceInstruction)?.reference.let { reference ->
                            reference is StringReference && reference.string == DATABASE_AUTHORITY_SUFFIX
                        }
                    } == true
                }
                .map { classDef to it }
        }

        if (methods.isEmpty()) throw PatchException("Could not find the database authority methods")

        methods.forEach { (classDef, method) ->
            val mutableMethod = classDefs.getOrReplaceMutable(classDef).methods.first {
                MethodUtil.methodSignaturesMatch(it, method)
            }
            val instructions = mutableMethod.implementation!!.instructions

            // Treat the package name as the official one, so the authority declared in the manifest is used.
            instructions
                .withIndex()
                .filter { (_, instruction) ->
                    instruction.opcode == Opcode.IGET_OBJECT &&
                        ((instruction as ReferenceInstruction).reference as FieldReference).let {
                            it.definingClass == "Landroid/content/pm/ApplicationInfo;" && it.name == "packageName"
                        }
                }
                .reversed()
                .forEach { (index, instruction) ->
                    val register = (instruction as TwoRegisterInstruction).registerA
                    mutableMethod.addInstruction(index + 1, "const-string v$register, \"$PACKAGE_NAME\"")
                }
        }
    }
}
