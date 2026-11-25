package com.ks.jaiplayer.vlc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VlcPluginPolicyTest {
    private val lgpl = LGPL_SENTENCE
    private val gpl = GPL_SENTENCE

    @Test fun keepsLgplPlugins() {
        assertNull(VlcPluginPolicy.exclusion("libavcodec_plugin.dylib", lgpl))
        assertNull(VlcPluginPolicy.exclusion("libmp4_plugin.dll", lgpl))
        assertNull(VlcPluginPolicy.exclusion("libpacketizer_a52_plugin.dylib", lgpl))
    }

    @Test fun leavesOutGplAndUnclearPlugins() {
        assertEquals("GPL", VlcPluginPolicy.exclusion("libx264_plugin.dylib", gpl))
        assertEquals("licence unclear", VlcPluginPolicy.exclusion("libmkv_plugin.dll", null))
        assertEquals("licence unclear: BSD", VlcPluginPolicy.exclusion("libfoo_plugin.dll", "BSD"))
    }

    @Test fun leavesOutPluginsThatLinkGplLibraries() {
        for (name in listOf("faad", "mad", "postproc", "dvdnav", "a52", "aribcam")) {
            assertEquals("links a GPL library", VlcPluginPolicy.exclusion("lib${name}_plugin.dylib", lgpl), name)
        }
        assertEquals("not used by the app", VlcPluginPolicy.exclusion("libqt_plugin.dll", lgpl))
        assertEquals("not a plugin", VlcPluginPolicy.exclusion("plugins.dat", lgpl))
    }

    @Test fun readsTheDeclaredLicence() {
        fun binary(vararg texts: String) = ("\u0000junk\u0000" + texts.joinToString("\u0000")).toByteArray()
        assertEquals(LGPL_SENTENCE, declaredLicence(binary(LGPL_SENTENCE)))
        assertEquals(GPL_SENTENCE, declaredLicence(binary(GPL_SENTENCE)))
        assertNull(declaredLicence(binary(LGPL_SENTENCE, GPL_SENTENCE)))
        assertNull(declaredLicence(binary("nothing")))
    }

    @Test fun pluginNames() {
        assertEquals("stream_out_rtp", VlcPluginPolicy.nameOf("libstream_out_rtp_plugin.dylib"))
        assertNull(VlcPluginPolicy.nameOf("libvlccore.dll"))
    }
}
