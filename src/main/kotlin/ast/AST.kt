package ic.org.ast

import antlr.WACCParser.*
import arrow.core.Validated.Valid
import arrow.core.getOrElse
import arrow.core.invalid
import arrow.core.toOption
import arrow.core.valid
import ic.org.*
import ic.org.grammar.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus

fun FuncContext.asAst(gScope: GlobalScope): Parsed<Func> {
  val ident = Ident(this.ID().text)
  val funcScope = FuncScope(ident, gScope)
  // TODO we need to infer pair return types possibly
  val type = type().asAst()

  if (type !is Valid) return type.errors.invalid()
  val params = param_list().toOption().fold({
    emptyList<Parsed<Param>>()
  }, { list ->
    list.param().map { it.asAst(funcScope) }
  })
  val stat = stat().asAst(funcScope)

  return if (params.areAllValid && stat is Valid)
    Func(type.a, ident, params.valids, stat.a)
      .also { gScope.addFunction(startPosition, it) }
      .valid()
  else
    (type.errors + params.errors + stat.errors).invalid()
}

private fun ParamContext.asAst(scope: Scope): Parsed<Param> {
  TODO("not implemented")
}

internal fun TypeContext.asAst(): Parsed<Type> =
  when {
    base_type() != null -> base_type().asAst().valid()
    pair_type() != null -> pair_type().asAst()
    array_type() != null -> array_type().asAst()
    else -> NOT_REACHED()
  }

private fun Base_typeContext.asAst(): BaseT = when (this) {
  is IntBaseTContext -> IntT
  is BoolBaseTContext -> BoolT
  is CharBaseTContext -> CharT
  is StringBaseTContext -> StringT
  else -> NOT_REACHED()
}

private fun Pair_typeContext.asAst(): Parsed<PairT> {
  val fstType = pair_elem_type(0).asAst()
  val sndType = pair_elem_type(1).asAst()
  return if (fstType is Valid && sndType is Valid)
    PairT(fstType.a, sndType.a).valid()
  else
    (fstType.errors + sndType.errors).invalid()
}

// TODO When checking Types: If NDPairT, we need to recurse to find the right Pair Type!
private fun Pair_elem_typeContext.asAst(): Parsed<Type> =
  when (this) {
    is BaseTPairElemContext -> base_type().asAst().valid()
    is ArrayPairElemContext -> array_type().asAst()
    // Pair type not defined yet, it must be inferred by the caller
    else -> AnyPairTs().valid()
    //is PairPairElemContext -> NDPairT.valid()
    //else -> NOT_REACHED()
  }

private fun Array_typeContext.asAst(): Parsed<ArrayT> {
  fun recurseArrayT(arrayT: Array_typeContext, currentDepth: Int): Parsed<Pair<Type, Int>> =
    when (arrayT) {
      is ArrayOfArraysContext -> recurseArrayT(arrayT.array_type(), currentDepth + 1)
      is ArrayOfBaseTContext -> arrayT.base_type().asAst().valid().map { it to currentDepth }
      is ArrayOfPairsContext -> arrayT.pair_type().asAst().map { it to currentDepth }
      else -> NOT_REACHED()
    }
  return recurseArrayT(this, 1).map { (type, depth) -> ArrayT.make(type, depth) }
}

/**
 * Entry level of recursive AST conversion. Takes a [Scope], [gScope] or creates a [GlobalScope]
 * by default, from which all scopes (except [FuncScope]s] inherit from.
 */
fun ProgContext.asAst(gScope: GlobalScope = GlobalScope()): Parsed<Prog> {
  val funcs = func().map { it.asAst(gScope) }
  val antlrStat = stat()
  // TODO rewrite syntactic error message with this.startPosition
    ?: return persistentListOf(SyntacticError("Malformed program at $text")).invalid()
  val stat = antlrStat.asAst(gScope)

  return if (funcs.areAllValid && stat is Valid)
    Prog(funcs.valids, stat.a).valid()
  else
    (funcs.errors + stat.errors).invalid()
}

