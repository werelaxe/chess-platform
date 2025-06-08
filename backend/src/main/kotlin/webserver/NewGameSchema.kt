package webserver

import dialects.GameKind
import kotlinx.serialization.Serializable


@Serializable
data class NewGameSchema(val kind: GameKind)
