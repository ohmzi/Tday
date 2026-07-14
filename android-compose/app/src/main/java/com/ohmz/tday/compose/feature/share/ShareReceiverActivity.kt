package com.ohmz.tday.compose.feature.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AnimRes
import androidx.appcompat.app.AppCompatActivity
import com.ohmz.tday.compose.MainActivity
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.feature.widget.WidgetCreateTarget
import com.ohmz.tday.compose.feature.widget.WidgetCreateTaskSubmitter
import com.ohmz.tday.compose.feature.widget.WidgetCreateTaskSurface
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives ACTION_SEND text/plain shares from other apps and drops them into
 * the same frameless create-task sheet the widgets use — same workspace
 * guard, same offline-first submit path. The share sheet launches this
 * activity inside the sending app's task, so dismissing simply returns the
 * user to where they shared from.
 */
@AndroidEntryPoint
class ShareReceiverActivity : AppCompatActivity() {

    @Inject
    lateinit var widgetCreateTaskSubmitter: WidgetCreateTaskSubmitter

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Tday_WidgetCreate)
        super.onCreate(savedInstanceState)
        val share = sharedTaskContent(
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
            text = intent.getStringExtra(Intent.EXTRA_TEXT),
        )
        if (intent.action != Intent.ACTION_SEND || share == null) {
            finish()
            return
        }
        applyTransition(
            enter = R.anim.widget_create_enter,
            exit = R.anim.widget_create_hold,
        )
        enableEdgeToEdge()
        setContent {
            WidgetCreateTaskSurface(
                createTarget = WidgetCreateTarget.TODAY,
                widgetCreateTaskSubmitter = widgetCreateTaskSubmitter,
                onExit = ::exitToSender,
                onOpenMainApp = ::openMainApp,
                initialTitle = share.title,
                initialNotes = share.notes,
            )
        }
    }

    private fun exitToSender() {
        finish()
        applyTransition(
            enter = R.anim.widget_create_hold,
            exit = R.anim.widget_create_exit,
        )
    }

    private fun openMainApp() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
        )
        finish()
        applyTransition(
            enter = R.anim.widget_create_hold,
            exit = R.anim.widget_create_exit,
        )
    }

    @Suppress("DEPRECATION")
    private fun applyTransition(
        @AnimRes enter: Int,
        @AnimRes exit: Int,
    ) {
        overridePendingTransition(enter, exit)
    }
}
