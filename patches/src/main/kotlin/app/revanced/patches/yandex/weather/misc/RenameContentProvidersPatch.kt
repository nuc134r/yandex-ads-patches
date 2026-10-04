package app.revanced.patches.yandex.weather.misc

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructions
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

/**
 * The account library declares authorities like "com.yandex.passport.internal.provider.<package name>".
 * "Change package name" only updates authorities starting with the package name,
 * so they are changed to "<package name>.passport.internal.provider".
 */
private const val PASSPORT_AUTHORITY_PREFIX = "com.yandex."

/**
 * The account library builds the authority of its internal provider from this prefix and the package name.
 */
private const val PASSPORT_INTERNAL_PROVIDER_PREFIX = "com.yandex.passport.internal.provider."

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
                            authority.startsWith(PACKAGE_NAME) -> authority
                            authority.startsWith(PASSPORT_AUTHORITY_PREFIX) && authority.endsWith(".$PACKAGE_NAME") ->
                                PACKAGE_NAME + "." + authority
                                    .removePrefix(PASSPORT_AUTHORITY_PREFIX)
                                    .removeSuffix(".$PACKAGE_NAME")
                            authority.contains(PACKAGE_NAME) ->
                                throw PatchException("Unexpected provider authority: $authority")
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
        fun methodsWithString(string: String) = classDefs.flatMap { classDef ->
            classDef.methods
                .filter { method ->
                    method.implementation?.instructions?.any {
                        (it as? ReferenceInstruction)?.reference.let { reference ->
                            reference is StringReference && reference.string == string
                        }
                    } == true
                }
                .map { classDef to it }
        }.ifEmpty { throw PatchException("Could not find methods using \"$string\"") }.map { (classDef, method) ->
            classDefs.getOrReplaceMutable(classDef).methods.first { MethodUtil.methodSignaturesMatch(it, method) }
        }

        // Build the authority of the account library's internal provider like the patched manifest declares it.
        methodsWithString(PASSPORT_INTERNAL_PROVIDER_PREFIX)
            .filter { it.returnType == "Landroid/net/Uri;" && it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;") }
            .ifEmpty { throw PatchException("Could not find the internal provider URI method") }
            .forEach { method ->
                method.addInstructions(
                    0,
                    """
                        new-instance v0, Ljava/lang/StringBuilder;
                        const-string v1, "content://"
                        invoke-direct { v0, v1 }, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
                        invoke-virtual { v0, p0 }, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                        const-string v1, ".passport.internal.provider"
                        invoke-virtual { v0, v1 }, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                        invoke-virtual { v0 }, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                        move-result-object v0
                        invoke-static { v0 }, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
                        move-result-object v0
                        return-object v0
                    """,
                )
            }

        // Methods that choose the database authority based on the package name.
        methodsWithString(DATABASE_AUTHORITY_SUFFIX).forEach { mutableMethod ->
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
