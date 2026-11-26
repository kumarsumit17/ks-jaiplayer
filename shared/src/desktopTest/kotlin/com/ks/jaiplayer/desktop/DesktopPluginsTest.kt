package com.ks.jaiplayer.desktop

import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugin.PluginContext
import com.ks.jaiplayer.settings.MemorySettingsStore
import com.ks.jaiplayer.ui.PluginProblem
import java.io.File
import java.nio.file.Files
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopPluginsTest {
    private class BuiltIn(override val id: String) : JaiPlugin {
        override fun install(context: PluginContext) {}
    }

    private fun jar(dir: File, name: String, vararg attrs: Pair<String, String>): File {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            attrs.forEach { (k, v) -> mainAttributes.putValue(k, v) }
        }
        return File(dir, name).also { JarOutputStream(it.outputStream(), manifest).close() }
    }

    @Test fun listsBuiltInOnAndAddedOffAndExplainsProblems() {
        val dir = Files.createTempDirectory("plugins").toFile()
        jar(dir, "a.jar", "JaiPlayer-Plugin-Id" to "my.plugin", "JaiPlayer-Plugin-Name" to "Mine", "JaiPlayer-Plugin-Api" to "1")
        jar(dir, "old.jar", "JaiPlayer-Plugin-Id" to "old.plugin", "JaiPlayer-Plugin-Api" to "0")
        jar(dir, "nothing.jar")
        jar(dir, "clash.jar", "JaiPlayer-Plugin-Id" to "recent-files", "JaiPlayer-Plugin-Api" to "1")
        val m = DesktopPlugins(MemorySettingsStore(), listOf(BuiltIn("recent-files")), dir)

        val byFile = m.plugins.associateBy { it.file }
        assertNull(byFile[null]!!.problem)
        assertEquals("Mine", byFile["a.jar"]!!.name)
        assertNull(byFile["a.jar"]!!.problem)
        assertEquals(PluginProblem.WrongVersion, byFile["old.jar"]!!.problem)
        assertEquals(PluginProblem.Unreadable, byFile["nothing.jar"]!!.problem)
        assertEquals(PluginProblem.Unreadable, byFile["clash.jar"]!!.problem, "a built-in id cannot be taken over")

        assertTrue(m.isOn("recent-files"))
        assertFalse(m.isOn("my.plugin"), "added plugins start off")
        // The manifest-only jar has no plugin class: turning it on loads nothing and breaks nothing.
        m.set("my.plugin", true)
        assertTrue(m.isOn("my.plugin"))
        assertEquals(listOf("recent-files"), m.active().map { it.id })
        m.set("recent-files", false)
        assertEquals(emptyList(), m.active().map { it.id })
    }

    @Test fun loadsTheSamplePluginOnlyWhenOn() {
        val sample = File("../desktop/plugin-sample/build/libs/ks-jaiplayer-loop-plugin.jar").takeIf { it.isFile }
            ?: return println("Skipped: build :desktop:plugin-sample:jar first")
        val dir = Files.createTempDirectory("plugins").toFile()
        sample.copyTo(File(dir, sample.name))
        val m = DesktopPlugins(MemorySettingsStore(), emptyList(), dir)
        assertEquals(emptyList(), m.active().map { it.id })
        m.set("sample.loop", true)
        assertEquals(listOf("sample.loop"), m.active().map { it.id })
        assertEquals("Loop (sample plugin)", m.active().single().name)
    }
}
