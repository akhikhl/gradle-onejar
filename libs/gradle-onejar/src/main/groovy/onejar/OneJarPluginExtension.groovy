package onejar

class OneJarPluginExtension {

  class Manifest {
    def attributes = [:]
    def attributes(map) {
      attributes << map
    }
  }

  def manifest = new Manifest()
  def defaultFlavors = true
  def products = [[ name: "default" ]]
  def archiveProducts = false
  def afterEvaluate = []

  def afterEvaluate(newValue) {
    afterEvaluate.add newValue
  }

  def product(String productName) {
    product( [ name: productName ] )
  }

  def product(Map productSpec) {
    if(defaultFlavors) {
      products = []
      defaultFlavors = false
    }
    products.add productSpec
  }

  def manifest(Closure closure) {
    closure.delegate = manifest
    closure()
  }
}
