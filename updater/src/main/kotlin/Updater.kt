package de.pantastix

import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import kotlin.system.exitProcess
/**
 * Ein einfacher Logger, der alle Aktionen in eine Datei im Benutzerverzeichnis schreibt.
 * Dies ist entscheidend für die Fehlersuche, da der Updater ohne sichtbares Fenster läuft.
 */
object Logger {
    private val logDirectory = File(System.getProperty("user.home"), ".tcgm")
    private val logFile = File(logDirectory, "updater.log")

    init {
        try {
            if (!logDirectory.exists()) {
                logDirectory.mkdirs()
            }
            logFile.delete()
        } catch (e: Exception) {
            println("Konnte Log-Verzeichnis nicht initialisieren: ${e.message}")
        }
    }

    fun log(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        val logMessage = "[$timestamp] $message\n"
        try {
            logFile.appendText(logMessage)
            println(message)
        } catch (e: Exception) {
            println("Konnte nicht in die Log-Datei schreiben: ${e.message}")
        }
    }

    fun logError(e: Exception) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        log("FEHLER: ${e.message}\n$sw")
    }
}

class UpdaterUI : JFrame("TCGM Updater") {
    private val statusLabel = JLabel("Initialisiere Updater...", SwingConstants.CENTER)
    private val progressBar = JProgressBar(0, 100)

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(400, 120)
        setLocationRelativeTo(null) // Zentriert das Fenster
        isResizable = false

        val panel = JPanel(BorderLayout(10, 10))
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        panel.add(statusLabel, BorderLayout.NORTH)
        panel.add(progressBar, BorderLayout.CENTER)
        contentPane = panel
    }

    fun execute(downloadUrl: String, fileName: String) {
        isVisible = true
        val worker = UpdaterWorker(downloadUrl, fileName,
            onProgress = { progress, status ->
                SwingUtilities.invokeLater {
                    progressBar.value = progress
                    statusLabel.text = status
                }
            },
            onComplete = {
                // Nach erfolgreicher Installation schließt sich der Updater von selbst.
                exitProcess(0)
            }
        )
        worker.execute()
    }
}

class UpdaterWorker(
    private val downloadUrl: String,
    private val fileName: String,
    private val onProgress: (Int, String) -> Unit,
    private val onComplete: () -> Unit
) : SwingWorker<Unit, Unit>() {

    override fun doInBackground() {
        try {
            val tempDir = System.getProperty("java.io.tmpdir")
            val destinationFile = File(tempDir, fileName)

            Logger.log("Download-URL: $downloadUrl")
            onProgress(0, "Lade Update herunter...")

            val connection = URI(downloadUrl).toURL().openConnection()
            val totalBytes = connection.contentLengthLong
            var bytesDownloaded: Long = 0

            connection.getInputStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        if (totalBytes > 0) {
                            val progress = (bytesDownloaded * 100 / totalBytes).toInt()
                            onProgress(progress, "Lade herunter... ($progress%)")
                        }
                    }
                }
            }
            Logger.log("Download abgeschlossen.")
            onProgress(100, "Download abgeschlossen. Starte Installation...")

            val os = System.getProperty("os.name").lowercase()
            val command = when {
                os.contains("win") -> "msiexec /i \"${destinationFile.absolutePath}\" /qb"
                os.contains("mac") -> "open \"${destinationFile.absolutePath}\""
                else -> "sudo dpkg -i \"${destinationFile.absolutePath}\""
            }
            Logger.log("Führe Befehl aus: $command")
            val process = Runtime.getRuntime().exec(command)
            process.waitFor()
            Logger.log("Installer-Prozess beendet mit Exit-Code: ${process.exitValue()}")

        } catch (e: Exception) {
            Logger.logError(e)
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(null, "Update fehlgeschlagen: ${e.message}", "Fehler", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    override fun done() {
        onComplete()
    }
}

fun main(args: Array<String>) {
    Logger.log("Updater gestartet.")
    if (args.size < 2) {
        Logger.log("Fehler: Nicht genügend Argumente.")
        exitProcess(1)
    }
    val downloadUrl = args[0]
    val fileName = args[1]

    SwingUtilities.invokeLater {
        UpdaterUI().execute(downloadUrl, fileName)
    }
}