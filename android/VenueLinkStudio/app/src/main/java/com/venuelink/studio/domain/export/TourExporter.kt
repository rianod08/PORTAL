package com.venuelink.studio.domain.export

import com.venuelink.studio.data.model.TourExport
import com.venuelink.studio.data.model.ValidationResult
import com.venuelink.studio.data.model.ValidationSeverity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object TourExporter {
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun exportTourJson(export: TourExport): String = json.encodeToString(export)

    fun importTourJson(text: String): Result<TourExport> = runCatching { json.decodeFromString<TourExport>(text) }

    fun validateTourExport(text: String): ValidationResult =
        importTourJson(text).fold(
            onSuccess = { ValidationResult(true, "Tour JSON is valid") },
            onFailure = { ValidationResult(false, "This tour JSON could not be read.", ValidationSeverity.Error) },
        )
}
