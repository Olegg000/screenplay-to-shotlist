package app.screenplay.service

import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.reader.pdf.PagePdfDocumentReader
import org.springframework.ai.reader.tika.TikaDocumentReader
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class DocumentParserService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun parseText(file: MultipartFile): String {
        val resource: Resource = file.resource

        val documents: List<Document> = when {
            file.originalFilename?.endsWith(".pdf", ignoreCase = true) == true -> {
                PagePdfDocumentReader(resource).read()
            }
            else -> {
                TikaDocumentReader(resource).read()
            }
        }

        log.info("Parsed document into {} block(s)", documents.size)

        return documents.joinToString(separator = "\n\n") { it.formattedContent }
    }
}
