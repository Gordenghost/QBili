package com.qbili.domain.model

private val imageTags = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val lazyImageSource = Regex("""\bdata-(?:src|original)\s*=\s*(?:(['"])(.*?)\1|([^\s>]+))""", RegexOption.IGNORE_CASE)
private val imageSource = Regex("""\s+src\s*=\s*(?:(['"])(.*?)\1|([^\s>]+))""", RegexOption.IGNORE_CASE)
private val imageSrcSet = Regex("""\s+srcset\s*=\s*(?:(['"]).*?\1|[^\s>]+)""", RegexOption.IGNORE_CASE)

fun articleDocument(
    title: String,
    author: String,
    content: String,
    textColor: String,
    backgroundColor: String,
    embeddedImages: Map<String, String> = emptyMap(),
): String {
    val readyContent = imageTags.replace(content) { match ->
        val source = imageUrl(match.value)
        if (source == null) match.value else {
            val url = (embeddedImages[source] ?: source).replace("'", "&#39;")
            match.value.replace(imageSource, "")
                .replace(imageSrcSet, "")
                .replace(Regex("""\s+loading\s*=\s*(['"]).*?\1""", RegexOption.IGNORE_CASE), "")
                .replaceFirst(Regex("<img", RegexOption.IGNORE_CASE), "<img src='$url' loading='eager'")
        }
    }
    return """<!doctype html><html lang="zh-CN"><head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=3">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: data:; style-src 'unsafe-inline'">
        <style>
          * { box-sizing: border-box; }
          body { margin: 0; padding: 16px; background: $backgroundColor; color: $textColor;
            font: 16px/1.7 sans-serif; overflow-wrap: anywhere; }
          h1 { font-size: 20px; line-height: 1.4; margin: 8px 0 16px; }
          h2 { font-size: 20px; }
          h3 { font-size: 18px; }
          h4, h5, h6 { font-size: 16px; }
          h2, h3, h4, h5, h6 { line-height: 1.4; margin: 20px 0 12px; }
          p { margin: 0 0 16px; white-space: pre-wrap; }
          ul, ol { margin: 0 0 16px; padding-left: 24px; }
          blockquote { margin: 12px 0; padding: 4px 12px; border-left: 3px solid #00a1d6; }
          .ql-align-center, .text-center { text-align: center; }
          .ql-align-right, .text-right { text-align: right; }
          .ql-size-huge { font-size: 1.8em; }
          .ql-size-large { font-size: 1.4em; }
          img, video { max-width: 100% !important; height: auto !important; }
          img { display: block; margin: 12px auto; }
          figure { max-width: 100%; margin: 16px 0; }
          pre { overflow-x: auto; white-space: pre-wrap; }
          a { color: #00a1d6; }
        </style></head><body>
        <h1>${title.escapeArticleText()}</h1>
        <p>${author.escapeArticleText()}</p>
        <article>$readyContent</article>
        </body></html>"""
}

fun articleImageUrls(content: String): List<String> = imageTags.findAll(content)
    .mapNotNull { imageUrl(it.value)?.takeIf { url -> url.startsWith("https://") } }
    .distinct().toList()

private fun imageUrl(tag: String): String? {
    val source = lazyImageSource.find(tag)?.let { it.groupValues[2].ifBlank { it.groupValues[3] } }
        ?.takeIf { it.isNotBlank() }
        ?: imageSource.find(tag)?.let { it.groupValues[2].ifBlank { it.groupValues[3] } }
            ?.takeIf { it.isNotBlank() }
        ?: return null
    return when {
        source.startsWith("//") -> "https:$source"
        source.startsWith("http://") -> "https://${source.removePrefix("http://")}"
        else -> source
    }.replace("&amp;", "&")
}

private fun String.escapeArticleText(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("'", "&#39;")
