package com.github.noamm9.dancingsquared.mixins

import com.github.noamm9.NoammAddons
import com.github.noamm9.ui.clickgui.enums.CategoryType
import com.llamalad7.mixinextras.injector.ModifyReturnValue
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.Arrays

@Mixin(CategoryType::class)
abstract class MixinCategoryType {
    private companion object {
        @Suppress("NOTHING_TO_INLINE")
        @JvmStatic
        @ModifyReturnValue(method = ["\$values"], at = [At("RETURN")])
        private inline fun addCustomCategory(values: Array<CategoryType>): Array<CategoryType> {
            try {
                val constructor = MethodHandles.lookup().findConstructor(
                    CategoryType::class.java,
                    MethodType.methodType(Void.TYPE, String::class.java, Int::class.javaPrimitiveType)
                )
                val category = constructor.invokeExact("MACRO", values.size) as CategoryType

                val extendedValues = Arrays.copyOf(values, values.size + 1)
                extendedValues[values.size] = category
                return extendedValues
            } catch (e: Exception) {
                NoammAddons.logger.error("Error while adding custom category type", e)
                e.printStackTrace()
                return values
            }
        }
    }
}
