package com.prezdev.devtour.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.inputStream

@Serializable
data class DevTourStep(
    val id: String = UUID.randomUUID().toString(),
    val file: String? = null,
    val line: Int? = null,
    val description: String? = null,
    val summary: String? = null,
    val order: Int? = null,
    @SerialName("tourId") val tourId: String? = null
)

@Serializable
data class DevTourTour(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Untitled Tour",
    val steps: MutableList<DevTourStep> = mutableListOf()
)

@Serializable
data class DevTourFile(
    val tours: MutableList<DevTourTour> = mutableListOf(),
    var activeTourId: String? = null
) {
    fun activeTour(): DevTourTour? {
        if (tours.isEmpty()) return null
        val current = tours.firstOrNull { it.id == activeTourId }
        if (current != null) return current
        val first = tours.first()
        activeTourId = first.id
        return first
    }
}

object DevTourDataParser {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun load(path: Path?): DevTourFile {
        if (path == null || !path.exists()) {
            return DevTourFile()
        }

        return runCatching {
            path.inputStream().bufferedReader().use { reader ->
                val rawText = reader.readText()
                parse(rawText)
            }
        }.getOrElse { DevTourFile() }
    }

    fun save(path: Path, data: DevTourFile) {
        val parent = path.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }
        val normalized = normalizeForWrite(data)
        Files.writeString(path, json.encodeToString(normalized))
    }

    private fun parse(text: String): DevTourFile {
        val element = runCatching { json.parseToJsonElement(text) }.getOrElse { return DevTourFile() }
        return when (element) {
            is JsonArray -> convertArrayToTours(element)
            is JsonObject -> parseObject(element)
            else -> DevTourFile()
        }.normalize()
    }

    private fun parseObject(obj: JsonObject): DevTourFile {
        val toursElement = obj["tours"]
        return if (toursElement is JsonArray) {
            val tours = toursElement.mapIndexed { index, element ->
                decodeTour(element, index + 1)
            }.toMutableList()
            DevTourFile(
                tours = tours,
                activeTourId = obj["activeTourId"]?.jsonPrimitive?.contentOrNull
            )
        } else {
            DevTourFile()
        }
    }

    private fun convertArrayToTours(array: JsonArray): DevTourFile {
        val tour = DevTourTour(name = "Main Tour")
        tour.steps += array.mapIndexed { index, element ->
            decodeStep(element, index + 1, tour.id)
        }
        return DevTourFile(
            tours = mutableListOf(tour),
            activeTourId = tour.id
        )
    }

    private fun decodeTour(element: JsonElement, index: Int): DevTourTour {
        val obj = element as? JsonObject ?: return DevTourTour(name = "Tour $index")
        val providedId = obj["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString()
        val steps = obj["steps"]?.jsonArray?.mapIndexed { idx, stepElement ->
            decodeStep(stepElement, idx + 1, providedId)
        }?.toMutableList() ?: mutableListOf()

        return DevTourTour(
            id = providedId,
            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "Tour $index",
            steps = steps
        )
    }

    private fun decodeStep(element: JsonElement, position: Int, tourId: String): DevTourStep {
        val obj = element as? JsonObject ?: return DevTourStep(order = position, tourId = tourId)
        return DevTourStep(
            id = obj["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString(),
            file = obj["file"]?.jsonPrimitive?.contentOrNull,
            line = obj["line"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
            description = obj["description"]?.jsonPrimitive?.contentOrNull,
            summary = obj["summary"]?.jsonPrimitive?.contentOrNull,
            order = obj["order"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: position,
            tourId = obj["tourId"]?.jsonPrimitive?.contentOrNull ?: tourId
        )
    }

    private fun DevTourFile.normalize(): DevTourFile {
        val normalizedTours = tours.mapIndexed { index, tour ->
            val stableId = tour.id.ifBlank { UUID.randomUUID().toString() }
            val sortedSteps = tour.steps
                .mapIndexed { idx, step ->
                    step.copy(
                        id = step.id.ifBlank { UUID.randomUUID().toString() },
                        order = step.order ?: idx + 1,
                        tourId = step.tourId ?: stableId
                    )
                }
                .sortedWith(
                    compareBy<DevTourStep> { it.order ?: Int.MAX_VALUE }
                        .thenBy { it.file ?: "" }
                        .thenBy { it.line ?: Int.MAX_VALUE }
                )
                .toMutableList()

            tour.copy(
                id = stableId,
                name = tour.name.ifBlank { "Tour ${index + 1}" },
                steps = sortedSteps
            )
        }.toMutableList()

        val resolvedActive = when {
            normalizedTours.isEmpty() -> null
            normalizedTours.any { it.id == activeTourId } -> activeTourId
            else -> normalizedTours.first().id
        }

        return DevTourFile(
            tours = normalizedTours,
            activeTourId = resolvedActive
        )
    }

    private fun normalizeForWrite(data: DevTourFile): DevTourFile {
        data.tours.forEachIndexed { index, tour ->
            if (tour.name.isBlank()) {
                tour.name = "Tour ${index + 1}"
            }
            tour.steps.forEachIndexed { idx, step ->
                if (step.order == null) {
                    tour.steps[idx] = step.copy(order = idx + 1, tourId = tour.id)
                } else if (step.tourId == null) {
                    tour.steps[idx] = step.copy(tourId = tour.id)
                }
            }
        }
        if (data.activeTourId == null && data.tours.isNotEmpty()) {
            data.activeTourId = data.tours.first().id
        }
        return data
    }
}
