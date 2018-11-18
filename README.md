# Andworx returns Android Development back to Eclipse

Deprecated by Google in 2015, the Eclipse Android Development Toolkit (ADT) has lost the support needed to keep it up to date
against the relentless evolution of Android technology. By the end of 2017 we had ADT successor [Andmore](https://www.eclipse.org/andmore), 
which has a blend of MOTODEV Studio and ADT, at version 0.5.1 and no sign of another release, ever.

The Andworx project commenced in April 2018. The aim was to focus on Android application development, creating, importing, building, launching, 
debugging and finally releasing applications. This not-so-revolutionary aim meant taking Andmore back to it's ADT roots to make it more manageable, 
and adding a new Eclipse plug-in equivalent to the Android Gradle Plugin for performing all build operations. The degree of change mandated creation
of new project and hence Andworx. 

Andworx has been under wraps while it's design was being establish but has now matured to the point where it is on a steady course and and developers
can engage constructively to advance it's progress.  In order to access Andworx, a sample project has been set up to to demonstrate
importing an Android project into Eclipse, launching it on a device and being able to debug it, and exporting a realease version ready to be
installed on a suitable device. This is done using contemporary API level 27 build tools. Many milestones lay ahead including:

* Create and configure a new project
* Add Tests 
* All standard features implemented
* Incremental builds
* Multi-module projects

Once Andworx is generally available, then there is plenty of opportunity for innovation, perhaps introducing tools in the Eclipse Marketplace.

## Getting Started

Andworx is a Maven/Tycho project which creates a P2 update site from which Andworx can be installed using the Eclipse Platform "Install new software..." memu option.
The build process downloads a large number of dependent jars to be included with the Andworx components. There are also a large number of Maven, Tycho and Eclipse
dependencies that take a while to download on first build. 

Andworx targets Eclipse Photon and for development you will need [Eclipse IDE for RCP and RAP Developers](https://www.eclipse.org/downloads/packages/release/photon/r/eclipse-ide-rcp-and-rap-developers).
The recommended test Platform is [Eclipse IDE for Java Developers](https://www.eclipse.org/downloads/packages/release/photon/r/eclipse-ide-java-developers).

To more information, have a look at AndNowAndworx.pdf](http://cybersearch2.com.au/docs/AndNowAndworx.pdf).


### Prequisites

* Java 8 
* [Maven](https://maven.apache.org/download.cgi) - v3.5.2+ recommended
* [Git](https://git-scm.com/downloads)
* Set up Maven toolchains configuration to point to your Java 8 JDK installation. 
Refer [Guide to Using Toolchains](https://maven.apache.org/guides/mini/guide-using-toolchains.html) and [JDK Toolchain](http://maven.apache.org/plugins/maven-toolchains-plugin/toolchains/jdk.html)

You should also ensure your Git [username](https://help.github.com/articles/setting-your-username-in-git/) and 
[commit email address](https://help.github.com/articles/setting-your-commit-email-address-in-git/) are configured.

### Maven Build Steps

1. Clone [this repository](https://github.com/androidworx/andworx.git). The project will be placed is a directory named "andworx". 
1. From a command terminal, use Maven to build Andworx. Note that tool chain vendor and version parameters need to be included eg `mvn clean verify -Djava.execution.vendor=oracle -Djava.execution.version=1.8`
1. If a first time build, you will potentially observe a large number of files being downloaded to be cached on the local Maven repository. If so, it may be time for a coffee break.

The build, when successful, creates a P2 repository in project sub directory andmore-core/site/target/repository.

## Eclipse Development

Once the build succeeds, Andworx can be imported to Eclipse IDE for RCP and RAP Developers. The first step is to set the Eclipse active platform to the Andworx target which is in a file named andworx.target.target
located in project directory andworx.target. To do this, open the file with the Target Editor. It will take a while for the repository indexes to be loaded but then you will be able to click on a link at
the top of the window to set this target as the current active platform.

Andworx is imported as a Maven project. Note that only the root POM and plugins need be imported initally. Note that on the initial import, Maven will request to install life-cycle components for Tycho which
you should accept. Once this installation is complete, there will be a lot of errors and you will need to use the Maven option to update all Maven projects.

### Photon Quirks

Eclipse Photon has quirks around when to clean projects:

* Some bug in Photon causes some projects to suddenly needing to be clean. This can be triggered by an automated build or cleaning a single project. The symptom is clean one project leads to several projects needing cleaning. The symptom Description is
"API analysis aborted for 'org.eclipse.andworx.android' since its build path is incomplete". To get out of this state requires you to clean each of the other projects in error, one by one, until the problem clears.
If you are lucky, only one additional clean is needed.

* The project org.eclipse.andworx.build uses Dagger annotation processing for dependency injection. Sometimes a prject clean is required to regenerate Dagger sources. A Maven build can trigger this situation.
The symptom is the project has just over 40 compile errors for missing Dagger-generated files. You will need to manually clean the project. Note this usally triggers the preceeding bug.


### SWTBot

There is one integration test which uses [SWTBot](https://www.eclipse.org/swtbot/) to interact with Andworx's user interface to go through the process of importing a project and checking that an APK is built.
This test will use the current display on which to lauch the test workbench unless arrangements are made for SWTBot to use a display within a display eg. on Linux use [Zephyr](http://jeffskinnerbox.me/posts/2014/Apr/29/howto-using-xephyr-to-create-a-new-display-in-a-window/).

## Installation

An online reposistory will be available shortly. In the meantime, you can build Andworx to produce a local repository.
On the Eclipse Platform to receive Andworx, go to Windows -> Preferences -> Install/Upddate -> Available Software Sites and add....  You will then be able to select the Andworx repository in a file browser dialog. 
The repository will be in project directory andmore-core/site/target/repository/ following a successful build. There is both an Andworx feature and Andworx SDK feature to select, the latter is optional and contains the sources.

To see Andworx in action, you will need to download the [zipped demonstration project](http://cybersearch2.com.au/andworx/downloads/Permissions.zip) and unzip it in a convenient location.
For instructions, download the document [AndNowAndworx.pdf](http://cybersearch2.com.au/docs/AndNowAndworx.pdf).
