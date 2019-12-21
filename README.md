# Introduction:

Transient Tracker (TT) is an open source PLUGIN for the ImageJ image processing program being designed to identify asteroids and other transient/variable objects in image sets. This software will provide: astrometric and photometric solutions, identification of moving/transient objects, identification of variable objects, and lightcurve analysis. Software output will be configured to allow data to be submitted to the Minor Planet Center (MPC), American Association of Variable Star Observers (AAVSO), and the IAU Central Bureau for Astronomical Telegrams (IAU CBAT). The goal of TT is to create easily extensible, professional grade software with a straightforward interface that allows amatuer astronomers and learning communities (e.g. classrooms and after-school groups) to participate in discovering new objects like NEOs (near Earth objects), and in extending our understanding of things that flicker, flare, and move in the night. This project was developed by part of the CosmoQuest team. CosmoQuest (https://cosmoquest.org/x/) is a primarily NASA-funded virtual research facility that engages the public in fully online citizen science projects in a framework that provides learning opportunities and facilitates collaboration. UPDATE (12/21/19) This project was defunded due to budget cuts to NASA last year. I just wanted to keep the work on my local repository just in case I ever decide to get back to it on a personal level.

# Current Functionality: (alpha release 0.01)

Presently TT will work on 32 bit greyscale FITS images. The PLUGIN will query the user for a "Brightness Deviation" ranging from 1 to 6 (default value of 6). This will be the number of standard deviations over the background of the image that will determine if a pixel belongs to a light emitting/reflecting object. After some basic statistical analysis of the image as a whole TT will gather data on unidentified objects in the field.

Once all objects in the field are identified, TT builds a curve of up to 20 pixels left, right of the brightest pixel on the x-axis, and another curve up to 20 pixels above and below the brightest pixel on the y-axis in the object using edge detection for objects closer than 20 pixels to either edge. TT then smooths the curves using a Gaussian filter. Finally the x and y sigma values are calculated in order to determine the Point Spread Functions / F.W.H.M. for each object.

After processing is complete TT gives the user this data in an output window with the ability to save it as a text file. TT also creates an overlay on the image that highlights the brightest pixel in each object.

# Current updates being worked on (alpha 0.02):

1. Point Spread Functions on each object in the image
2. FWHM calculations for each object in the image

# Usage:

Because Transient Tracker will be a full featured set of ImageJ PLUGINS, the first step to using this software is installing ImageJ on your system. Because this software and plugin are written in the Java programming language and the ImageJ scripting language (macros) it can be run on any system that has a Java Virtual Machine (JVM).

1. Determine if the JVM is installed on your system. If it is, skip step 2.

2. Installing the Java Virtual Machine

   Navigate your browser to https://www.java.com/en/download/help/download_options.xml and follow the directions for your operating system.

3. Installing ImageJ

   Navigate your browser to https://imagej.nih.gov/ij/docs/install/index.html and follow the directions for your operating system.

   WARNING:If installing on Mac OSX, please be very mindful of which version you are running. The installation procedure may go differently for the last 4 versions of OSX.

4. Installing Transient Tracker PLUGIN

   Find the plugins folder in your ImageJ installation, and create a folder called Astrometry (or whatever you want really) and place the piFind_Objects.java plugin in that folder.

   Open ImageJ. Open any 32bit greyscale FITS image (see below on where to find these). Click on Plugins->Compile and Run. Navigate to the Plugins/Astrometry folder you copied the plugin into, and select it and hit the Open/Select button. This will install the plugin as a permanent menu under Plugins in ImageJ, as well as run the plugin on the image you have opened. After it's installed you just click Plugins->Astrometry->piFind_Objects

# Useful information:

If you'd like to test the algorithm on the same star cluster images I'm using (M13, M71, M92, NGC6791, NGC7789) they can be found by navigating your browser at https://skyview.gsfc.nasa.gov/current/cgi/query.pl and following these steps:

1. Type the cluster name into the "Coordinates or Source:" field. (ex. M13)
2. Scroll down to the "Optical:DSS" category and select "DSS".
3. Scroll down to the "Common Options" section, and type 2048 into the "Image size (pixels)" field.
4. Click Sumbit Request
5. Scroll down to directly below the image and click the Download "FITS" link.
6. Save image to your local machine.
7. Open saved image when completing "Usage: Step 4"
