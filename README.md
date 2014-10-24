
# Gradle STJS plugin
[![Build Status](https://travis-ci.org/dzwicker/st-js-gradle-plugin.png)](https://travis-ci.org/dzwicker/st-js-gradle-plugin)
[![Bitdeli Badge](https://d2weczhvl823v0.cloudfront.net/dzwicker/st-js-gradle-plugin/trend.png)](https://bitdeli.com/free "Bitdeli Badge")

Gradle plugin to make it easy to compile Strongly-Typed Javascript.


## Strongly-Typed Javascript (STJS)
 
STJS is an open source (Apache 2.0 licensed) Javascript code generator from a Java source. It is executed after the compilation of your Java code.
 
The full website can be found at http://st-js.org


## Usage

Add the following to your build.gradle file:

```groovy
buildscript {
  repositories {
    mavenCentral()
  }

  dependencies {
    classpath group: 'com.github.dzwicker.stjs.gradle', name: 'st-js-gradle-plugin', version: '3.0.1'
  }
}

apply plugin: 'stjs'

stjs {
    include 'com/**/*StJs.java'
}

war {
    baseName 'liquid-equity-time'

    from("$buildDir/stjs") {
        into 'WEB-INF/classes'
    }
}

```

### Important

Please be aware of the manuel addition of the ```generatedSourcesDirectory``` to the war!
 
### Properties

Most of the above is self-explanatory. Include the buildscript section to pull this plugin into your project. Apply the plugin, and set your project version from the output of this plugin. Make sure the above configuration is at the top build.gradle file.

The options available are:

* include - the path specifier describing for what Java source you want to generate JavaScript. It's the standard path Maven/Ant specifiers. Defaults to "" (nothing).
* exclude - the path specifier describing what Java source you want to exclude from the JavaScript generate. It's the standard path Maven/Ant specifiers. Defaults to "" (nothing).
* allowedPackages - it's a list of Java packages that are allowed to be used inside the Java sources used for generation. A common usage is when you reserved in the Java sources a package for bridges to some JavaScript libraries. This package should than be excluded from the generation process.
* generateArrayHasOwnProperty - true to generate inside each array iteration if (!array.hasOwnProperty(index)) continue; in order to protect array iteration from the inclusion of the methods added to Array's prototype. Default value if true
* generateSourceMap - if true, a source map for every javascript resource will be created.
* generatedSourcesDirectory - the directory the plugin will generate the javascript to. Defaults to $buildDir/stjs.
* output -  the path to the compiles java classes. Defaults to sourceSets.main.output
* encoding - the encoding for the resources. Defaults to 'UTF-8'.
* classpath - the classpath for the compiler. Defaults to sourceSets.main.compileClasspath.

### Tasks

The plugin adds the ```stjs``` task to the project.

    gradle stjs


## Release notes

### 3.0.1 (23 June 2014)
The first public release of the plugin it will work with stjs 3.0.1.
