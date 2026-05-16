package com.lanrhyme.clipypse

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    Logger.init(DesktopLogger())

    val state = rememberWindowState()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Clipypse",
        state = state
    ) {
        window.minimumSize = Dimension(400, 500)

        App()
    }
}
