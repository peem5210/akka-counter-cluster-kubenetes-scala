package http.routes

import akka.http.scaladsl.server.Route

trait RouteClass {
  def getRoute: Route
}
