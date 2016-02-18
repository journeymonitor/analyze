package com.journeymonitor.analyze.common.models

case class StatisticsModel(testresultId: String,
                           testresultDatetimeRun: java.util.Date,
                           runtimeMilliseconds: Int,
                           numberOf200: Int,
                           numberOf400: Int,
                           numberOf500: Int) extends Model
