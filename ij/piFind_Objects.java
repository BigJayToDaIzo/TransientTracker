package ij;

/*************************************/
/*These imports not needed for plugin*/

import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.io.OpenDialog;

import ij.io.SaveDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.io.FileInfo;

import java.text.DecimalFormat;
import java.awt.*;
import java.awt.event.*;
import java.awt.Point;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import javax.swing.*;
import java.util.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.List;

public class piFind_Objects extends JFrame {

  public static void main(String[] args) {
    /*****************************************************************************/
    /* these lines not needed in plugin, because the imp is passed to the plugin */

    //loads an image into an ImagePlus object
    OpenDialog od = new OpenDialog("Select Telescope Image");
    String imagePath = od.getPath();
    ImagePlus imgplus = new ImagePlus(imagePath);
    imgplus.show();

    //build pixel array for mad image math
    ImageProcessor ip = imgplus.getProcessor();
    /*****************************************************************************/

    //build pixel arrays
    float[] pixels = (float[]) ip.getPixels();
    float[] sortedPixels = new float[ip.getWidth() * ip.getHeight()];

    //sort a copy of the array for box plot figures
    int i = 0;
    for (float f : pixels) {
      sortedPixels[i] = f;
      i++;

    }
    Arrays.sort(sortedPixels);

    //gather statistical data on image
    float mean = mean(sortedPixels);
    float median = median(sortedPixels);
    float stdDev = stdDev(pixels, mean);

    //create dialog for user input
    GenericDialog gd = new GenericDialog("Brightness Deviation:");
    String[] choices = {"1", "2", "3", "4", "5", "6"};
    gd.addChoice("# of Std. Deviation:", choices, "6");
    gd.showDialog();
    String userInput = gd.getNextChoice();
    float nsigma = Integer.valueOf(userInput) * stdDev;

    //convert pixels to 2d array for easier edge detection
    int h = ip.getHeight();
    int w = ip.getWidth();
    float[][] canvas = new float[h][w];
    float[][] canvas2 = new float[h][w];

    canvas = toTwoDimensions(pixels, h, w);
    canvas2 = toTwoDimensions(pixels, h, w);

    /*************************************************************************
     *   Begin new object location algo
     ************************************************************************/
    //Create TEMPORARY scanner to accept user input in console until GUI is completed
    Scanner input = new Scanner(System.in);
    //set default average box size/overlap/offset
    int boxSideLen = 3;
    int boxOverlap = 1;
    int boxOffset = boxSideLen - boxOverlap;
    ArrayList<Roi> boundingBoxes = new ArrayList<>();


    //get average box size from user
    System.out.println("Number of pixels for averaging box side length: (enter for default of 3pix)");
    String s = input.nextLine();
    if(!s.isEmpty()){
      boxSideLen = Integer.parseInt(s);
      boxOverlap = Math.floorDiv(boxSideLen, 3);
      boxOffset = boxSideLen - boxOverlap;
    }

    //determine how many iterations on x axis to get box to average entire image
    for(i = 0; i < w - 1; i = i + boxOffset){
      for(int j = 0; j < h - 1; j = j + boxOffset){
        //generate ROI's
        Roi roi = new Roi(i, j, boxSideLen, boxSideLen);
        boundingBoxes.add(roi);

      }

    }

    //remove non image points from ROI's
    HashMap<Roi, Float> boxAverages = new HashMap<>();
    for(Roi r: boundingBoxes){
      int divisor = 0;
      float rt = 0;
      Point[] pts = r.getContainedPoints();
      //determine points are IN image
      for(Point p: pts){
        if(p.x < w && p.y < h){
          //average ROI's
          rt += canvas[p.y][p.x];
          divisor++;

        }

      }
      //if average > nsigma add to boxAverages
      if(rt / divisor > nsigma)
        boxAverages.put(r, rt / divisor);

    }
    //sort ROI's by average ASC
    Set<Entry<Roi, Float>> set = boxAverages.entrySet();
    List<Entry<Roi, Float>> list = new ArrayList<Entry<Roi, Float>>(set);
    Collections.sort(list, new Comparator<Map.Entry<Roi, Float>>(){
      public int compare(Map.Entry<Roi, Float> o1, Map.Entry<Roi, Float> o2){
        return (o2.getValue()).compareTo(o1.getValue());

      }

    });

    ArrayList<UnidentifiedObject> uos = new ArrayList<>(); //create empty array of unidentified objects
    boolean foundOverlap = true;
    Entry entry = list.get(0);
    Roi newRoi = (Roi)entry.getKey();
    float[] roiMaxDetails = returnRoiMax(newRoi, canvas, h, w);
    UnidentifiedObject uo = new UnidentifiedObject((int)roiMaxDetails[0], (int)roiMaxDetails[1], roiMaxDetails[2]);
    setUniquePoints(newRoi, uo, canvas, h, w);
    uos.add(uo);
    list.remove(0);

    while(foundOverlap){
      Iterator iterator = list.iterator();
      foundOverlap = false;
      while(iterator.hasNext()){//while list has next object
        entry = (Entry<Roi, Float>)iterator.next();
        newRoi = (Roi)entry.getKey();
        roiMaxDetails = returnRoiMax(newRoi, canvas, h, w);
        if(determineOverlap(newRoi, uo, h, w)){
          foundOverlap = true;
          setUniquePoints(newRoi, uo, canvas, h, w);
          //check for updated center
          if(roiMaxDetails[2] > uo.getCenterIntensity()){
            uo.setXCenter((int)roiMaxDetails[0]);
            uo.setYCenter((int)roiMaxDetails[1]);
            uo.setCenterIntensity(roiMaxDetails[2]);
          }
          iterator.remove();

        }

      }
      if(!foundOverlap){
        if(!list.isEmpty()){
          foundOverlap = true;
          entry = list.get(0);
          newRoi = (Roi)entry.getKey();
          roiMaxDetails = returnRoiMax(newRoi, canvas, h, w);
          uo = new UnidentifiedObject((int)roiMaxDetails[0], (int)roiMaxDetails[1], roiMaxDetails[2]);
          setUniquePoints(newRoi, uo, canvas, h, w);
          uos.add(uo);
          list.remove(0);

        }

      }

    }
    //sanity check UO counter for image
    System.out.println("number of unidentified objects located: " + uos.size());

    /*************************************************************************
     *   END new object location algo
     ************************************************************************/

    //Create array of all objects in the image
    //ArrayList<UnidentifiedObject> uos = getObjects(canvas, nsigma, median, ip);

    double xFwhmAvg = 0;
    double yFwhmAvg= 0;
    int iter = 0;

    for (UnidentifiedObject o : uos) {
      //build fit curves to determine sigma values
      o.buildFit(ip, canvas2);
      //Calculate fwhm on each axis after fitting
      o.setXFwhm();
      xFwhmAvg += o.getXFwhm();
      o.setYFwhm();
      yFwhmAvg += o.getYFwhm();
      iter++;

    }
    //after fwhm calculated on objects, average across axis and image
    xFwhmAvg = xFwhmAvg / iter;
    yFwhmAvg = yFwhmAvg / iter;
    double imgFwhmAvg = (xFwhmAvg + yFwhmAvg)  / 2.0;

    System.out.println("Algorithmically calculated FWHM = "  + imgFwhmAvg);
    System.out.print("Enter updated FWHM.  If no change press enter: ");
    s = input.nextLine();
    if(!s.isEmpty()){
      imgFwhmAvg = Integer.parseInt(s);
      System.out.println("You have changed the FWHM to " + imgFwhmAvg);

    }

    //display default aperture in multiples of FWHM and give user opportunity to change multiplier
    int apertureMultiplier = 4;
    //TODO WHY is this calculated before we know if user changed it?
    double apertureRadius = apertureMultiplier * imgFwhmAvg;
    System.out.println("Algorithmically calculated aperture is 4 * FWHM = " + apertureRadius);
    System.out.print("Enter updated aperture MULTIPLIER to FWHM.  If no change press enter: ");
    s = input.nextLine();
    if(!s.isEmpty()){
      apertureMultiplier = Integer.parseInt(s);
      apertureRadius = apertureMultiplier * imgFwhmAvg;
      System.out.println("You have changed the aperture to " + s + " * FWHM = " + apertureRadius);

    }else{
      System.out.println("User requested no update to aperture multiplier.");

    }

    //assert an update to apperture didn't increase multiplier over inner radius of annulus
    int innerAnnulusMultiplier = 8;
    if(apertureMultiplier >= 8){
      innerAnnulusMultiplier = apertureMultiplier + 1;

    }
    double innerAnnulusRadius = innerAnnulusMultiplier * imgFwhmAvg;
    System.out.println("Algorithmically calculated inner annulus is " + innerAnnulusMultiplier + " * FWHM = " +
            innerAnnulusRadius + " pixels.");
    System.out.print("Enter updated inner annulus MULTIPLIER to FWHM.  If no change press enter: ");
    s = input.nextLine();
    if(!s.isEmpty()){
      innerAnnulusMultiplier = Integer.parseInt(s);
      innerAnnulusRadius = innerAnnulusMultiplier * imgFwhmAvg;
      System.out.println("You have changed the inner annulus to " + s + " * FWHM = " + innerAnnulusRadius +
              " pixels.");

    }else{
      System.out.println("User requested no update to inner annulus multiplier.");

    }

    //display default Outter Annulus in multiples of FWHM and give user opportunity to change multiplier
    int outterAnnulusMultiplier = 10;
    if(innerAnnulusMultiplier >= 10){
      outterAnnulusMultiplier = innerAnnulusMultiplier + 1;

    }
    double outterAnnulusRadius = outterAnnulusMultiplier * imgFwhmAvg;
    System.out.println("Algorithmically calculated outter annulus is " + outterAnnulusMultiplier + " * FWHM " +
    outterAnnulusRadius + " pixels.");
    System.out.print("Enter updated outter annulus MULTIPLIER to FWHM.  If no change press enter: ");
    s = input.nextLine();
    if(!s.isEmpty()){
      outterAnnulusMultiplier = Integer.parseInt(s);
      outterAnnulusRadius = outterAnnulusMultiplier * imgFwhmAvg;
      System.out.println("You have changed the outter annulus to " + s + " * FWHM = " + outterAnnulusRadius +
              " pixels.");

    }else{
      System.out.println("User requested no update to outter annulus multiplier.");

    }
    System.out.println("Enter gain (-e/ADU) for CCD: (default: 1.0 -e/ADU) ");
    s = input.nextLine();
    float gain = 1;
    if(!s.isEmpty()) { gain = Float.parseFloat(s); }
    System.out.println("Enter readout noise for image: (default: 5.0) ");

    s = input.nextLine();
    float rn = 5;
    if(!s.isEmpty()) rn = Float.parseFloat(s);

    //display final details
    System.out.println();
    System.out.println("Finalized aperture (red): " + apertureRadius + " pixels.");
    System.out.println("Finalized inner annulus (green): " + innerAnnulusRadius + " pixels.");
    System.out.println("Finalized outter annulus (blue): " + outterAnnulusRadius + " pixels.");

    //TODO remove console output for debugging
    System.out.println("Number of Unidentified objects found in field: " + uos.size());

    // Build the output frame
    JFrame frame = new JFrame("Unidentified Objects");
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    String outputStr = "";
    JPanel panel = new JPanel();
    JTextArea display = new JTextArea(40, 80);

    //TODO: Refactor to make overlay optional
    Overlay overlay = new Overlay();
    //Add arrays to hold all ROI's for analysis.
    ArrayList<OvalRoi> apertureROIs = new ArrayList<OvalRoi>();
    ArrayList<OvalRoi> innerAnnulusROIs = new ArrayList<OvalRoi>();
    ArrayList<OvalRoi> outterAnnulusROIs = new ArrayList<OvalRoi>();
    i = 1;
    for (UnidentifiedObject uo1 : uos) {
      //build red roi to surround aperture of object
      OvalRoi aperture = new OvalRoi(uo1.getXCenter() - apertureRadius, uo1.getYCenter() - apertureRadius,
              apertureRadius * 2, apertureRadius * 2);
      apertureROIs.add(aperture);
      aperture.setStrokeColor(Color.red);
      overlay.add(aperture);

      //build green roi to surround inner annulus
      OvalRoi innerAnnulus = new OvalRoi(uo1.getXCenter() - innerAnnulusRadius, uo1.getYCenter() - innerAnnulusRadius,
              innerAnnulusRadius * 2, innerAnnulusRadius * 2);
      innerAnnulusROIs.add(innerAnnulus);
      innerAnnulus.setStrokeColor(Color.green);
      overlay.add(innerAnnulus);

      //build blue roi to surround outter annulus
      OvalRoi outterAnnulus = new OvalRoi(uo1.getXCenter() - outterAnnulusRadius,
              uo1.getYCenter() - outterAnnulusRadius,outterAnnulusRadius * 2, outterAnnulusRadius * 2);
      outterAnnulusROIs.add(outterAnnulus);
      outterAnnulus.setStrokeColor(Color.blue);
      overlay.add(outterAnnulus);

      Point[] oapts = outterAnnulus.getContainedPoints();
      ArrayList<Point> oaptsArrayList = new ArrayList<>();
      Point[] iapts = innerAnnulus.getContainedPoints();
      Point[] apts = aperture.getContainedPoints();

      for(Point p: oapts){
        if(p.x >= 0 && p.y >= 0 && p.x < ip.getWidth() && p.y < ip.getHeight())
          oaptsArrayList.add(p);

      }
      for(Point p: iapts) {
        if (oaptsArrayList.contains(p)) {
          oaptsArrayList.remove(p);

        }

      }

      //sigma clip annulus
      ArrayList<Point> sigmaClippedAnnulus = sigmaClip(oaptsArrayList, canvas);
      float clippedMedian = medianPts(sigmaClippedAnnulus, canvas);
      float clippedMean = meanPts(sigmaClippedAnnulus, canvas);
      float backgroundPerPixel;
      if(clippedMedian >= clippedMean){
        backgroundPerPixel = 3 * clippedMedian - 2 * clippedMean;

      }else{
        backgroundPerPixel = clippedMean;

      }
      uo1.setBackground(backgroundPerPixel);
      //reset canvas to original intensities that were changed in the walkimage method
      canvas = toTwoDimensions(pixels, h, w);

      //calculate signal
      float signal = 0;
      for(Point p: apts){
        if(p.x >= 0 && p.y >= 0 && p.x < ip.getWidth() && p.y < ip.getHeight()){
          if(gain != 1) signal += canvas[p.y][p.x] * gain;
          else signal += canvas[p.y][p.x];

        }

      }
      signal = signal - (apts.length * backgroundPerPixel);
      uo1.setSignal(signal);
      //calculate flux
      float flux = (float) Math.sqrt(signal * gain + oaptsArrayList.size() * (backgroundPerPixel * gain + Math.pow(rn, 2)));
      uo1.setFlux(flux);
      //builds output string for saving to disk if necessary
      outputStr += uo1.toGUIString(i);
      //writes the output to the display panel
      display.append(uo1.toGUIString(i));
      i++;

    }
    //finalize string to write to file
    final String outStr = outputStr;


    //Save to file button
    JButton bSave = new JButton("Save to file");
    bSave.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        //Open save selection dialogue
        SaveDialog sd = new SaveDialog("Save output to...", "output", ".txt");
        String path = sd.getDirectory();
        String filename = sd.getFileName();
        String pathNFilename = path + filename;

        //write outputStr to path/file.txt of user choosing
        BufferedWriter bw = null;
        FileWriter fw = null;
        try {
          fw = new FileWriter(pathNFilename);
          bw = new BufferedWriter(fw);
          bw.write(outStr);
          System.out.println("Done");

        } catch (IOException exception) {
          exception.printStackTrace();

        } finally {
          try {
            if(bw != null)
              bw.close();
            if(fw != null)
              fw.close();

          } catch (IOException exception2) {
            exception2.printStackTrace();

          }
        }
      }
    });

    bSave.setToolTipText("Save this document to the hard disk");
    display.setEditable(false);
    JScrollPane scroll = new JScrollPane(display);
    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    panel.add(scroll);
    panel.add(bSave);

    frame.add(panel);
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    //after building overlay, set and show
    imgplus.setOverlay(overlay);

  }//end RUN method

  /*
  TODO: Possible Refactor Opportunity
  Statistical methods that can POSSIBLY be replaced with ImageJ's statistical tools.  Are they 32 bit precision?
  Check for other opportunities to use ImageJ functionality as opposed to new code
  */

  public static float mean(float[] arr) {
    float rtotal = 0;
    for (float f : arr) {
      rtotal += f;
    }
    float mean = rtotal / (float) arr.length;
    return mean;

  }

  public static float median(float[] arr) {
    float median = 0;
    //if array len is even, take average of two center indicies
    if (arr.length % 2 == 0) {
      int i = arr.length / 2;
      int j = i - 1;
      median = (arr[i] + arr[j]) / 2;
    } else {
      median = arr[arr.length / 2];
    }
    return median;

  }

  public static float stdDev(float[] arr, float mean) {
    float var = 0;
    for (float f : arr) {
      var = var + (float) Math.pow(f - mean, 2);
    }

    float stdDev = (float) Math.sqrt(var / (arr.length - 1));
    return stdDev;

  }

  public static float meanPts(ArrayList<Point> arr, float[][] canvas){
    float rtotal = 0;
    for(Point p: arr){
      rtotal += canvas[p.y][p.x];
    }
    float mean = rtotal / (float) arr.size();

    return mean;
  }

  public static float medianPts(ArrayList<Point> arr, float[][] canvas){
    float median;
    int iter = 0;
    float[] sortedCanvas = new float[arr.size()];
    for(Point p: arr){
      sortedCanvas[iter] = canvas[p.y][p.x];
      iter++;

    }
    Arrays.sort(sortedCanvas);

    if(arr.size() % 2 == 0){
      int i = arr.size() / 2;
      int j = i - 1;
      median = (sortedCanvas[i] + sortedCanvas[j]) / 2;

    }else{
      median = sortedCanvas[arr.size() / 2];

    }
    return median;

  }

  public static float stdDevPts(ArrayList<Point> arr, float[][] canvas, float mean){
    float var = 0;
    for(Point p : arr){
      var = var + (float) Math.pow(canvas[p.y][p.x] - mean, 2);

    }
    float stdDev = (float) Math.sqrt(var / arr.size() - 1);
    return stdDev;

  }

  //TODO: DEBUG sigma clipping per simulations
  public static ArrayList<Point> sigmaClip(ArrayList<Point> arr, float[][] canvas){
    final int MAX_ITER = 10;
    final double SIGMA_TOLERANCE = 0.001;

    int iter = 0;
    double alpha = 3;
    double deltaSigma = Double.MAX_VALUE;
    double posOutlier; double negOutlier;
    double sigma; double newSigma;
    float mean; float median;

    //pre processing statistical calculations
    median = medianPts(arr, canvas);
    mean = meanPts(arr, canvas);
    sigma = stdDevPts(arr, canvas, mean);
    while(iter < MAX_ITER && Math.abs(deltaSigma) >= SIGMA_TOLERANCE){
      posOutlier = median + alpha * sigma;
      negOutlier = median - alpha * sigma;
      //remove outliers
      for(int i = 0; i < arr.size(); i++){
        if(canvas[arr.get(i).y][arr.get(i).x] >= posOutlier || canvas[arr.get(i).y][arr.get(i).x] <= negOutlier){
          arr.remove(i);

        }

      }
      //post processing statistical  mean & sigma and compare sigma to old
      median = medianPts(arr, canvas);
      mean = meanPts(arr, canvas);
      newSigma = stdDevPts(arr, canvas, mean);
      deltaSigma = sigma - newSigma;
      sigma = newSigma;
      iter++;
      if(iter == 10){
        System.out.println("***ERROR, max sigma clip iterations exceeded!!!***");

      }

    }
    return arr;

  }

  public static float[][] toTwoDimensions(float[] pixels, int height, int width){
    float[][] canvas = new float[height][width];
    int x = 0;
    for (int i = 0; i < height; i++) {
      for (int j = 0; j < width; j++) {
        //controlling width left to right and converting origin from TLC to BLC
        canvas[(height - 1) - i][j] = pixels[x];
        x++;

      }

    }
    return canvas;

  }

  public static float[] returnRoiMax(Roi r, float[][] canvas, int h, int w){
    float[] max = new float[3];
    max[2] = Float.MIN_VALUE;
    for(Point p: r.getContainedPoints()){
      if(p.x < w && p.y < h){
        if(canvas[p.y][p.x] > max[2]){
          max[0] = p.x;
          max[1] = p.y;
          max[2] = canvas[p.y][p.x];
        }
      }
    }
    return max;
  }

  public static void setUniquePoints(Roi newRoi, UnidentifiedObject uo, float[][] canvas, int h, int w){
    ArrayList<Point> newPoints = new ArrayList<>();
    ArrayList<Point> uoPoints = new ArrayList<>();
    Point[] np = newRoi.getContainedPoints();
    //*****TODO PROBABLY REFACTOR of ArrayLists
    //add new Roi points to ArrayList
    for(Point p: np){
      if(p.x < w && p.y < h){//point falls within image
        newPoints.add(p);

      }

    }
    //add uo points to ArrayList
    ArrayList<ij.UnidentifiedObject.ObjPixel> uops = uo.getobjCoords();
    for(ij.UnidentifiedObject.ObjPixel uop: uops){
      int x = uop.getXcoord();
      int y = uop.getYcoord();
      Point p = new Point(x, y);
      uoPoints.add(p);

    }
    //*****END POSSIBLE REFACTOR

    //are newRoi points unique to uoPoints?
    for(Point newPoint: newPoints){//already had pixels outside of image boundary removed
      if(!uoPoints.contains(newPoint)){
        uo.setObjCoord(newPoint.x, newPoint.y, canvas[newPoint.y][newPoint.x]);
        //is new point brighter than uo.getCenterIntensity()?
        if(canvas[newPoint.y][newPoint.x] > uo.getCenterIntensity()){//new object center
          uo.setXCenter(newPoint.x);
          uo.setYCenter(newPoint.y);
          uo.setCenterIntensity(canvas[newPoint.y][newPoint.x]);

        }

      }

    }

  }

  public static boolean determineOverlap(Roi newRoi, UnidentifiedObject uo, int h, int w){
    ArrayList<Point> newPoints = new ArrayList<>();
    ArrayList<Point> uoPoints = new ArrayList<>();
    Point[] np = newRoi.getContainedPoints();
    //*****TODO PROBABLY REFACTOR of ArrayLists
    //add new Roi points to ArrayList
    for(Point p: np){
      if(p.x < w && p.y < h){//point falls within image
        newPoints.add(p);

      }

    }
    //add uo points to ArrayList
    ArrayList<ij.UnidentifiedObject.ObjPixel> uops = uo.getobjCoords();
    for(ij.UnidentifiedObject.ObjPixel uop: uops){
      int x = uop.getXcoord();
      int y = uop.getYcoord();
      Point p = new Point(x, y);
      uoPoints.add(p);

    }
    //*****END POSSIBLE REFACTOR
    //are newRoi points unique to uoPoints?
    for(Point newPoint: newPoints){//already had pixels outside of image boundary removed
      if(uoPoints.contains(newPoint)){
        return true;

      }

    }
    return false;
  }

}

