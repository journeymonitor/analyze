package com.journeymonitor.analyze.api

import components.CassandraRepositoryComponents
import controllers.Statistics
import play.api.ApplicationLoader.Context
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext}
import router.Routes

class AppLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application =
    new AppComponents(context).application
}

class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) with CassandraRepositoryComponents {

  lazy val applicationController = new controllers.Application()
  lazy val statisticsController = new Statistics(statisticsRepository)
  lazy val assets = new _root_.controllers.Assets(httpErrorHandler)

  override def router: Router = new Routes(
    httpErrorHandler,
    applicationController,
    assets,
    statisticsController
  )
}
