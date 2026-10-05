package com.github.noamm9.dancingsquared.mixins

import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo
import java.io.File
import java.net.JarURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.TreeSet
import java.util.jar.JarFile

class AutoMixinDiscovery : IMixinConfigPlugin {
    private lateinit var basePackage: String
    private lateinit var basePath: String
    private var mixins = emptyList<String>()

    override fun onLoad(mixinPackage: String) {
        basePackage = mixinPackage
        basePath = mixinPackage.replace('.', '/')
        mixins = discoverMixins()
    }

    private fun discoverMixins(): List<String> {
        if (FabricLoader.getInstance().environmentType != EnvType.CLIENT) return emptyList()

        val result = TreeSet<String>()
        val resources = javaClass.classLoader.getResources(basePath)
        while (resources.hasMoreElements()) {
            val url = resources.nextElement()
            when (url.protocol) {
                "jar" -> collectFromJar(url, result)
                "file" -> collectFromDirectory(url, result)
            }
        }
        return result.toList()
    }

    private fun collectFromJar(url: URL, result: MutableSet<String>) {
        val connection = url.openConnection() as JarURLConnection
        JarFile(connection.jarFile.name).use { jar ->
            jar.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("$basePath/") && it.endsWith(".class") }
                .filterNot { it.contains('$') || it.endsWith("/module-info.class") }
                .filterNot { it.endsWith("${this.javaClass.simpleName}.class") }
                .map { it.removeSuffix(".class").replace('/', '.') }
                .filter { it.startsWith("$basePackage.") }
                .mapTo(result) { it.removePrefix("$basePackage.") }
        }
    }

    private fun collectFromDirectory(url: URL, result: MutableSet<String>) {
        val root = File(URLDecoder.decode(url.path, Charsets.UTF_8.name())).takeIf(File::exists) ?: return
        root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".class") }
            .forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                if (relative.contains('$') || relative.endsWith("/module-info.class")) return@forEach
                if (relative.endsWith("${this.javaClass.simpleName}.class")) return@forEach
                result += relative.removeSuffix(".class").replace('/', '.')
            }
    }

    override fun getMixins(): MutableList<String> = mixins.toMutableList()
    override fun getRefMapperConfig(): String? = null
    override fun shouldApplyMixin(targetClassName: String?, mixinClassName: String?) = true
    override fun acceptTargets(myTargets: MutableSet<String>?, otherTargets: MutableSet<String>?) = Unit
    override fun preApply(targetClassName: String?, targetClass: ClassNode?, mixinClassName: String?, info: IMixinInfo?) = Unit
    override fun postApply(targetClassName: String?, targetClass: ClassNode?, mixinClassName: String?, info: IMixinInfo?) = Unit
}
