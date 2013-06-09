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
  def flavors = [[ name: "default" ]]

  def flavor(String flavorName) {
    flavor( [ name: flavorName ] )
  }

  def flavor(Map flavorSpec) {
    if(defaultFlavors) {
      flavors = []
      defaultFlavors = false
    }
    flavors.add flavorSpec
  }

  def manifest(Closure closure) {
    closure.delegate = manifest
    closure()
  }
}
