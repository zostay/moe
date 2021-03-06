package org.moe.runtime

import scala.collection.mutable.{HashMap,Map}

import org.moe.runtime.nativeobjects.MoePairObject

class MoeSignature(
    private val params: List[MoeParameter] = List()
  ) extends MoeObject {

  lazy val arity = params.length
  lazy val namedParameterMap: Map[String,MoeParameter] = Map(
    params.filter(_ match { 
      case (x: MoeNamedParameter) => true
      case _                      => false
    }).map(
      p => p.getKeyName -> p
    ):_*
  )

  def getParams = params

  def bindArgsToEnv (args: MoeArguments, env: MoeEnvironment) = {
    
    val r = env.getCurrentRuntime.get

    var extra: List[MoeObject] = List()

    for (i <- 0.until(arity)) {
      params(i) match {
        case MoePositionalParameter(name) => env.create(name, args.getArgAt(i).get)
        case MoeOptionalParameter(name) => args.getArgAt(i) match {
          case Some(a) => env.create(name, a)
          case None    => env.create(name, r.NativeObjects.getUndef)
        }
        case MoeSlurpyParameter(name) => env.create(
          name, 
          r.NativeObjects.getArray(args.slurpArgsAt(i):_*)
        )
        case MoeSlurpyNamedParameter(name) => env.create(
          name, 
          r.NativeObjects.getHash(args.slurpArgsAt(i).map(_.unboxToTuple.get):_*)
        )
        case _ => extra = args.getArgAt(i).get :: extra 
      }
    }

    for (arg <- extra) {
      arg match {
        case (a: MoePairObject) => {
          val k = a.key(r).unboxToString.get
          val p = namedParameterMap.get(k).getOrElse(
            throw new MoeErrors.MoeProblems("Could not find matching key for " + k)
          )
          env.create(p.getName, a.value(r))
        }
        case _ => throw new MoeErrors.MoeProblems("extra argument was not a pair")
      }
    }

  }
}
