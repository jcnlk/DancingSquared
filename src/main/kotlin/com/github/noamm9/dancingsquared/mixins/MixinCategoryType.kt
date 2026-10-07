package com.github.noamm9.dancingsquared.mixins

import com.github.noamm9.NoammAddons
import com.github.noamm9.ui.clickgui.enums.CategoryType
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import sun.misc.Unsafe

@Mixin(CategoryType::class)
abstract class MixinCategoryType {
    private companion object {
        private const val CATEGORY_NAME = "ADDON"

        @JvmStatic
        @Inject(method = ["<clinit>"], at = [At("TAIL")])
        private fun addCustomCategory(@Suppress("UNUSED_PARAMETER") callbackInfo: CallbackInfo) {
            try {
                val unsafe = unsafe()
                val values = categoryValues()
                val category = unsafe.allocateInstance(CategoryType::class.java) as CategoryType
                setEnumFields(unsafe, category, values.size)

                @Suppress("UNCHECKED_CAST")
                val extendedValues = values.copyOf(values.size + 1) as Array<CategoryType>
                extendedValues[values.size] = category

                setStaticField(unsafe, "\$VALUES", extendedValues)
                setStaticField(unsafe, "\$ENTRIES", enumEntries(extendedValues))
            } catch (exception: Exception) {
                NoammAddons.logger.error("Failed to add the DancingSquared category", exception)
            }
        }

        private fun categoryValues(): Array<CategoryType> {
            val field = CategoryType::class.java.getDeclaredField("\$VALUES")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return field.get(null) as Array<CategoryType>
        }

        private fun setEnumFields(unsafe: Unsafe, category: CategoryType, ordinal: Int) {
            val nameField = Enum::class.java.getDeclaredField("name")
            val ordinalField = Enum::class.java.getDeclaredField("ordinal")
            unsafe.putObject(category, unsafe.objectFieldOffset(nameField), CATEGORY_NAME)
            unsafe.putInt(category, unsafe.objectFieldOffset(ordinalField), ordinal)
        }

        private fun setStaticField(unsafe: Unsafe, name: String, value: Any) {
            val field = CategoryType::class.java.getDeclaredField(name)
            unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value)
        }

        private fun enumEntries(values: Array<CategoryType>): Any {
            val method = Class.forName("kotlin.enums.EnumEntriesKt").declaredMethods.first {
                it.name == "enumEntries" && it.parameterCount == 1 && it.parameterTypes[0].isArray
            }
            return method.invoke(null, values)
        }

        private fun unsafe(): Unsafe {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            return field.get(null) as Unsafe
        }
    }
}
