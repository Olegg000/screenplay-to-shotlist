package app.screenplay.service

import app.screenplay.dto.AnalyzedScene
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream

@Service
class CsvExportService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generateDirectorScriptCsv(analyzedScenes: List<AnalyzedScene>): ByteArrayInputStream {
        val sb = StringBuilder()
        appendCsvHeader(sb)

        var globalShotCounter = 1
        for (scene in analyzedScenes) {
            log.debug("Writing scene {}", scene.sceneNumber)
            for (shot in scene.shots) {
                sb.append(globalShotCounter++).append(';')
                sb.append(scene.sceneNumber).append(';')
                sb.append(shot.shotNumberInScene).append(';')
                sb.append(escapeCsvField(shot.location)).append(';')
                sb.append(shot.durationSec).append(" сек.;")
                sb.append(escapeCsvField(shot.shotType)).append(';')
                sb.append(escapeCsvField(shot.description)).append(';')
                sb.append(escapeCsvField(shot.sound)).append(';')
                sb.append(escapeCsvField(shot.props)).append(';')
                sb.append(escapeCsvField(shot.equipment)).append('\n')
            }
        }

        // Prepend a UTF-8 BOM so Excel opens the Cyrillic CSV with the correct encoding.
        val csvBytes = "\uFEFF".toByteArray(Charsets.UTF_8) + sb.toString().toByteArray(Charsets.UTF_8)
        return ByteArrayInputStream(csvBytes)
    }

    private fun appendCsvHeader(sb: StringBuilder) {
        sb.append("№ Кадра (общ.);№ Сцены;№ Кадра (в сцене);Локация;ХРН;План и способ съемки;Содержание кадра;Звук;Реквизит;Техника\n")
    }

    private fun escapeCsvField(field: String?): String {
        if (field == null) {
            return ""
        }
        var escaped = field.replace("\"", "\"\"")
        if (escaped.contains(';') || escaped.contains('\n') || escaped.contains(',')) {
            escaped = "\"$escaped\""
        }
        return escaped
    }
}
