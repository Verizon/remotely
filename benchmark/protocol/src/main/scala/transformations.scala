package remotely
package example.benchmark

trait transformations {
  def fromSmallW(s: SmallW): Small = Small(s.alpha, s.omega)
  def toSmallW(s: Small): SmallW = SmallW(s.alpha, s.omega)

  def fromMediumW(m: MediumW): Medium = Medium(m.ay,
                                               m.bee,
                                               m.cee.map(fromSmallW),
                                               m.dee)

  def toMediumW(m: Medium): MediumW = MediumW(m.ay,
                                              m.bee,
                                              m.cee.map(toSmallW),
                                              m.dee)

  def fromLargeW(l: LargeW): Large = Large(l.one,
                                           l.two,
                                           l.three,
                                           l.four,
                                           l.five.map(fromMediumW),
                                           l.six.map(fromSmallW))

  def toLargeW(l: Large): LargeW = LargeW(l.one,
                                          l.two,
                                          l.three,
                                          l.four,
                                          l.five.map(toMediumW),
                                          l.six.map(toSmallW))



  def fromBigW(b: BigW): Big = Big(b.one)
  def toBigW(b: Big): BigW = BigW(b.one)
}

