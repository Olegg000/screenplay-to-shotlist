package app.screenplay.controller

import app.screenplay.service.CsvExportService
import app.screenplay.service.DocumentAiService
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping
class MainController(
    private val documentAiService: DocumentAiService,
    private val csvExportService: CsvExportService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/all-to-csv", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun exportDefaultScript(@RequestParam("file") file: MultipartFile): ResponseEntity<InputStreamResource> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().build()
        }

        val analyzed = documentAiService.startScript(file)
        log.info("File analyzed, building the shot list table...")

        val csv = csvExportService.generateDirectorScriptCsv(analyzed)
        log.info("Shot list table built successfully")

        val headers = HttpHeaders()
        val originalFilename = file.originalFilename?.substringBefore('.') ?: "script"
        val csvFilename = "$originalFilename.csv"
        val encodedFilename = UriUtils.encode(csvFilename, StandardCharsets.UTF_8)
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$encodedFilename")

        return ResponseEntity
            .ok()
            .headers(headers)
            .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
            .body(InputStreamResource(csv))
    }
}
