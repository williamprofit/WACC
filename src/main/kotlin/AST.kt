package ic.org

import antlr.WACCParser
import arrow.core.Validated.*
import arrow.core.invalid
import arrow.core.valid
import ic.org.grammar.Func
import ic.org.grammar.Ident
import ic.org.grammar.Param
import ic.org.grammar.Prog
import ic.org.grammar.Stat
import ic.org.grammar.Type
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus

fun WACCParser.FuncContext.asAst(): Parsed<Func> {
  val type = this.type().asAst()
  val ident = Ident(this.ID().text).valid() // TODO are there any checks on identifiers needed
  val params = param_list().param().map { it.asAst() }
  val stat = stat().asAst()

  //TODO("Is this func valid? We probably need to make checks on the stat")

  return if (params.allValid && ident is Valid && type is Valid && stat is Valid) {
    val validParams = params.map { (it as Valid).a }
    Func(type.a, ident.a, validParams, stat.a).valid()
  } else {
    (type.errors + ident.errors + params.errors + stat.errors).invalid()
  }
}

private fun WACCParser.ParamContext.asAst(): Parsed<Param> {
  TODO("not implemented")
}

private fun WACCParser.TypeContext.asAst(): Parsed<Type> {
  TODO("not implemented")
}

fun WACCParser.ProgContext.asAst(): Parsed<Prog> {
  //if (this.exception.localizedMessage != null) {
  //  println(exception.localizedMessage)
  //  return persistentListOf(SyntacticError(this.exception.localizedMessage)).invalid()
  //}

  val funcs = this.func().map { it.asAst() }
  val stat = this.stat().asAst()

  TODO("Is this Prog valid?")

  return if (funcs.allValid && stat is Valid) {
    val validFuncs = funcs.map { (it as Valid).a }
    Prog(validFuncs, stat.a).valid()
  } else {
    funcs.errors.plus(stat.errors).invalid()
  }
}

private fun WACCParser.StatContext.asAst(): Parsed<Stat> = TODO()
