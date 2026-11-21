package app.liftlog

import androidx.compose.ui.window.ComposeUIViewController
import app.liftlog.ui.App

/** Entry point used by the iOS app (iosApp/ContentView.swift). */
fun MainViewController() = ComposeUIViewController { App(IosPlatform()) }
