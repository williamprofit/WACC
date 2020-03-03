package ic.org.jvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus
import java.util.LinkedList

interface JvmInstr {
  val code: String

  // override fun toString() = code
  companion object {
    fun inline(s: String) = object : JvmInstr {
      override val code = s
    }
  }
}

data class JvmLabel(val name: String) : JvmInstr {
  override val code = "$name:"
}

class JvmAsm private constructor(
  val instrs: PersistentList<JvmInstr>,
  val methods: PersistentSet<JvmAsm> = persistentSetOf()
) {
  constructor(a: JvmAsm) : this(a.instrs, a.methods)

  fun withMethod(m: WACCMethod): JvmAsm = JvmAsm(instrs, methods + m.asm)
  fun withMethods(ms: List<WACCMethod>) = JvmAsm(instrs, methods + ms.map { it.asm })

  fun combine(other: JvmAsm) = JvmAsm(instrs + other.instrs, methods + other.methods)

  operator fun plus(other: JvmAsm) = combine(other)
  operator fun plus(other: JvmInstr) = combine(instr(other))

  class BuilderScope {
    private val instructions = LinkedList<JvmAsm>()

    operator fun JvmInstr.unaryPlus() = instructions.addLast(instr(this))
    operator fun List<JvmInstr>.unaryPlus() = forEach { instructions.addLast(instr(it)) }
    operator fun JvmAsm.unaryPlus() = instructions.addLast(this)
    operator fun String.unaryPlus() = instructions.addLast(instr(JvmInstr.inline(this)))

    internal fun build() = instructions.fold(empty, JvmAsm::combine)

    fun withMethod(m: WACCMethod) = instructions.addLast(empty.withMethod(m))
    fun withMethods(ms: List<WACCMethod>) = instructions.addLast(empty.withMethods(ms))
  }

  companion object {
    fun instr(i: JvmInstr) = JvmAsm(persistentListOf(i))
    val empty = JvmAsm(persistentListOf())
    fun write(init: BuilderScope.() -> Unit) = BuilderScope().apply(init).build()
    operator fun invoke(init: BuilderScope.() -> Unit) = write(init)
  }
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@MustBeDocumented
annotation class JvmGenOnly
