package foxxy.reference.frontend.services

import org.scalajs.dom
import zio.json.JsonCodec

final case class Storage() {
  def set[T: JsonCodec](key: String, value: T): Unit = {
    dom.window.localStorage.setItem(key, JsonCodec[T].encoder.encodeJson(value).toString())
  }

  def get[T: JsonCodec](key: String): Option[T] = {
    dom.window.localStorage.getItem(key) match {
      case null  => None
      case value => Some(JsonCodec[T].decoder.decodeJson(value).toOption.get)
    }
  }

  def remove(key: String): Unit = {
    dom.window.localStorage.removeItem(key)
  }
}
