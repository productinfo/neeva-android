package com.neeva.app.ui.widgets

import android.graphics.Typeface
import android.text.style.CharacterStyle
import android.text.style.StyleSpan
import android.text.style.URLSpan
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.text.HtmlCompat
import com.neeva.app.ui.LightDarkPreviewContainer

/**
 * Converts an HTML string into an [AnnotatedString] for display via Compose.
 *
 * Any links in the HTML are placed into the [AnnotatedString] with tags and annotations for the
 * links that match the href value of the anchor tag, allowing the [onClick] handler to do something
 * when a particular link is clicked.
 *
 * You may find it useful to pass in a [textStyle] that passes along [LocalContentColor.current]
 * as its color.  This is because the [ClickableText] uses the non-Material 3 [BasicText] composable
 * and doesn't correctly pull the local color.
 */
@Composable
fun AnnotatedSpannable(
    rawHtml: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    onClick: (annotatedString: AnnotatedString, offset: Int) -> Unit = { _, _ -> }
) {
    data class SpanInfo(
        val style: CharacterStyle,
        val start: Int,
        val end: Int
    )

    val spannable = HtmlCompat.fromHtml(rawHtml, 0)
    val annotatedString = buildAnnotatedString {
        withStyle(textStyle.toSpanStyle()) {
            append(spannable.toString())

            spannable
                .getSpans(0, spannable.length, CharacterStyle::class.java)
                .map {
                    // Figure out what each of the different Spans cover.
                    val start = spannable.getSpanStart(it)
                    val end = spannable.getSpanEnd(it)
                    SpanInfo(it, start, end)
                }
                .forEach {
                    // Convert each individual Span into an annotation on the AnnotatedString.
                    when (it.style) {
                        is URLSpan -> {
                            this.addStyle(
                                SpanStyle(textDecoration = TextDecoration.Underline),
                                it.start, it.end
                            )

                            this.addStringAnnotation(
                                tag = it.style.url,
                                annotation = it.style.url,
                                it.start, it.end
                            )
                        }

                        is StyleSpan -> {
                            val spanStyle = when (it.style.style) {
                                Typeface.NORMAL -> SpanStyle(fontStyle = FontStyle.Normal)
                                Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                                Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)

                                Typeface.BOLD_ITALIC -> SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic
                                )

                                else -> null
                            }
                            spanStyle?.let { spanStyle ->
                                this.addStyle(spanStyle, it.start, it.end)
                            }
                        }

                        else -> {
                            // Add support for more HTML tags as needed.
                        }
                    }
                }
        }
    }

    ClickableText(
        text = annotatedString,
        style = textStyle,
        modifier = modifier,
        onClick = { onClick(annotatedString, it) }
    )
}

@Preview
@Composable
internal fun AnnotatedSpannablePreview() {
    val rawHtml = "This is a <b>TOTALLY</b> random <i>string</i> that <b><i>might</b></i> " +
        "have a <a href=\"https://www.example.com/\"><b>not</b> suspicious link</a> in it."
    LightDarkPreviewContainer {
        AnnotatedSpannable(
            rawHtml = rawHtml,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            onClick = { _, _ -> }
        )
    }
}