/*
 * gradle-onejar
 *
 * Copyright (c) 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.gradle.onejar

/**
 * Extension for gradle-onejar plugin.
 * <p>
 * Usage:
 * <pre>
 * onejar {
 *   manifest attribute attrName1: attrValue1 [, ...]
 *   product name: ... platform: ... arch: ... language: ... suffix: ... launchers: [ ... ]
 *   beforeProductGeneration { .. }
 * }
 * </pre>
 * Description:
 * <ul>
 * <li>manifest - optional, sets one or more attributes in META-INF/MANIFEST.MF</li>
 * <li>product - optional, adds product to the output of gradle-onejar plugin, supports properties:
 *   <ul>
 *      <li>name - required, unique product name, defines names for gradle configuration and tasks.</li>
 *      <li>platform - optional, could be "linux" or "windows". This parameter does not affect onejar output,
 *      although it is written to "VERSION" file of the generated product. Default value is "any".</li>
 *      <li>arch - optional, could be "x86_32" or "x86_64". This parameter does not affect onejar output,
 *      although it is written to "VERSION" file of the generated product. Default value is "any".</li>
 *      <li>language - optional, could be "en", "de" or any other code, formatted corresponding to ISO_639-1.
 *      Default value is "en".</li>
 *      <li>suffix - optional, string suffix added to folder/file name of the generated product.
 *      Default value is equal to product name.</li>
 *      <li>launchers - optional, array of one or more launcher-code strings. Accepted values
 *      are "windows" and "shell". If "launchers" contains "windows", gradle-onejar plugin
 *      generates launcher ".bat" file. If "launchers" contains "shell", gradle-onejar plugin
 *      generates launcher ".sh" file. If "launchers" is not specified, gradle-onejar plugin
 *      automatically selects best launcher corresponding to the product platform.
 *   </ul>
 * </li>
 * <li>archiveProducts - optional, boolean. When true, gradle-onejar plugin generates an archive
 * (.zip or .tar.gz) for each generated product.</li>
 * <li>beforeProductGeneration - optional, closure. When specified, the closure
 * will be called by gradle-onejar plugin just after project evaluation
 * and before product generation.</li>
 * </ul>
 * @author akhikhl
 *
 */
class OneJarPluginExtension {

  class Manifest {
    def attributes = [:]
    def attributes(map) {
      attributes << map
    }
  }

  def mainJar = null
  def manifest = new Manifest()
  private boolean defaultProducts = true
  def products = [[ name: "default" ]]
  boolean archiveProducts = false
  def additionalProductFiles = []
  def beforeProductGeneration = []
  def excludeProductFile = []
  def launchParameters = []
  def onProductGeneration = []

  def additionalProductFiles(newValue) {
    if(newValue instanceof Collection)
      additionalProductFiles.addAll newValue
    else
      additionalProductFiles.add newValue
  }

  def beforeProductGeneration(newValue) {
    beforeProductGeneration.add newValue
  }

  def excludeProductFile(Closure newValue) {
    excludeProductFile.add newValue
  }

  def launchParameter(String newValue) {
    launchParameters.add newValue
  }

  def mainJar(newValue) {
    mainJar = newValue
  }

  def manifest(Closure closure) {
    closure.delegate = manifest
    closure()
  }

  def onProductGeneration(newValue) {
    onProductGeneration.add newValue
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
}