//begin support classes for plugin
class UnidentifiedObject {

  public class ObjPixel {
    private int xcoord;
    private int ycoord;
    private float intensity;

    public ObjPixel(int x, int y, float intensity){
      this.xcoord = x;
      this.ycoord = y;
      this.intensity = intensity;

    }

    //getters
    public int getXcoord(){return this.xcoord;}
    public int getYcoord(){ return this.ycoord;}
    public float getIntensity(){ return this.intensity;}

    //setters
    public void setXcoord(int x){ this.xcoord = x;}
    public void setYcoord(int y){ this.ycoord = y;}
    public void setIntensity(float i){ this.intensity = i;}

    public String toString(){
      String toString = "Xcoord: " + this.getXcoord() + " Ycoord: " + this.getYcoord() + " intensity: "
          + this.getIntensity();
      return toString;

    }

  }//end ObjPixel class

  private int xCenter;
  private int yCenter;
  private float centerIntensity;
  private float background;
  private float signal;
  private float flux;
  private ArrayList<ObjPixel> objCoords; //includes all pixels of object and their intensity
  private float xSigma = 1;
  private float ySigma = 1;
  private double xFwhm = 0;
  private double yFwhm = 0;

  //begin properties that do not calculate until after all objects have been found in the image
  private HashMap<Integer, Double> xGaus = new HashMap<Integer, Double>();
  private HashMap<Integer, Double> yGaus = new HashMap<Integer, Double>();
  private HashMap<Integer, Float> xObs = new HashMap<Integer, Float>();
  private HashMap<Integer, Float> yObs = new HashMap<Integer, Float>();
  private HashMap<Integer, Double> xFit = new HashMap<Integer, Double>();
  private HashMap<Integer, Double> yFit = new HashMap<Integer, Double>();

