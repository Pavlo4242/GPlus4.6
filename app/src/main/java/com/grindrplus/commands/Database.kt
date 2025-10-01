package com.grindrplus.commands

import android.app.AlertDialog
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.Environment
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import com.grindrplus.GrindrPlus
import com.grindrplus.core.DatabaseHelper
import com.grindrplus.ui.Utils.copyToClipboard
import java.io.File

class Database(
    recipient: String,
    sender: String
) : CommandModule("Database", recipient, sender) {
    @Command("list_tables", aliases = ["lts"], help = "List all tables in the database")
    fun listTables(args: List<String>) {
        try {
            val query = "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;"
            val tables = DatabaseHelper.query(query).map { it["name"].toString() }

            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                val dialogView = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val tableList = if (tables.isEmpty()) "No tables found."
                    else tables.joinToString("\n")

                val textView = AppCompatTextView(activity).apply {
                    text = tableList
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setPadding(20, 20, 20, 20)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 20, 0, 0)
                    }
                }

                dialogView.addView(textView)

                AlertDialog.Builder(activity)
                    .setTitle("Database Tables")
                    .setView(dialogView)
                    .setPositiveButton("Close") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setNegativeButton("Copy") { _, _ ->
                        copyToClipboard("Database Tables", tableList)
                    }
                    .create()
                    .show()
            }
        } catch (e: Exception) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }

    private fun backupDatabase(): String? {
        return try {
            val context = GrindrPlus.context
            val databases = context.databaseList()
            val grindrUserDb = databases.firstOrNull {
                it.contains("grindr_user") && it.endsWith(".db")
            } ?: return null

            val sourceDb = context.getDatabasePath(grindrUserDb)
            if (!sourceDb.exists()) return null

            // Create backup directory
            val backupDir = File(Environment.getExternalStorageDirectory(), "GrindrPlusBackup")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val backupFile = File(backupDir, "grindrplus_${System.currentTimeMillis()}.db")

            // Copy the database file
            sourceDb.copyTo(backupFile, overwrite = true)

            backupFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    // Add this command to backup the database
    @Command("backup_db", aliases = ["bdb"], help = "Backup the database to external storage")
    fun backupDatabaseCommand(args: List<String>) {
        try {
            val backupPath = backupDatabase()

            if (backupPath != null) {
                GrindrPlus.showToast(Toast.LENGTH_LONG,
                    "Database backed up successfully!\nLocation: $backupPath")
            } else {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Failed to backup database. Check if database exists.")
            }
        } catch (e: Exception) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error backing up database: ${e.message}")
        }
    }


    @Command("list_table", aliases = ["lt"], help = "List all rows from a specific table")
    fun listTable(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Please provide a table name.")
            return
        }

        val tableName = args[0]
        try {
            val query = "SELECT * FROM $tableName;"
            val rows = DatabaseHelper.query(query)

            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                val dialogView = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val tableContent = if (rows.isEmpty()) {
                    "No rows found in table $tableName."
                } else {
                    rows.joinToString("\n\n") { row ->
                        row.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                    }
                }

                val textView = AppCompatTextView(activity).apply {
                    text = tableContent
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setPadding(20, 20, 20, 20)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 20, 0, 0)
                    }
                }

                dialogView.addView(textView)

                AlertDialog.Builder(activity)
                    .setTitle("Table Content: $tableName")
                    .setView(dialogView)
                    .setPositiveButton("Close") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setNegativeButton("Copy") { _, _ ->
                        copyToClipboard("Table Content: $tableName", tableContent)
                    }
                    .create()
                    .show()
            }
        } catch (e: Exception) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }

    @Command("copy_table", aliases = ["ct"], help = "Copy table from Grindr db to GrindrPlus db")
    fun copyTable(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Usage: !copy_table table_name [new_table_name]")
            return
        }

        val sourceTable = args[0]
        val targetTable = args.getOrNull(1) ?: sourceTable

        try {
            // Step 1: Get table schema from source database
            val schemaQuery = "SELECT sql FROM sqlite_master WHERE type='table' AND name='$sourceTable';"
            val schemaResult = DatabaseHelper.query(schemaQuery)

            if (schemaResult.isEmpty()) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Table '$sourceTable' not found in source database")
                return
            }

            val createTableSQL = schemaResult[0]["sql"] as String
            val modifiedCreateSQL = createTableSQL.replace(sourceTable, targetTable)

            // Step 2: Create the table in GrindrPlus database
            val grindrPlusDb = getGrindrPlusDatabase()
            grindrPlusDb.execSQL(modifiedCreateSQL)

            // Step 3: Copy all data
            val dataQuery = "SELECT * FROM $sourceTable;"
            val sourceData = DatabaseHelper.query(dataQuery)

            if (sourceData.isNotEmpty()) {
                val columns = sourceData[0].keys.joinToString(", ")
                val placeholders = sourceData[0].keys.joinToString(", ") { "?" }

                sourceData.forEach { row ->
                    val values = row.values.map {
                        when (it) {
                            "NULL" -> null
                            else -> it
                        }
                    }.toTypedArray()

                    grindrPlusDb.insert(targetTable, null, values, columns, placeholders)
                }
            }

            grindrPlusDb.close()

            GrindrPlus.showToast(Toast.LENGTH_LONG,
                "✓ Table '$sourceTable' copied to '$targetTable' (${
                    sourceData.size} rows)")

        } catch (e: Exception) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error copying table: ${e.message}")
        }
    }

    // Helper function to get GrindrPlus database
    private fun getGrindrPlusDatabase(): SQLiteDatabase {
        val context = GrindrPlus.context
        return context.openOrCreateDatabase("Grindrplus.db", Context.MODE_PRIVATE, null)
    }

    // Helper extension function for easier insertion
    private fun SQLiteDatabase.insert(table: String, conflictAlgorithm: Int?, values: Array<Any?>,
                                      columns: String, placeholders: String): Long {
        val sql = "INSERT INTO $table ($columns) VALUES ($placeholders)"
        return if (conflictAlgorithm != null) {
            this.compileStatement(sql).apply {
                values.forEachIndexed { index, value ->
                    when (value) {
                        null -> bindNull(index + 1)
                        is Int -> bindLong(index + 1, value.toLong())
                        is Long -> bindLong(index + 1, value)
                        is Float -> bindDouble(index + 1, value.toDouble())
                        is Double -> bindDouble(index + 1, value)
                        is Boolean -> bindLong(index + 1, if (value) 1 else 0)
                        else -> bindString(index + 1, value.toString())
                    }
                }
            }.executeInsert()
        } else {
            this.execSQL(sql, values)
            -1 // Return -1 since we can't get rowId with execSQL
        }
    }


    @Command("list_databases", aliases = ["ldbs"], help = "List all database files in the app's files directory")
    fun listDatabases(args: List<String>) {
        try {
            val context = GrindrPlus.context
            val databases = context.databaseList()

            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                val dialogView = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val dbList = if (databases.isEmpty()) "No databases found." else databases.joinToString("\n")

                val textView = AppCompatTextView(activity).apply {
                    text = dbList
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setPadding(20, 20, 20, 20)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 20, 0, 0)
                    }
                }

                dialogView.addView(textView)

                AlertDialog.Builder(activity)
                    .setTitle("Database Files")
                    .setView(dialogView)
                    .setPositiveButton("Close") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setNegativeButton("Copy") { _, _ ->
                        copyToClipboard("Database Files", dbList)
                    }
                    .create()
                    .show()
            }
        } catch (e: Exception) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }
}
