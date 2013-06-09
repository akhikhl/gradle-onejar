package onejar

class OneJarPluginExtension {

  class Manifest {
    def attributes = [:]
    def attributes(map) {
      attributes << map
    }
  }

  def manifest = new Manifest()
  def defaultProducts = true
  def products = [[ name: "default" ]]
  def archiveProducts = false
  def beforeProductGeneration = []

  def beforeProductGeneration(newValue) {
    beforeProductGeneration.add newValue
  }

  def product(String productName) {
    product( [ name: productName ] )
  }

  def product(Map productSpec) {
    if(defaultProducts) {
      products = []
      defaultProducts = false
    }
    products.add productSpec
  }

  def manifest(Closure closure) {
    closure.delegate = manifest
    closure()
  }
}
