# The FFmpeg decoder's JNI: native method names, and the method native code calls back.
-keep,includedescriptorclasses class com.ks.jaiplayer.ffmpeg.** { native <methods>; }
-keep,includedescriptorclasses class com.ks.jaiplayer.ffmpeg.FfmpegAudioDecoder {
    private java.nio.ByteBuffer growOutputBuffer(androidx.media3.decoder.SimpleDecoderOutputBuffer, int);
}
