package http.routes

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.http.scaladsl.server.Route
import http.WebSocketHandler

class WSRoute(implicit val ctx: ActorContext[Nothing]) extends RouteClass {
  override def getRoute: Route = path("ws") {
    // TODO: Create an actor per WS connection
    //  to handle queue notification and queue position
    //  for each of the connection
    handleWebSocketMessages(WebSocketHandler()(ctx.system))
  }
}
