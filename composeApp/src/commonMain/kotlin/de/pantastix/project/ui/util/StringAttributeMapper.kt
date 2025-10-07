package de.pantastix.project.ui.util

import androidx.compose.runtime.Composable
import de.pantastix.project.shared.resources.MR
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource

@Composable
fun getAttributeDisplayName(attribute: String): String {
    return stringResource(
        when (attribute) {
            "nameLocal" -> MR.strings.attribute_name
            "setName" -> MR.strings.attribute_set
            "currentPrice" -> MR.strings.attribute_price
            "ownedCopies" -> MR.strings.attribute_owned
            "language" -> MR.strings.attribute_language
            else -> MR.strings.unknown_attribute
        }
    )
}

fun getAttributeStringResource(attribute: String): StringResource {
    return when (attribute) {
        "nameLocal" -> MR.strings.attribute_name
        "setName" -> MR.strings.attribute_set
        "currentPrice" -> MR.strings.attribute_price
        "ownedCopies" -> MR.strings.attribute_owned
        "language" -> MR.strings.attribute_language
        else -> MR.strings.unknown_attribute
    }
}
