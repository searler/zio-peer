package searler.zio_peer

sealed trait RoutingSpec[-E]

case object  SELF extends RoutingSpec[Any]
case object  MASTER extends RoutingSpec[Any]

trait AddressSpec[-E] extends RoutingSpec [E]{
  def matches(address: E): Boolean
}

case object ALL_SELF extends AddressSpec[Any] {
  override def matches(address: Any): Boolean = true
}

case object ALL extends AddressSpec[Any] {
  override def matches(address: Any): Boolean = true
}

case class Single[E](target: E) extends AddressSpec[E] {
  override def matches(address: E): Boolean = address == target
}

case class AllBut[E](target: E) extends AddressSpec[E] {
  override def matches(address: E): Boolean = address != target
}
