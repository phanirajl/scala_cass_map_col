
import com.datastax.driver.core.{Cluster, LocalDate, Row}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters
import scala.collection.JavaConverters._

/*
 Immutable Map myJavaMap.asScala.toMap
 Mutable Map   myJavaMap.asScala
*/
object ScalaCassMapCol extends App {

  object slog extends Serializable {
    @transient lazy val log = LoggerFactory.getLogger(getClass.getName)
  }

  case class bars(ticker_id :Int,
                  ddate     :LocalDate,
                  tdBars    :Seq[(String,Map[String,String])]){
    override def toString = "ticker_id="+ddate+"  ["+ddate+"]" + tdBars.toString()
    def getBarByName(barName : String) = {
      tdBars.filter(p => (p._1==barName)).headOption.get._2
    }
  }

  val rowToBars = (row : Row, colNames : Seq[String]) => {
    new bars(
      row.getInt("ticker_id"),
      row.getDate("ddate"),
      for (cn <- colNames) yield
        (cn,row.getMap(cn, classOf[String], classOf[String]).asScala.toMap)
    )
  }

  slog.log.debug("BEGIN")
  val node: String = "193.124.112.90"
  private val cluster = Cluster.builder().addContactPoint(node).build()
  val session = cluster.connect()

  val queryTicksCountTotal =
    """ select *
          from mts_bars.td_bars_3600 """

  val pqueryTicksCountTotal = session.prepare(queryTicksCountTotal).bind()
  val bound = pqueryTicksCountTotal

  /**
    * Get meta information about columns in this DataSet.
    * Column names by pattern bar_x (where x from 1 to N) for MAP fields.
    */
  val colDef = session.execute(bound).getColumnDefinitions
  val colsBarsNames :Seq[String] = for (thisColumn <- colDef.asList().asScala if thisColumn.getName().substring(0,3) == "bar") yield
    thisColumn.getName()

  val dsTicksCntTotalRow : Seq[bars] = JavaConverters.asScalaIteratorConverter(session.execute(bound).all().iterator())
    .asScala
    .toSeq.map(rowToBars(_,colsBarsNames))
    .sortBy(ft => ft.ticker_id)(Ordering[Int].reverse)



  slog.log.debug("===================================================================")
  slog.log.debug("ROWS SIZE="+dsTicksCntTotalRow.size)
  slog.log.debug(" ")
  for (rowTicksCntTotalRow <- dsTicksCntTotalRow){
    slog.log.debug("ticker_id = "+rowTicksCntTotalRow.ticker_id+" for ["+rowTicksCntTotalRow.ddate+"]")
    slog.log.debug("  BARS COUNT = "+rowTicksCntTotalRow.tdBars.size)
    slog.log.debug("    BARS WITH DATA = "+rowTicksCntTotalRow.tdBars.filter(p => (p._2.nonEmpty)).size)
    /**
      * local loop by nonEmpty bars.
      */
      for(neBar <- rowTicksCntTotalRow.tdBars.filter(p => (p._2.nonEmpty))){
        slog.log.debug("      ("+neBar._1+") = "+neBar._2)
      }
    slog.log.debug(" ")
  }
  slog.log.debug("===================================================================")

  /**
    * Now we can take one random bar_x from presented and save it into cassandra.
    */
  val b_tickerId : Int = dsTicksCntTotalRow(2).ticker_id
  val bOneName :String = "bar_1"
  val b : Map[String,String] = dsTicksCntTotalRow(2).getBarByName(bOneName)
  slog.log.debug("b_tickerId = "+b_tickerId+" ("+bOneName+") ="+b)


  val querySaveCountTotal =
    """ insert into mts_bars.td_bars_3600(ticker_id,ddate,  bar_1,bar_2,bar_3,bar_4) values(?,?,  ?,?,?,?) """
  val pquerySaveCountTotal = session.prepare(querySaveCountTotal)

  val boundSaveCountTotal = pquerySaveCountTotal.bind()
    .setInt("ticker_id", dsTicksCntTotalRow(2).ticker_id)
    .setDate("ddate", dsTicksCntTotalRow(2).ddate)
    .setMap("bar_1",dsTicksCntTotalRow(2).getBarByName("bar_1").asJava)
    .setMap("bar_2",dsTicksCntTotalRow(2).getBarByName("bar_1").asJava)
    .setMap("bar_3",b.asJava)
    .setMap("bar_4",b.asJava)

  val rsBar = session.execute(boundSaveCountTotal).one()

  session.close()
}
