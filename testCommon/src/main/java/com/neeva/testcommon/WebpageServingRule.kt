package com.neeva.testcommon

import android.net.Uri
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.URLConnection
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import timber.log.Timber

/**
 * Starts up a server on the Android device that serves web pages out of the app's assets.
 *
 * An alternative to using this class would be to have devs and CircleCI start up a local webserver,
 * but having this be part of test setup _probably_ makes it easier for new people to jump in.
 */
class WebpageServingRule : TestRule {
    companion object {
        private const val LOCAL_TEST_URL = "http://127.0.0.1:8000"

        /** Returns the URL to load to be served the given [filename] from the assets. */
        fun urlFor(filename: String): String = "$LOCAL_TEST_URL/$filename"
    }

    inner class ServingThread : Runnable, AutoCloseable {
        private val thread: Thread = Thread(this)

        init {
            thread.start()
        }

        @Throws(IOException::class)
        override fun run() {
            val serverSocket = ServerSocket(8000)

            while (true) {
                serverSocket.accept().use { socket ->
                    try {
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val writer = BufferedOutputStream(socket.getOutputStream())

                        // Read all the HTTP headers being sent.  We only care about the first line,
                        // which should indicate what file the client is requesting.
                        val firstLine: String? = reader.readLine()
                        var ignored = reader.readLine()
                        while (ignored.isNotEmpty()) {
                            ignored = reader.readLine()
                        }

                        // Figure out what file to load up using the first line of the request,
                        // which should take the form of:
                        // GET /file_being_requested.html HTTP/1.1
                        val filename: String = firstLine
                            ?.takeIf { it.startsWith("GET") }
                            ?.split(' ')
                            ?.getOrNull(1)
                            ?.drop(1) // Drop the leading "/" from the path.
                            ?.let { Uri.parse(it).path.takeUnless { path -> path.isNullOrEmpty() } }
                            ?: "index.html"

                        try {
                            // Try to load the file up from the assets.
                            val assets = InstrumentationRegistry.getInstrumentation().context.assets
                            val assetFiles = assets.list("html")

                            val loadFilename = if (assetFiles?.any { it == filename } == true) {
                                filename
                            } else if (assetFiles?.any { it == "$filename.html" } == true) {
                                Timber.w("Redirecting $filename -> $filename.html")
                                "$filename.html"
                            } else {
                                throw FileNotFoundException()
                            }

                            val bytes = assets.open("html/$loadFilename").buffered().use {
                                it.readBytes()
                            }

                            // Send the page if we found the file.
                            val mimeType = URLConnection.guessContentTypeFromName(loadFilename)
                            val output =
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: ${mimeType}\r\n" +
                                    "Content-Length: ${bytes.size}\r\n\r\n"
                            writer.write(output.toByteArray().plus(bytes))
                        } catch (e: FileNotFoundException) {
                            writer.write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
                        } catch (throwable: IOException) {
                            Timber.e(
                                t = throwable,
                                message = "Exception while serving file"
                            )
                            writer.write("HTTP/1.1 500 Not Implemented\r\n\r\n".toByteArray())
                        }

                        writer.flush()
                        writer.close()
                    } catch (throwable: Exception) {
                        Timber.e(
                            t = throwable,
                            message = "Exception caught for this socket"
                        )
                    }
                }
            }
        }

        override fun close() {
            thread.interrupt()
        }
    }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                ServingThread().use {
                    base.evaluate()
                }
            }
        }
    }
}
