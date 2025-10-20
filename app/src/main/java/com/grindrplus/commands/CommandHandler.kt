package com.grindrplus.commands

import android.app.AlertDialog
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import android.graphics.Color
import com.grindrplus.GrindrPlus


class CommandHandler(
    recipient: String,
    sender: String = ""
) {
    private val commandModules: MutableList<CommandModule> = mutableListOf()

    init {
        commandModules.add(Location(recipient, sender))
        commandModules.add(Profile(recipient, sender))
        commandModules.add(Utils(recipient, sender))
        commandModules.add(Database(recipient, sender))
        commandModules.add(MediaManager(recipient, sender))
        commandModules.add(Scammer(recipient, sender))
        commandModules.add(Archive(recipient, sender))
    }

    fun handle(input: String) {
        val args = input.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val command = args.firstOrNull() ?: return

        if (command == "help") {
            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                val dialogView = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(20, 20, 20, 20)
                }

                val helpText = commandModules.joinToString("\n\n") { it.getHelp() }

                val textView = AppCompatTextView(activity).apply {
                    text = helpText
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setPadding(20, 20, 20, 20)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                dialogView.addView(textView)

                AlertDialog.Builder(activity)
                    .setTitle("Help")
                    .setView(dialogView)
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()
                    .show()
            }
        } else {
            for (module in commandModules) {
                if (module.handle(command, args.drop(1))) break
            }
        }

    }
}