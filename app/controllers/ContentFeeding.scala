package controllers

import play.api.mvc.{Controller, Result}
import services.ContentEnumerator

trait ContentFeeding {
  self: Controller =>

  def feedContent(content: ContentEnumerator): Result = {
    content match {
      case ContentEnumerator(e, contentType, Some(length)) => Ok.feed(e).as(contentType).withHeaders("Content-Length" -> length.toString)
      case ContentEnumerator(e, contentType, None) => Ok.chunked(e).as(contentType)
    }

  }
}