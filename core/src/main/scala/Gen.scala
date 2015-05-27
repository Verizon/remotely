package remotely

private[remotely] object Gen extends MacrosCompatibility {
  /**
   * this just allows us to put a $signature into a quasi-quote.
   * implemented this way instead of by providing Liftable[Signature]
   * only because I gave up on trying to figure out the complex cake
   * of path-dependant types which is the current reflection api.
   */
  def liftSignature(c: Context)(signature: Signature): c.universe.Tree = {
    import c.universe._
    val s = signature
    val t: Tree = q"_root_.remotely.Signature(${s.name}, List(..${s.params.map(liftField(c)(_))}), ${liftType(c)(s.out)})"
    t
  }

  def liftField(c: Context)(field: Field[Any]): c.universe.Tree = {
    import c.universe._
    q"_root_.remotely.Field(${field.name}, ${liftType(c)(field.type_)})"
  }

  def liftType(c: Context)(type_ : Type[Any]): c.universe.Tree = {
    import c.universe._
    q"_root_.remotely.Type(${type_.name}, ${type_.isStream})"
  }
}