  private boolean xWithinEpsilon = false;
  private boolean yWithinEpsilon = false;
  //end properties that do not calculate until after all objects have been found in the image

  //ctors
  public UnidentifiedObject(int x, int y, float centerIntensity) {
    this.xCenter = x;
    this.yCenter = y;
    this.centerIntensity = centerIntensity;
    this.objCoords = new ArrayList<>();
    this.setObjCoord(x, y, centerIntensity);
  }

  //getters
  public int getXCenter(){ return this.xCenter; }
  public int getYCenter(){ return this.yCenter; }
  public float getCenterIntensity() { return this.centerIntensity; }
  public float getNearbyBackground() { return this.background; }
  public float getXSigma() { return this.xSigma; }
  public float getYSigma() { return this.ySigma; }
  public HashMap<Integer, Double> getxGaus() {
    if(!xGaus.isEmpty()){
      return this.xGaus;

    }else{
      System.out.println("There is no xGaussian data yet.");
      this.xGaus = null;
      return this.xGaus;

    }
  }
  public HashMap<Integer, Double> getyGaus() {
    if(!yGaus.isEmpty()){
      return this.yGaus;

    }else{
      System.out.println("There is no yGaussian data yet.");
      this.yGaus = null;
      return this.yGaus;

    }
  }
  public HashMap<Integer, Double> getxFit() {
    if(!xFit.isEmpty()){
      return this.xFit;

    }else{
      System.out.println("There is no xFitted data yet.");
      this.xFit = null;
      return this.xFit;

    }
  }
  public HashMap<Integer, Double> getyFit() {
    if(!yFit.isEmpty()){
      return this.yFit;

    }else{
      System.out.println("There is no yFitted data yet.");
      this.yFit = null;
      return this.yFit;

    }
  }
  public double getXFwhm() {
    if(xFwhm != 0.0){
      return this.xFwhm;

    }else{
      System.out.println("xFWHM has not been calculated on this object yet.");
      return this.xFwhm;

    }
  }
  public double getYFwhm() {
    if(yFwhm != 0.0){
      return this.yFwhm;

    }else{
      System.out.println("yFWHM has not been calculated on this object yet.");
      return this.yFwhm;

    }
  }
  public ArrayList<ObjPixel> getobjCoords() { return this.objCoords; }
  public float getBackground(){ return this.background; }
  public float getSignal(){ return this.signal; }
  public float getFlux(){ return this.flux; }

