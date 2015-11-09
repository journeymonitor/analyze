import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.spark.sql._
import com.datastax.spark.connector._
import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonAST._
import org.json4s.DefaultFormats
import org.json4s.native.JsonParser

/*

Goal: For each testcase, show a diagram with the TTFB of each
      page that was requested in the last N testruns:

#          #*
# +   # +  #* o
#*+   #*+  #*+o
#*+   #*+  #*+o
123   123  1234
tr1   tr2  tr3

CREATE TABLE source_data.testresults (
    testcase_id text,
    datetime_run timestamp,
    testresult_id text,
    har text,
    is_analyzed boolean,
    PRIMARY KEY (testcase_id, datetime_run)
);

CREATE TABLE source_data.ttfbs (
    testresult_id text,
    page_num int,
    url text,
    ttfb int,
    PRIMARY KEY (testresult_id)
);

SELECT testresult_id FROM testresults WHERE testcase_id = 'abcd' ORDER BY datetime_run DESC LIMIT 10;

foreach (testresult_id) ->
  SELECT page_num, url, ttfb FROM ttfbs WHERE testresult_id = 'xyz'

  foreach (page_num, url, ttfb) ->
    DRAW CHART for testresult_id

 */

object SimpleApp {
  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Simple Application")
    conf.set("spark.cassandra.connection.host", "127.0.0.1")
    val sc = new SparkContext("spark://127.0.0.1:7077", "SimpleApp", conf)
    val sqlContext = new org.apache.spark.sql.SQLContext(sc)

    val rowRDD = sc.cassandraTable("source_data", "testresults")

    // Create RDD with a tuple of one Testresult ID to one HAR content per entry
    val testresultIdToHarRDD =
      rowRDD.map(
        row => {
          (row.get[String]("testresult_id"), row.get[String]("har"))
        }
      )

    // Create RDD with a tuple of one Testresult ID to one JValue representing the HAR per entry
    val testresultIdToJsonRDD = testresultIdToHarRDD.map(testresultIdToHar => {
      (testresultIdToHar._1, parse(testresultIdToHar._2, false))
    })

    // Create RDD with a tuple of one Testresult ID to one URL to one wait timing of the first request in each HAR per entry
    val testresultIdToUrlToFirstByteTimeRDD = testresultIdToJsonRDD.map(testresultIdToJson => {
      implicit val formats = DefaultFormats
      val entries = (testresultIdToJson._2 \ "log" \ "entries").children
      if (!entries.isEmpty) {
        val url = (entries(0) \ "request" \ "url").extractOrElse[Option[String]](None)
        val firstByteTime = (entries(0) \ "timings" \ "wait").extractOrElse[Option[Int]](None)
        (testresultIdToJson._1, url, firstByteTime)
      } else {
        (None, None, None)
      }
    })

    val validTestresultIdToUrlToFirstByteTimeRDD = testresultIdToUrlToFirstByteTimeRDD.filter(testresultIdToUrlToFirstByteTime => {
      testresultIdToUrlToFirstByteTime != (None, None, None)
    })

    validTestresultIdToUrlToFirstByteTimeRDD.saveToCassandra(
      "source_data",
      "ttfbs",
      SomeColumns("testresult_id", "url", "ttfb")
    )

  }
}

