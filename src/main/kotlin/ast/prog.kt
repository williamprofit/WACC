package ic.org.ast

import org.antlr.v4.runtime.tree.TerminalNode

// <program>
data class Prog(val funcs: List<Func>, val body: Stat)

// <func>
data class Func(
  val retType: Type,
  val ident: Ident,
  val params: List<Param>,
  val stat: Stat
)

// <param>
data class Param(val type: Type, val ident: Ident)

// <pair-elem>
sealed class PairElem {
  abstract val expr: Expr

  companion object {
    fun fst(expr: Expr) = Fst(expr)
    fun snd(expr: Expr) = Snd(expr)
  }
}

data class Fst internal constructor(override val expr: Expr) : PairElem() {
  override fun toString() = "fst $expr"
}

data class Snd internal constructor(override val expr: Expr) : PairElem() {
  override fun toString() = "snd $expr"
}

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class Ident(val name: String) {
  constructor(node: TerminalNode) : this(node.text)

  override fun toString() = name
}
