package com.ks.jaiplayer.player

/**
 * Everything the user can do to the player from a key or a remote. The desktop keyboard and the
 * TV remote map to the same actions (see [com.ks.jaiplayer.ui.actionForKey]).
 */
enum class PlayerAction {
    TogglePlay,
    SeekBack,
    SeekForward,
    SeekBackLong,
    SeekForwardLong,
    VolumeUp,
    VolumeDown,
    ToggleMute,
    ToggleFullScreen,
    Slower,
    Faster,
    NextItem,
    PreviousItem,

    /** Leave full screen, else hide the controls, else close the video. Handled by the interface. */
    Back,
}