  //setters
  public void setXCenter(int x){ this.xCenter = x; }
  public void setYCenter(int y){ this.yCenter = y; }
  public void setCenterIntensity(float i) { this.centerIntensity = i; }
  public void setBackground(float b) { this.background = b; }
  public void setXSigma(float s) { this.xSigma = s; }
  public void setYSigma(float s) { this.ySigma = s; }
  public void setXFwhm() { this.xFwhm = this.getXSigma() * 2 * Math.sqrt(2 * Math.log(2)); }
  public void setYFwhm() { this.yFwhm = this.getYSigma() * 2 * Math.sqrt(2 * Math.log(2)); }
  public void setObjCoord(int x, int y, float intensity){
    ObjPixel op = new ObjPixel(x, y, intensity);
    this.objCoords.add(op);

  }
  public void setSignal(float s){ this.signal = s; }
  public void setFlux(float f) { this.flux = f; }


  //utility methods
  public void buildFit(ImageProcessor ip, float[][] canvas){
    float xInterval = 1;
    float yInterval = 1;
    float xErr = -99;
    float xErrPlusInt = -99;
    float yErr = -99;
    float yErrPlusInt = -99;

    int xMinOffset = -20;
    int yMinOffset = -20;
    int xMaxOffset = 20;
    int yMaxOffset = 20;

    HashMap<Integer, Double> Gaus = new HashMap<Integer, Double>();
    HashMap<Integer, Double> GausPlusInt = new HashMap<Integer, Double>();
    HashMap<Integer, Double> Fit = new HashMap<Integer, Double>();
    HashMap<Integer, Double> FitPlusInt = new HashMap<Integer, Double>();

    //edge detection code
    if(this.getXCenter() < 20){
      //adjust to left edge
      xMinOffset = -1*(this.getXCenter());

    }
    if(this.getXCenter() > ip.getWidth() - 21){
      //adjust to right edge
      xMaxOffset = ip.getWidth() - this.getXCenter() - 1;

    }
    if(this.getYCenter() < 20){
      //adjust to top edge
      yMinOffset = -1*(this.getYCenter());

    }
    if(this.getYCenter() > ip.getHeight() - 21){
      yMaxOffset = ip.getHeight() - this.getYCenter() - 1;

    }//END edge detection

    //build Observed curves on x & y
    for(int i = xMinOffset; i <= xMaxOffset; i++){
      this.xObs.put(i, canvas[this.getYCenter()][this.getXCenter() + i]);

    }
    for(int i = yMinOffset; i <= yMaxOffset; i++){
      this.yObs.put(i, canvas[this.getYCenter() + i][this.getXCenter()]);

    }//END build Observed curves

    //build all other curves and update sigma on X axis
    //***** X AXIS STUFFS
    while(xErr < 0 && xErrPlusInt < 0){
      Gaus.clear();
      GausPlusInt.clear();
      for(int i = xMinOffset; i <= xMaxOffset; i++){
        //do math on each pixel
        double gaus = ((1 / (this.xSigma * Math.sqrt(2 * Math.PI))) * Math.exp(-(.5) * ((i * i)/(this.xSigma *
            this.xSigma))));
        double gausPlusInt = ((1 / ((this.xSigma + xInterval) * Math.sqrt(2 * Math.PI))) * Math.exp(-(.5) *
            ((i * i)/((this.xSigma + xInterval) * (this.xSigma + xInterval)))));
        Gaus.put(i, gaus);
        GausPlusInt.put(i, gausPlusInt);

      }

      //build fit / fitplusinterval curves
      Fit.clear();
      FitPlusInt.clear();
      for(int i = xMinOffset; i <= xMaxOffset; i++){
        float maxObs = returnFltMapMax('v', this.xObs);
        double maxGaus = returnDblMapMax('v', Gaus);
        double currGaus = Gaus.get(i);
        double fit = 1 / maxGaus * currGaus * maxObs;
        Fit.put(i, fit);
        double maxGausPlusInt = returnDblMapMax('v', GausPlusInt);
        double currGausPlusInt = GausPlusInt.get(i);
        double fitPlusInt = 1 / maxGausPlusInt * currGausPlusInt * maxObs;
        FitPlusInt.put(i, fitPlusInt);

      }

      //calc Err & ErrPlusInterval
      xErr = 0;
      xErrPlusInt = 0;
      for(int i = xMinOffset; i <= xMaxOffset; i++){
        xErr += Fit.get(i) - this.xObs.get(i);
        xErrPlusInt += FitPlusInt.get(i) - this.xObs.get(i);

      }

      //if both still negative iterate this.xSigma
      if(xErr < 0 && xErrPlusInt < 0){
        this.xSigma += xInterval;

      }

    }

    //half interval and reduce
    xInterval /= 2;
    reduceIntervals(xInterval, 'x', xMinOffset, xMaxOffset);

    //*****Y AXIS STUFFS*****
    while(yErr < 0 && yErrPlusInt < 0){
      Gaus.clear();
      GausPlusInt.clear();
      for(int i = yMinOffset; i <= yMaxOffset; i++){
        //do math on each pixel
        double gaus = ((1 / (this.ySigma * Math.sqrt(2 * Math.PI))) * Math.exp(-(.5) * ((i * i)/(this.ySigma *
            this.ySigma))));
        double gausPlusInt = ((1 / ((this.ySigma + yInterval) * Math.sqrt(2 * Math.PI))) * Math.exp(-(.5) *
            ((i * i)/((this.ySigma + yInterval) * (this.ySigma + yInterval)))));
        Gaus.put(i, gaus);
        GausPlusInt.put(i, gausPlusInt);

      }

      //build fit / fitplusinterval curves
      Fit.clear();
      FitPlusInt.clear();
      for(int i = yMinOffset; i <= yMaxOffset; i++){
        float maxObs = returnFltMapMax('v', this.yObs);
        double maxGaus = returnDblMapMax('v', Gaus);
        double currGaus = Gaus.get(i);
        double fit = 1 / maxGaus * currGaus * maxObs;
        Fit.put(i, fit);
        double maxGausPlusInt = returnDblMapMax('v', GausPlusInt);
        double currGausPlusInt = GausPlusInt.get(i);
        double fitPlusInt = 1 / maxGausPlusInt * currGausPlusInt * maxObs;
        FitPlusInt.put(i, fitPlusInt);

      }

      //calc Err & ErrPlusInterval
      yErr = 0;
      yErrPlusInt = 0;
      for(int i = yMinOffset; i <= yMaxOffset; i++){
        yErr += Fit.get(i) - this.yObs.get(i);
        yErrPlusInt += FitPlusInt.get(i) - this.yObs.get(i);

      }

      //if both still negative iterate this.xSigma
      if(yErr < 0 && yErrPlusInt < 0){
        this.ySigma += yInterval;

      }

    }

    //half interval and reduce
    yInterval /= 2;
    reduceIntervals(yInterval, 'y', yMinOffset, yMaxOffset);

  }

