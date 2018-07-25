# Big Image Tools - Read and write large 2D images with Icy

## Description

This project contains the code necessary to read and write large 2D images. Either to integrate with other plugins or to interactively explore a large 2D image the tools in this project will help you.

This project has been developed as a _Gradle_ project and can be opened with _Eclipse_ IDE.

### Available plugins

In danyfel.bigimage.io:

- Load Big Image

  This plugin can load an entire image at a desired resolution level (0 full resolution, 1 half resolution, 2 quarter resolution, etc.). You can also load a region of the image by providing the full resolution coordinates of the region.

- Save Big Image

  This plugin is more a testing plugin, it allows you to save an image loaded on icy by tiles avoiding excessive memory usage.

- Transfer Big Image

  This plugin is a plugin to transfer image data from one file to another passing by Icy. This is a template plugin for thresholders and tile processing plugins. 

## Installation

### Requirements

In order to be able to work with this project you must have installed the following software:

- **Icy**, version 1.9.5.1 or above. ( [Available here](http://icy.bioimageanalysis.org) )
  - The following plugins should be already installed in order to execute BigImageTools:
    - EzPlug SDK
    - Protocols SDK
- **Eclipse**, version _Neon_ or above. Make sure to have the _Buildship_ plugin installed. ([Available here](http://www.eclipse.org/downloads/))
- **Icy4Eclipse** plugin for Eclipse, the latest version available. Follow [these](http://icy.bioimageanalysis.org/index.php?display=startDevWithIcy) instructions.

### Setup

1. Use your *Git* repository manager of preference to download this repository (even Eclipse can do this). The repository URL is [https://github.com/danyfel80/icy-bigimage.git](https://github.com/danyfel80/icy-bigimage.git).
2. Make sure the environment variable **ICY_HOME** is set to the location of your Icy installation. _**Note**: This could be tricky on Mac so make sure to follow [these](https://stackoverflow.com/questions/829749/launch-mac-eclipse-with-environment-variables-set) instructions._
3. Open Eclipse and select the menu *File > Import...* Then select *Gradle > Existing Gradle Project*. Click *Next* the project root directory is demanded select the folder **BigImageTools** inside the folder you downloaded the at. Finally, click *Finish* to create the project in eclipse.

Eclipse will download the dependencies specified in the *gradle.build* file. When it finishes you should see the project without any problem on the project explorer of Eclipse. *If this is not the case, check that the environment variable ICY_HOME is correctly defined.*