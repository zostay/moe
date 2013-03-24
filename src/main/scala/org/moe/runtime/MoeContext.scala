package org.moe.runtime

abstract class MoeContext

case class MoeNoContext()  extends MoeContext
case class MoeIntContext() extends MoeContext
case class MoeNumContext() extends MoeContext
case class MoeStrContext() extends MoeContext
