![CNSim-Bitcoin Logo](src/site/resources/images/logo.png)

# A CNSim instantiation for the Nakamoto protocol

This repo hosts an implementation of Nakamoto consensus using the CNSim engine.

# Quick start
0. It is assumed that you have the latest versions of `maven`, `git` and `Java` installed in your system.
1. Install the `cnsim-engine` library in your local maven repository. 
	1. Clone `https://github.com/cmg-york/cnsim-engine/` to your local machine.
	2. `mvn install`. This installs the cnsim-engine JAR in your local Maven Repository
2. `mvn test` for running the bitcoin tests
3. `testRun.sh` for confirming that a complete simulation can be run. Uses property file in  `examples/config/test/sanity/` 
4. Create your own configurations and simulate as follows:
```
mvn exec:java -Dexec.args='-c [path-to-my-configuration]/[my configuration].properties'
```

## Documentation

* Markdown documentation viewable on GitHub can be found under [src/site/markdown/documentation](src/site/markdown/documentation).
* Complete html documents including JavaDocs can be found under `docs/index.html` - repo needs to be cloned for these to be viewed locally.


