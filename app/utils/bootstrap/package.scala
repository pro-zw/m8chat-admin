package utils

import views.html.helper.{FieldElements, FieldConstructor}

package object bootstrap {
  implicit val fieldConstructor = new FieldConstructor {
    override def apply(elements: FieldElements) = {
      views.html.tags.fieldConstructor(elements)
    }
  }
}
