package ic.org.arm

import java.util.UUID
import kotlinx.collections.immutable.persistentListOf

/**
 * Represents a unique label in a code segment that references a string.
 *
 * Same-name labels are allowed thanks to each label havin a unique [id].
 */
data class StringData(val msg: String, val length: Int, val uid: String? = null) {
  val id = uid ?: UUID.randomUUID().toString().take(8)
  val label = Label("s_$id")
  val body = persistentListOf(
    label,
    Directive("word $length"),
    Directive("ascii\t\"$msg\"")
  )
}