  public void reduceIntervals(float interval, char axis, int minOffset, int maxOffset){
    float epsilon = 0.01f;
    float err = 0;
    float errCenter = 0;
    float errRight = 0;

    HashMap<Integer, Double> Gaus = new HashMap<Integer, Double>();
    HashMap<Integer, Double> GausCenter = new HashMap<Integer, Double>();
    HashMap<Integer, Double> GausRight = new HashMap<Integer, Double>();
    HashMap<Integer, Double> Fit = new HashMap<Integer, Double>();
    HashMap<Integer, Double> FitCenter = new HashMap<Integer, Double>();
    HashMap<Integer, Double> FitRight = new HashMap<Integer, Double>();

    if(axis == 'x') {
      Gaus.clear();
      GausCenter.clear();
      GausRight.clear();
      Fit.clear();
      FitCenter.clear();
      FitRight.clear();
      //first Gaussian curves on x axis
      for (int i = minOffset; i <= maxOffset; i++) {
        double gaus = ((1 / (this.xSigma * Math.sqrt(2 * Math.PI))) * Math.exp(-(.5) * ((i * i) /
            (this.xSigma * this.xSigma))));
        double gausCenter = ((1 / ((this.xSigma + interval) * Math.sqrt(2 * Math.PI))) * Math.exp(-(.5) *
            ((i * i) / ((this.xSigma + interval) * (this.xSigma + interval)))));
        double gausRight = ((1 / ((this.xSigma + (2 * interval)) * Math.sqrt(2 * Math.PI))) * Math.exp(-(.5) *
            ((i * i) / ((this.xSigma + (2 * interval)) * (this.xSigma + (2 * interval))))));
        Gaus.put(i, gaus);
        GausCenter.put(i, gausCenter);
        GausRight.put(i, gausRight);

      }

      //then Fit curves on x axis
      for (int i = minOffset; i <= maxOffset; i++) {
        float maxObs = returnFltMapMax('v', this.xObs);
        double maxGaus = returnDblMapMax('v', Gaus);
        double currGaus = Gaus.get(i);
        double fit = 1 / maxGaus * currGaus * maxObs;
        Fit.put(i, fit);
        double maxGausCenter = returnDblMapMax('v', GausCenter);
        double currGausCenter = GausCenter.get(i);
        double fitCenter = 1 / maxGausCenter * currGausCenter * maxObs;
        FitCenter.put(i, fitCenter);
        double maxGausRight = returnDblMapMax('v', GausRight);
        double currGausRight = GausRight.get(i);
        double fitRight = 1 / maxGausRight * currGausRight * maxObs;
        FitRight.put(i, fitRight);

      }

      //build error sums
      for (int i = minOffset; i <= maxOffset; i++) {
        err += Fit.get(i) - this.xObs.get(i);
        errCenter += FitCenter.get(i) - this.xObs.get(i);
        errRight += FitRight.get(i) - this.xObs.get(i);

      }
      while (!this.xWithinEpsilon) {
        //check left half for opposing signs
        if ((err > 0 && errCenter < 0) || (err < 0 && errCenter > 0)) {//check left half for opposing signs
          //if within epsilon set sigma
          if ((interval * 2 - interval) <= epsilon) {
            this.xWithinEpsilon = true;

          }

        } else {//right side has opposing signs
          //if within epsilon set sigma
          this.xSigma += interval;
          //if sigma has 3 decimal places, set withinEpsilon to true
          if (interval * 2 - interval <= epsilon) {
            this.xWithinEpsilon = true;

          }

        }

        if (!this.xWithinEpsilon) {//divide interval by 2 and recursively call
          interval = interval / 2;
          reduceIntervals(interval, 'x', minOffset, maxOffset);

        }else{
          //build true xGaus curve
          for (int i = minOffset; i <= maxOffset; i++) {
            //do math on each pixel
            double gaus = ((1 / (this.xSigma * Math.sqrt(2 * Math.PI))) * Math.exp(-(.5) * ((i * i) /
                (this.xSigma * this.xSigma))));
            this.xGaus.put(i, gaus);

          }
          //build true Fit curve
          for (int i = minOffset; i <= maxOffset; i++) {
            float maxObs = returnFltMapMax('v', this.xObs);
            double maxGaus = returnDblMapMax('v', this.xGaus);
            double currGaus = xGaus.get(i);
            double fit = 1 / maxGaus * currGaus * maxObs;
            this.xFit.put(i, fit);

          }

        }

      }
    //else axis = y
    }else{
      Gaus.clear();
      GausCenter.clear();
      GausRight.clear();
      Fit.clear();
      FitCenter.clear();
      FitRight.clear();

      //first Gaussian curves on y axis
      for (int i = minOffset; i <= maxOffset; i++) {
        //do math on each pixel
        double gaus = ((1 / (this.ySigma * Math.sqrt(2 * Math.PI))) * Math.exp(-(.5) * ((i * i) /
            (this.ySigma * this.ySigma))));
        double gausCenter = ((1 / ((this.ySigma + interval) * Math.sqrt(2 * Math.PI))) * Math.exp(-(.5) *
            ((i * i) / ((this.ySigma + interval) * (this.ySigma + interval)))));
        double gausRight = ((1 / ((this.ySigma + (2 * interval)) * Math.sqrt(2 * Math.PI))) * Math.exp(-(.5) *
            ((i * i) / ((this.ySigma + (2 * interval)) * (this.ySigma + (2 * interval))))));
        Gaus.put(i, gaus);
        GausCenter.put(i, gausCenter);
        GausRight.put(i, gausRight);

      }

      //then Fit curves on y axis
      for (int i = minOffset; i <= maxOffset; i++) {
        float maxObs = returnFltMapMax('v', this.yObs);
        double maxGaus = returnDblMapMax('v', Gaus);
        double currGaus = Gaus.get(i);
        double fit = 1 / maxGaus * currGaus * maxObs;
        Fit.put(i, fit);
        double maxGausCenter = returnDblMapMax('v', GausCenter);
        double currGausCenter = GausCenter.get(i);
        double fitCenter = 1 / maxGausCenter * currGausCenter * maxObs;
        FitCenter.put(i, fitCenter);
        double maxGausRight = returnDblMapMax('v', GausRight);
        double currGausRight = GausRight.get(i);
        double fitRight = 1 / maxGausRight * currGausRight * maxObs;
        FitRight.put(i, fitRight);

      }

      //build error sums
      for (int i = minOffset; i <= maxOffset; i++) {
        err += Fit.get(i) - this.yObs.get(i);
        errCenter += FitCenter.get(i) - this.yObs.get(i);
        errRight += FitRight.get(i) - this.yObs.get(i);

      }

      while (!this.yWithinEpsilon) {
        //check left half for opposing signs
        if ((err > 0 && errCenter < 0) || (err < 0 && errCenter > 0)) {//check left half for opposing signs
          //if within epsilon set sigma
          if (interval * 2 - interval <= epsilon) {
            //this.ySigma += 0.5 * interval; //
            this.yWithinEpsilon = true;

          }

        } else {//right side has opposing signs
          //if within epsilon set sigma
          this.ySigma += interval;
          if (interval * 2 - interval <= epsilon) {
            //this.ySigma += 0.5 * interval;
            this.yWithinEpsilon = true;

          }

        }
        //else divide interval by 2 and recursively call
        if (!this.yWithinEpsilon) {
          interval = interval / 2;
          reduceIntervals(interval, 'y', minOffset, maxOffset);

        }else{
          //build true yGaus curve
          for (int i = minOffset; i <= maxOffset; i++) {
            //do math on each pixel
            double gaus = ((1 / (this.ySigma * Math.sqrt(2 * Math.PI))) * Math.exp(-(.5) * ((i * i) /
                (this.ySigma * this.ySigma))));
            this.yGaus.put(i, gaus);

          }
          //build true yFit curve
          for (int i = minOffset; i <= maxOffset; i++) {
            float maxObs = returnFltMapMax('v', this.yObs);
            double maxGaus = returnDblMapMax('v', this.yGaus);
            double currGaus = yGaus.get(i);
            double fit = 1 / maxGaus * currGaus * maxObs;
            this.yFit.put(i, fit);

          }

        }

      }

    }

  }

