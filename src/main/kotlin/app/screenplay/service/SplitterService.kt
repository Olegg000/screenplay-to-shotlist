package app.screenplay.service

import org.springframework.stereotype.Service
import java.util.regex.Pattern

/**
 * Splits raw screenplay text into scenes on standard scene headers
 * (ИНТ. / НАТ. / INT. / EXT.), keeping each header with its scene body.
 */
@Service
class SplitterService {
    private val sceneHeaderPattern: Pattern = Pattern.compile(
        "(?im)^((?:ИНТ|НАТ|EXT|INT)\\..*? - .*?$)"
    )

    fun splitToScenes(fullText: String): List<String> {
        val regex = "(?=${sceneHeaderPattern.pattern()})"
        return fullText.split(Regex(regex))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
