package demo

class Genericplay[A](name: A) {
	def sometype(): A = {
	  name
	}
}

object DemoDemo {
  val stringType = new Genericplay[String]("some string")
  
  val intType = new Genericplay[Int](1234)
  
  
  def main(args: Array[String]) {
	  println(stringType)
	  
	  println(intType)
  }
  
}