package models

import anorm._
import play.api.db.DB
import play.api.Play.current
import scala.util.Try

case class DashboardStatistics(userCount: Long,
                               advertiserCount: Long,
                               activeAdvertCount: Long,
                               totalRevenue: BigDecimal)

object Dashboard {
  def getStatistics:Try[DashboardStatistics] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from public.admin_get_dashboard_statistics()")
        .apply().map(row => DashboardStatistics(
        row[Long]("_user_count"),
        row[Long]("_advertiser_count"),
        row[Long]("_active_advert_count"),
        row[BigDecimal]("_total_revenue")
      )).head
    })
  }
}
