package app.screenplay.service

import app.screenplay.dto.AnalyzedScene
import app.screenplay.dto.ShotData
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class DocumentAiService(
    private val documentParserService: DocumentParserService,
    private val splitterService: SplitterService,
    chatClientBuilder: ChatClient.Builder
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val chatClient = chatClientBuilder.build()

    fun startScript(file: MultipartFile): List<AnalyzedScene> {
        val fullText = documentParserService.parseText(file)
        val scenes = splitterService.splitToScenes(fullText)
        val batches = createBatches(scenes)

        log.info("Starting shot-list generation for {} scene(s) in {} batch(es)", scenes.size, batches.size)

        val allShots = mutableListOf<ShotData>()
        var sceneCounter = 0
        for ((batchIndex, batch) in batches.withIndex()) {
            val batchText = batch.joinToString("\n\n---- СЛЕДУЮЩАЯ СЦЕНА ----\n\n")
            val prompt = createDirectorPrompt(batchText, sceneCounter + 1)

            log.info("Requesting shots for batch {}/{}", batchIndex + 1, batches.size)
            val shotsFromBatch = chatClient.prompt()
                .user(prompt)
                .advisors(SimpleLoggerAdvisor({
                    ""
                }, {
                    "METADATA_TOKENS: " + it.metadata.usage.promptTokens.toString() + "," + it.metadata.usage.totalTokens.toString() + "," + it.metadata.usage.completionTokens.toString()
                }, 0))
                .call()
                .entity(Array<ShotData>::class.java)

            if (shotsFromBatch != null) {
                log.info("Batch {} returned {} shot(s)", batchIndex + 1, shotsFromBatch.size)
                allShots.addAll(shotsFromBatch.toList())
            } else {
                log.warn("Batch {} returned an empty response", batchIndex + 1)
            }
            sceneCounter += batch.size

            if (batch != batches.last()) {
                // Fixed pause between batches to stay under the OpenAI rate limit.
                // ponytail: naive fixed delay; upgrade to exponential backoff that
                // retries on HTTP 429 (Retry-After header) instead of always sleeping.
                log.info("Waiting {} ms before the next batch to respect rate limits", BATCH_DELAY_MS)
                Thread.sleep(BATCH_DELAY_MS)
            }
        }

        log.info("Shot-list generation finished, mapping shots back to scenes")

        return scenes.mapIndexed { index, sceneText ->
            AnalyzedScene(
                sceneNumber = index + 1,
                originalText = sceneText,
                shots = allShots.filter { it.sceneNumber == index + 1 }
            )
        }
    }

    private fun createBatches(scenes: List<String>, maxTokens: Int = 20000): List<List<String>> {
        val batches = mutableListOf<MutableList<String>>()
        if (scenes.isEmpty()) return batches

        var currentBatch = mutableListOf<String>()
        var currentBatchTokens = 0

        val promptTokens = 500
        val outputMultiplier = 1 // JSON output is usually several times larger than the input scene text.
        val safetyMargin = 1000
        val effectiveMaxTokens = maxTokens - promptTokens - safetyMargin

        for (scene in scenes) {
            val sceneTokens = scene.length / 4
            val sceneCost = sceneTokens * (1 + outputMultiplier) // Estimated input + output cost for this scene.

            // If a single scene is too large on its own, flush the current batch and split the scene.
            if (sceneCost > effectiveMaxTokens) {
                if (currentBatch.isNotEmpty()) {
                    batches.add(currentBatch)
                    currentBatch = mutableListOf()
                    currentBatchTokens = 0
                }
                val chunks = splitHugeScene(scene, effectiveMaxTokens / (1 + outputMultiplier))
                chunks.forEach { chunk -> batches.add(mutableListOf(chunk)) }
                continue
            }

            val currentBatchCost = currentBatchTokens * (1 + outputMultiplier)

            // If adding this scene would blow the budget, close the current batch and start a new one.
            if (currentBatchCost + sceneCost > effectiveMaxTokens && currentBatch.isNotEmpty()) {
                batches.add(currentBatch)
                currentBatch = mutableListOf(scene)
                currentBatchTokens = sceneTokens
            } else {
                currentBatch.add(scene)
                currentBatchTokens += sceneTokens
            }
        }

        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch)
        }

        log.info("Split screenplay into {} batch(es)", batches.size)
        return batches
    }

    private fun splitHugeScene(sceneText: String, maxChunkSizeInTokens: Int): List<String> {
        val maxChunkCharSize = maxChunkSizeInTokens * 4 // Convert token budget back into characters.
        log.warn("Scene too large, splitting into chunks of {} chars", maxChunkCharSize)
        return sceneText.chunked(maxChunkCharSize)
    }

    private fun createDirectorPrompt(batchText: String, startSceneNumber: Int): String {
        return """
        Ты — опытный режиссер-постановщик. Преврати КАЖДУЮ сцену из текста ниже в покадровую разбивку.
        Сцены разделены '--- СЛЕДУЮЩАЯ СЦЕНА ---'.
        Твой ответ ДОЛЖЕН БЫТЬ строго в формате единого JSON-массива.

        Каждый объект в массиве — это ОДИН кадр со следующей структурой:
        - `sceneNumber`: Int. Номер сцены, к которой относится кадр. Начни нумерацию с $startSceneNumber.
        - `shotNumberInScene`: Int. Номер кадра внутри его сцены.
        - `location`: String. Локация. Указывай только если это начало новой сцены.
        - `durationSec`: Int. Примерная длительность кадра в секундах.
        - `shotType`: String. План и способ съемки (например: "Кр. пл. статика", "Ср. пл.").

        - `description`: String. ВАЖНО: Это поле должно содержать **визуальное описание действия И все диалоги (реплики) персонажей**.
            Пример для `description`: "Шарик выходит из дома. МАТРОСКИН (сердито): Опять ты за свое!"

        - `sound`: String. ВАЖНО: Это поле **только для невербальных звуков**, важных для звукорежиссера.
            Пример для `sound`: "Шум ветра", "Скрип двери", "Тиканье часов". Если таких звуков нет, пропусти это поле.

        - `props`: String. Ключевой реквизит в кадре.
        - `equipment`: String. Особая техника.

        Текст для анализа:
        ---
        $batchText
        ---

        Верни только JSON-массив.
    """.trimIndent()
    }

    private companion object {
        const val BATCH_DELAY_MS = 10_000L
    }
}
