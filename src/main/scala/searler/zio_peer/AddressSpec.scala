package searler.zio_peer

trait AddressSpec[-E] {
  def matches(address: E): Boolean
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