  public double returnDblMapMax(char keyOrVal, HashMap<Integer, Double> pmap){
    double max = -9999999.0;
    if(keyOrVal == 'k'){
      for(Integer key : pmap.keySet()){
        if(key > max)
          max = key;
      }

    }else if(keyOrVal == 'v'){
      for(Double val : pmap.values()){
        if(val > max)
          max = val;
      }

    }else
      System.out.println("Please ensure you pass 'k' or 'v' as your first parameter!");

    return max;

  }

  public float returnFltMapMax(char keyOrVal, HashMap<Integer, Float> pmap){
    float max = -9999999.0f;
    if(keyOrVal == 'k'){
      for(Integer key : pmap.keySet()){
        if(key > max)
          max = key;

      }
    }else if(keyOrVal == 'v'){
      for(Float val : pmap.values()){
        if(val > max)
          max = val;

      }
    }else
      System.out.println("Please ensure you pass 'k' or 'v' as your 1st parameter!");
    return max;

  }

  public String toGUIString(int iter){
    String str = "UO#" + iter + " | x:" + this.getXCenter() + " | y:" + this.getYCenter() + " | FWHM:" +
            (this.getXFwhm() + this.getYFwhm()) / 2 + " | signal: " + this.getSignal() + " +/- " + this.getFlux() +
            "\n";

    return str;
  }

  public String toString(){
    //portion of FWHM formula to multiply times the sigma values
    DecimalFormat numberFormat = new DecimalFormat("#.000");
    String toString = "x:" + this.xCenter + " |y:" + this.yCenter + " |bg:" + this.background + " |signal:"
            + " +/- " + this.flux + "\n"
            + "xFwhm:" + this.xFwhm + " |yFwhm:" + this.yFwhm + " |xSigma:" + this.xSigma + " |ySigma:" + this.ySigma
            + "/n";
    String stringAppend = "";
    for (ObjPixel op: this.objCoords){
      stringAppend += "(" + op.xcoord + "," + op.ycoord + ") ";
    }
    toString = toString + stringAppend;

    return toString;

  }

}
