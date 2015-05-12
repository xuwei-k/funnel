package funnel
package chemist

import journal.Logger
import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import funnel.Monitoring
import funnel.http.MonitoringServer
import scalaz.==>>
import scalaz.std.string._
import java.net.URI
import org.scalactic.TypeCheckedTripleEquals

class ShardingSpec extends FlatSpec with Matchers with TypeCheckedTripleEquals {

  import Sharding.Distribution

  implicit lazy val log: Logger = Logger("chemist-spec")

  implicit def tuple2target(in: (String,String)): Target =
    Target(in._1, new URI(in._2))

  def fakeFlask(id: String) = Flask(FlaskID(id), Location.localhost, Location.telemetryLocalhost)

  val d1: Distribution = ==>>(
    (fakeFlask("a"), Set(("z","http://one.internal"))),
    (fakeFlask("d"), Set(("y","http://two.internal"), ("w","http://three.internal"), ("v","http://four.internal"))),
    (fakeFlask("c"), Set(("x","http://five.internal"))),
    (fakeFlask("b"), Set(("z","http://two.internal"), ("u","http://six.internal")))
  )

  val i1: Set[Target] = Set(
    ("w", "http://three.internal"),
    ("u", "http://eight.internal"),
    ("v", "http://nine.internal"),
    ("z", "http://two.internal")
  )

  val i2: Set[Target] = Set(
    ("w", "http://alpha.internal"),
    ("u", "http://beta.internal"),
    ("v", "http://omega.internal"),
    ("z", "http://gamma.internal"),
    ("p", "http://zeta.internal"),
    ("r", "http://epsilon.internal"),
    ("r", "http://theta.internal"),
    ("r", "http://kappa.internal"),
    ("z", "http://omicron.internal")
  )

  it should "correctly sort the map and return the flasks in order of their set length" in {
    Sharding.shards(d1).map(_.id.value) should equal (Seq("a", "c", "b", "d"))
  }

  it should "snapshot the exsiting shard distribution" in {
    Sharding.sorted(d1).map(_._1.id.value) should equal (Seq("a", "c", "b", "d"))
  }

  it should "correctly remove urls that are already being monitored" in {
    Sharding.deduplicate(i1)(d1) should equal ( Set[Target](
      ("v", "http://nine.internal"),
      ("u", "http://eight.internal"))
    )
  }

  it should "correctly calculate how the new request should be sharded over known flasks" in {
    EvenSharding.calculate(i1)(d1).map {
      case (x,y) => x.id.value -> y
    }.toSet should === (Set(
                          "a" -> Target("u",new URI("http://eight.internal")),
                          "c" -> Target("v",new URI("http://nine.internal"))))

    EvenSharding.calculate(i2)(d1).map(_._2).toSet should === (Set(
                                                                 Target("v",new URI("http://omega.internal")),
                                                                 Target("w",new URI("http://alpha.internal")),
                                                                 Target("r",new URI("http://epsilon.internal")),
                                                                 Target("z",new URI("http://gamma.internal")),
                                                                 Target("u",new URI("http://beta.internal")),
                                                                 Target("z",new URI("http://omicron.internal")),
                                                                 Target("r",new URI("http://kappa.internal")),
                                                                 Target("r",new URI("http://theta.internal")),
                                                                 Target("p",new URI("http://zeta.internal"))))
  }
}