internal fun Assign_lhsContext.asAst(scope: Scope): Parsed<AssLHS> = when (this) {
  is LHSIdentContext -> scope[ID().text].fold({
    UndefinedIdentifier(startPosition, ID().text).toInvalidParsed()
  }, { IdentLHS(it).valid() })

  is LHSArrayElemContext -> scope[array_elem().ID().text].fold({
    UndefinedIdentifier(startPosition, array_elem().text).toInvalidParsed()
  }, { variable ->
    val exprs = array_elem().expr().map { it.asAst(scope) }
    if (exprs.areAllValid)
      ArrayElemLHS(ArrayElem(variable.ident, exprs.valids), variable).valid()
    else
      exprs.errors.invalid()
  })

  // TODO revisit pair_elem().text vs ID().text
  is LHSPairElemContext -> pair_elem().expr().asAst(scope)
    .validate(
      //{ it is IdentExpr },
      { it.type is PairT },
      { TypeError(startPosition, AnyPairTs(), it.type, pair_elem().text) })
    .flatMap { pairIdent ->
      scope[pair_elem().expr().text].fold({
        UndefinedIdentifier(startPosition, pair_elem().text).toInvalidParsed()
      }, { vari ->
        // vari.type as PairT is a safe cast because it was checked in the validate() clause.
        if (pair_elem().FST() != null)
          PairElemLHS(Fst(pairIdent), vari, vari.type as PairT)
        else {
          PairElemLHS(Snd(pairIdent), vari, vari.type as PairT)
        }.valid()
      })
    }

  else -> NOT_REACHED()
}

internal fun Assign_rhsContext.asAst(scope: Scope): Parsed<AssRHS> {
  when {
    array_lit() != null -> {
      val tokExprs = array_lit().expr()
      if (tokExprs.isEmpty()) {
        return ArrayLit(emptyList(), NDArrayT()).valid()
      }

      // Transform all the expressions to ASTs
      val exprs = tokExprs.map { it.asAst(scope) }
      if (!exprs.areAllValid) {
        return exprs.errors.invalid()
      }
      val valid = exprs.valids

      // Make sure expressions all have the same type
      val t = valid[0].type
      for (e in valid) {
        if (e.type != t) {
          return TypeError(tokExprs[0].startPosition, t, e.type, "array building")
            .toInvalidParsed()
        }
      }
      val arrayT = ArrayT.make(valid.first().type, 1)
      return ArrayLit(valid, arrayT).valid()
    }
    NEWPAIR() != null -> return combine(expr(0).asAst(scope), expr(1).asAst(scope)) { e1, e2 ->
      Newpair(e1, e2)
    }
    pair_elem() != null ->
      // TODO PICKUP WHAT ABOUT NESTED PAIRS AND ARRAYS
      return pair_elem().expr().asAst(scope)
        .validate(
          { it !is NullPairLit }) {
          NullPairError(pair_elem().expr().startPosition)
        }
        .validate({ it.type is PairT }) {
          TypeError(pair_elem().expr().startPosition, AnyPairTs(), it.type, "pair-elem")
        }
        .map {
          // Safe cast because it was checked in the above validate() clause
          val pairT = it.type as PairT
          if (pair_elem().FST() != null)
            PairElemRHS(Fst(it), pairT)
          else
            PairElemRHS(Snd(it), pairT)
        }

    CALL() != null -> {
      // Make ASTs out of all the args
      val args: List<Parsed<Expr>> =
        arg_list()?.expr().toOption().getOrElse { emptyList() }
          .map { it.asAst(scope) }
      if (!args.areAllValid) {
        return args.errors.invalid()
      }

      // TODO make sure arg types are good

      val id = ID().text
      return scope.globalFuncs[Ident(id)].toOption()
        .fold(
          { UndefinedIdentifier(ID().position, id).toInvalidParsed() },
          { Call(it, args.valids).valid() })
    }

    expr() != null -> {
      return expr(0).asAst(scope).map { ExprRHS(it) }
    }
    else -> NOT_REACHED()
  }
}
