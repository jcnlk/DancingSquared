package com.github.noamm9.dancingsquared.mixins

import com.github.noamm9.NoammAddons
import com.github.noamm9.ui.clickgui.enums.CategoryType
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

@Mixin(CategoryType::class)
abstract class MixinCategoryType {
    private companion object {
        @Suppress("NOTHING_TO_INLINE")
        @JvmStatic
        @Inject(method = ["\$values"], at = [At("RETURN")], cancellable = true)
        private inline fun addCustomCategory(cir: CallbackInfoReturnable<Array<CategoryType>>) {
            try {
                val values = cir.returnValue
                val constructor = MethodHandles.lookup().findConstructor(
                    CategoryType::class.java,
                    MethodType.methodType(Void.TYPE, String::class.java, Int::class.javaPrimitiveType)
                )
                val category = constructor.invokeWithArguments("MACRO", values.size) as CategoryType

                cir.returnValue = values + category
            } catch (e: Exception) {
                NoammAddons.logger.error("Error while adding custom category type", e)
                e.printStackTrace()
            }
        }
    }
}
