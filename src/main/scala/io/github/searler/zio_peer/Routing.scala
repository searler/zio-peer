package io.github.searler.zio_peer

trait Routing[-E] {
  def matches(address: E): Boolean
}

case object ALL extends Routing[Any] {
  override def matches(address: Any): Boolean = true
}

case object IGNORE extends Routing[Any] {
  override def matches(address: Any): Boolean = false
}

case class Single[E](target: E) extends Routing[E] {
  override def matches(address: E): Boolean = address == target
}

case class AllBut[E](target: E) extends Routing[E] {
  override def matches(address: E): Boolean = address != target
}