/*


{
  "log": {
    "version": "1.2",
    "creator": {
      "name": "BrowserMob Proxy",
      "version": "2.0",
      "comment": ""
    },
    "browser": {
      "name": "Firefox",
      "version": "38.0",
      "comment": ""
    },
    "pages": [
      {
        "id": "Page 0",
        "startedDateTime": "2015-06-01T14:40:12.015+02:00",
        "title": "",
        "pageTimings": {
          "comment": ""
        },
        "comment": ""
      }
    ],
    "entries": [
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:24.778+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 287,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 3511,
            "mimeType": "text/html; charset=utf-8",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 477,
          "bodySize": 3511,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 1,
          "connect": 7,
          "send": 0,
          "wait": 183,
          "receive": 2,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "80.237.132.169",
        "comment": "",
        "time": 193
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:25.250+02:00",
        "request": {
          "method": "GET",
          "url": "http://fonts.googleapis.com/css?family=Montserrat:700",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "family",
              "value": "Montserrat:700"
            }
          ],
          "headersSize": 313,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 343,
            "mimeType": "text/css",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 345,
          "bodySize": 343,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 1,
          "dns": 1,
          "connect": 27,
          "send": 0,
          "wait": 15,
          "receive": 6,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "74.125.71.95",
        "comment": "",
        "time": 50
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:25.284+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/css/css_kShW4RPmRstZ3SpIC-ZvVGNFVAi0WEMuCnI0ZkYIaFw.css",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 349,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 1907,
            "mimeType": "text/css",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 365,
          "bodySize": 1907,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 34,
          "receive": 0,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 34
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:25.273+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/css/css_botNHIYRQPys-RH2iA3U4LbV9bPNRS64tLAs8ec1ch8.css",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 349,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 827,
            "mimeType": "text/css",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 364,
          "bodySize": 827,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 1,
          "dns": 0,
          "connect": 11,
          "send": 0,
          "wait": 27,
          "receive": 1,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "80.237.132.169",
        "comment": "",
        "time": 40
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:25.275+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/css/css_MnXiytJtb186Ydycnpwpw34cuUsHaKc80ey5LiQXhSY.css",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 349,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 254,
            "mimeType": "text/css",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 363,
          "bodySize": 254,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 10,
          "send": 0,
          "wait": 49,
          "receive": 0,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "80.237.132.169",
        "comment": "",
        "time": 59
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:25.292+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/css/css_RyhZyNyl8RLqWOJ3LllPozn3VyDNiWzKaJ29Ol30MDk.css",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 349,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 2518,
            "mimeType": "text/css",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 365,
          "bodySize": 2518,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 4,
          "dns": 0,
          "connect": 7,
          "send": 0,
          "wait": 56,
          "receive": 1,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "80.237.132.169",
        "comment": "",
        "time": 68
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:25.294+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/js/js_xAPl0qIk9eowy_iS9tNkCWXLUVoat94SQT48UBCFkyQ.js",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 331,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 32743,
            "mimeType": "text/javascript",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 374,
          "bodySize": 32743,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 5,
          "dns": 0,
          "connect": 10,
          "send": 0,
          "wait": 52,
          "receive": 10,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "80.237.132.169",
        "comment": "",
        "time": 77
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:25.293+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/js/js_cGLCpGIANKm1VaUmlm55c9EHSYCk6j_ojQwTRU6aLxk.js",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 331,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 11773,
            "mimeType": "text/javascript",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 374,
          "bodySize": 11773,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 7,
          "send": 0,
          "wait": 47,
          "receive": 1,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "80.237.132.169",
        "comment": "",
        "time": 55
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:25.296+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/js/js_I8yX6RYPZb7AtMcDUA3QKDZqVkvEn35ED11_1i7vVpc.js",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 331,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 1778,
            "mimeType": "text/javascript",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 372,
          "bodySize": 1778,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 1,
          "dns": 0,
          "connect": 16,
          "send": 0,
          "wait": 45,
          "receive": 0,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "80.237.132.169",
        "comment": "",
        "time": 62
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:25.307+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/favicon.ico",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 298,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 3638,
            "mimeType": "image/x-icon",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 322,
          "bodySize": 3638,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 7,
          "send": 1,
          "wait": 27,
          "receive": 1,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "80.237.132.169",
        "comment": "",
        "time": 36
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:25.314+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/android.png",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 298,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 1730,
            "mimeType": "image/png",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 319,
          "bodySize": 1730,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 42,
          "receive": 0,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 42
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:25.963+02:00",
        "request": {
          "method": "GET",
          "url": "https://fontastic.s3.amazonaws.com/dfSAf8pTeJ9jzj7nS5P2wP/icons.css",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 333,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 571,
            "mimeType": "text/css",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 404,
          "bodySize": 571,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 7,
          "connect": 165,
          "send": 0,
          "wait": 179,
          "receive": 453,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "54.231.162.34",
        "comment": "",
        "time": 804
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.771+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/styles/large/public/field/image/articles/_dsc0395_hdr.jpg?itok=Q6CHnTpb",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "itok",
              "value": "Q6CHnTpb"
            }
          ],
          "headersSize": 380,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 393681,
            "mimeType": "image/jpeg",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 323,
          "bodySize": 393681,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 29,
          "receive": 69,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 98
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.770+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/styles/large/public/field/image/articles/_dsc4800.jpg?itok=Giyk8s_K",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "itok",
              "value": "Giyk8s_K"
            }
          ],
          "headersSize": 376,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 1275106,
            "mimeType": "image/jpeg",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 325,
          "bodySize": 1275106,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 38,
          "receive": 175,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 213
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.771+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/styles/large/public/field/image/articles/_dsc0620_hdr.jpg?itok=uv9A4TTo",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "itok",
              "value": "uv9A4TTo"
            }
          ],
          "headersSize": 380,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 368279,
            "mimeType": "image/jpeg",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 323,
          "bodySize": 368279,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 24,
          "receive": 43,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 67
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.778+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/all/themes/eyesbound/img/ajax-loader.gif",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 335,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 1928,
            "mimeType": "image/gif",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 319,
          "bodySize": 1928,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 31,
          "receive": 0,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 31
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.777+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/styles/large/public/field/image/articles/_dsc5259.jpg?itok=lhLuim7K",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "itok",
              "value": "lhLuim7K"
            }
          ],
          "headersSize": 376,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 794211,
            "mimeType": "image/jpeg",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 323,
          "bodySize": 794211,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 1,
          "wait": 42,
          "receive": 55,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 98
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.777+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/styles/large/public/field/image/articles/f0901399.jpg?itok=qKRmnuTd",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "itok",
              "value": "qKRmnuTd"
            }
          ],
          "headersSize": 376,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 1991726,
            "mimeType": "image/jpeg",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 325,
          "bodySize": 1991726,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 72,
          "receive": 75,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 147
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.777+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/styles/large/public/field/image/articles/_dsc3975.jpg?itok=NQNvhcMP",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "itok",
              "value": "NQNvhcMP"
            }
          ],
          "headersSize": 376,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 315171,
            "mimeType": "image/jpeg",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 323,
          "bodySize": 315171,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 43,
          "receive": 33,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 76
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.778+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/styles/large/public/field/image/articles/dsc_3193_hdr.jpg?itok=-seTa1uX",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "itok",
              "value": "-seTa1uX"
            }
          ],
          "headersSize": 380,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 430403,
            "mimeType": "image/jpeg",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 323,
          "bodySize": 430403,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 38,
          "receive": 37,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 75
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.771+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/styles/large/public/field/image/articles/dsc_2249.jpg?itok=V395Dbei",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "itok",
              "value": "V395Dbei"
            }
          ],
          "headersSize": 376,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 221508,
            "mimeType": "image/jpeg",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 323,
          "bodySize": 221508,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 8,
          "send": 0,
          "wait": 71,
          "receive": 91,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "80.237.132.169",
        "comment": "",
        "time": 170
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.801+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/styles/large/public/field/image/articles/dsc_1203.jpg?itok=ia4Ez_Zw",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "itok",
              "value": "ia4Ez_Zw"
            }
          ],
          "headersSize": 376,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 161183,
            "mimeType": "image/jpeg",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 323,
          "bodySize": 161183,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 17,
          "dns": 0,
          "connect": 8,
          "send": 0,
          "wait": 60,
          "receive": 43,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "80.237.132.169",
        "comment": "",
        "time": 128
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.802+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/styles/large/public/field/image/articles/dsc_5594.jpg?itok=tR64HaUO",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "itok",
              "value": "tR64HaUO"
            }
          ],
          "headersSize": 376,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 901730,
            "mimeType": "image/jpeg",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 323,
          "bodySize": 901730,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 16,
          "dns": 0,
          "connect": 7,
          "send": 0,
          "wait": 83,
          "receive": 67,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "80.237.132.169",
        "comment": "",
        "time": 173
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.802+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/sites/default/files/styles/large/public/field/image/articles/dsc_5384.jpg?itok=etw2ME8p",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "itok",
              "value": "etw2ME8p"
            }
          ],
          "headersSize": 376,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 1283586,
            "mimeType": "image/jpeg",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 325,
          "bodySize": 1283586,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 1,
          "dns": 0,
          "connect": 7,
          "send": 0,
          "wait": 78,
          "receive": 95,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "80.237.132.169",
        "comment": "",
        "time": 181
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:26.937+02:00",
        "request": {
          "method": "GET",
          "url": "http://fonts.gstatic.com/s/montserrat/v6/IQHow_FEYlDC4Gzy_m8fcgFhaRv2pGgT5Kf0An0s4MM.woff",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 422,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 13292,
            "mimeType": "font/woff",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 414,
          "bodySize": 13292,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 1,
          "connect": 23,
          "send": 0,
          "wait": 12,
          "receive": 8,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "173.194.112.248",
        "comment": "",
        "time": 44
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:27.097+02:00",
        "request": {
          "method": "GET",
          "url": "http://www.google-analytics.com/analytics.js",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 293,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 11109,
            "mimeType": "text/javascript",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 373,
          "bodySize": 11109,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 3,
          "connect": 5,
          "send": 0,
          "wait": 7,
          "receive": 4,
          "ssl": 0,
          "comment": ""
        },
        "serverIPAddress": "173.194.112.238",
        "comment": "",
        "time": 19
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:27.298+02:00",
        "request": {
          "method": "GET",
          "url": "http://www.google-analytics.com/r/collect?v=1&_v=j36&aip=1&a=1944214114&t=pageview&_s=1&dl=http%3A%2F%2Feyesbound.com%2F&ul=en-us&de=UTF-8&dt=EYESBOUND&sd=8-bit&sr=1280x1024&vp=1920x1129&je=0&_u=QEAAAEABI%7E&jid=1291767642&cid=1724070513.1433162427&tid=UA-10493171-1&_r=1&z=277966830",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "dt",
              "value": "EYESBOUND"
            },
            {
              "name": "ul",
              "value": "en-us"
            },
            {
              "name": "sr",
              "value": "1280x1024"
            },
            {
              "name": "a",
              "value": "1944214114"
            },
            {
              "name": "sd",
              "value": "8-bit"
            },
            {
              "name": "vp",
              "value": "1920x1129"
            },
            {
              "name": "cid",
              "value": "1724070513.1433162427"
            },
            {
              "name": "de",
              "value": "UTF-8"
            },
            {
              "name": "v",
              "value": "1"
            },
            {
              "name": "je",
              "value": "0"
            },
            {
              "name": "t",
              "value": "pageview"
            },
            {
              "name": "aip",
              "value": "1"
            },
            {
              "name": "dl",
              "value": "http://eyesbound.com/"
            },
            {
              "name": "_r",
              "value": "1"
            },
            {
              "name": "tid",
              "value": "UA-10493171-1"
            },
            {
              "name": "jid",
              "value": "1291767642"
            },
            {
              "name": "_s",
              "value": "1"
            },
            {
              "name": "z",
              "value": "277966830"
            },
            {
              "name": "_u",
              "value": "QEAAAEABI~"
            },
            {
              "name": "_v",
              "value": "j36"
            }
          ],
          "headersSize": 562,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 35,
            "mimeType": "image/gif",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 371,
          "bodySize": 35,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 17,
          "receive": 0,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 17
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:28.827+02:00",
        "request": {
          "method": "GET",
          "url": "https://fontastic.s3.amazonaws.com/dfSAf8pTeJ9jzj7nS5P2wP/fonts/1416134681.woff",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 435,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 3372,
            "mimeType": "application/octet-stream",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 572,
          "bodySize": 3372,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 182,
          "receive": 0,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 182
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:29.216+02:00",
        "request": {
          "method": "GET",
          "url": "http://eyesbound.com/",
          "httpVersion": "HTTP/1.1",
          "cookies": [
            {
              "name": "has_js",
              "value": "1",
              "comment": ""
            },
            {
              "name": "_ga",
              "value": "GA1.2.1724070513.1433162427",
              "comment": ""
            },
            {
              "name": "_gat",
              "value": "1",
              "comment": ""
            }
          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 409,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 3511,
            "mimeType": "text/html; charset=utf-8",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 477,
          "bodySize": 3511,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 1,
          "wait": 57,
          "receive": 0,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 58
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:29.404+02:00",
        "request": {
          "method": "GET",
          "url": "https://fontastic.s3.amazonaws.com/dfSAf8pTeJ9jzj7nS5P2wP/icons.css",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [

          ],
          "headersSize": 434,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 304,
          "statusText": "Not Modified",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 0,
            "mimeType": "",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 323,
          "bodySize": 0,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 163,
          "receive": 1,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 164
      },
      {
        "pageref": "Page 0",
        "startedDateTime": "2015-06-01T14:40:29.736+02:00",
        "request": {
          "method": "GET",
          "url": "http://www.google-analytics.com/collect?v=1&_v=j36&aip=1&a=481319728&t=pageview&_s=1&dl=http%3A%2F%2Feyesbound.com%2F&ul=en-us&de=UTF-8&dt=EYESBOUND&sd=8-bit&sr=1280x1024&vp=1920x1129&je=0&_u=QACAAEABI%7E&jid=&cid=1724070513.1433162427&tid=UA-10493171-1&z=1516857092",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "queryString": [
            {
              "name": "dt",
              "value": "EYESBOUND"
            },
            {
              "name": "ul",
              "value": "en-us"
            },
            {
              "name": "sr",
              "value": "1280x1024"
            },
            {
              "name": "a",
              "value": "481319728"
            },
            {
              "name": "sd",
              "value": "8-bit"
            },
            {
              "name": "vp",
              "value": "1920x1129"
            },
            {
              "name": "cid",
              "value": "1724070513.1433162427"
            },
            {
              "name": "de",
              "value": "UTF-8"
            },
            {
              "name": "v",
              "value": "1"
            },
            {
              "name": "je",
              "value": "0"
            },
            {
              "name": "t",
              "value": "pageview"
            },
            {
              "name": "aip",
              "value": "1"
            },
            {
              "name": "dl",
              "value": "http://eyesbound.com/"
            },
            {
              "name": "tid",
              "value": "UA-10493171-1"
            },
            {
              "name": "jid",
              "value": ""
            },
            {
              "name": "_s",
              "value": "1"
            },
            {
              "name": "z",
              "value": "1516857092"
            },
            {
              "name": "_u",
              "value": "QACAAEABI~"
            },
            {
              "name": "_v",
              "value": "j36"
            }
          ],
          "headersSize": 545,
          "bodySize": 0,
          "comment": ""
        },
        "response": {
          "status": 200,
          "statusText": "OK",
          "httpVersion": "HTTP/1.1",
          "cookies": [

          ],
          "headers": [

          ],
          "content": {
            "size": 35,
            "mimeType": "image/gif",
            "comment": ""
          },
          "redirectURL": "",
          "headersSize": 405,
          "bodySize": 35,
          "comment": ""
        },
        "cache": {

        },
        "timings": {
          "blocked": 0,
          "dns": 0,
          "connect": 0,
          "send": 0,
          "wait": 4,
          "receive": 1,
          "ssl": 0,
          "comment": ""
        },
        "comment": "",
        "time": 5
      }
    ],
    "comment": ""
  }
}


 */
