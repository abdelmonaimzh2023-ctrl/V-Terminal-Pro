package com.vterminal.plugins

import android.content.Context
import java.io.*
import java.util.jar.*

class PluginsSystem(private val context: Context) {
    data class Plugin(val name: String, val version: String, val description: String, val className: String)
    
    private val plugins = mutableListOf<Plugin>()
    
    fun scanPlugins() {
        plugins.clear()
        val pluginDir = File("${context.filesDir}/plugins")
        if (!pluginDir.exists()) pluginDir.mkdirs()
        
        pluginDir.listFiles()?.forEach { file ->
            if (file.extension == "jar" || file.extension == "vpp") {
                try {
                    val jar = JarFile(file)
                    val manifest = jar.manifest
                    val attrs = manifest.mainAttributes
                    plugins.add(Plugin(
                        name = attrs.getValue("Plugin-Name") ?: file.nameWithoutExtension,
                        version = attrs.getValue("Plugin-Version") ?: "1.0",
                        description = attrs.getValue("Plugin-Description") ?: "",
                        className = attrs.getValue("Plugin-Class") ?: ""
                    ))
                    jar.close()
                } catch (e: Exception) {}
            }
        }
    }
    
    fun getPlugins(): List<Plugin> = plugins
    
    fun executePlugin(name: String, args: Array<String>): String {
        val plugin = plugins.find { it.name == name } ?: return "Plugin not found"
        return "Plugin ${plugin.name} v${plugin.version} executed with args: ${args.joinToString()}"
    }
}
