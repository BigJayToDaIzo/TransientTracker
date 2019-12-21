//alpha 0.02 COMPLETE Friday May 18, 12:25pm CDT

package ij;

import ij.io.OpenDialog;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.IOException;

public class piObjectify_Header {


  public static void main(String[] args){
    //check OS for MAC annoying removal of title bar and use also annoying alert box
    boolean isMac = false;
    if(System.getProperty("os.name").toLowerCase().contains("mac"))
      isMac = true;
    if(isMac)
      JOptionPane.showMessageDialog( null, "Select Telescope Image");
    OpenDialog od = new OpenDialog("Select Telescope Image");
    String imagePath = od.getPath();
    ImagePlus imgplus = new ImagePlus(imagePath);
    //hardcode image file for debuggin
    //ImagePlus imgplus = new ImagePlus("/home/josephlmyers/Desktop/FITS Images/M13.fits");
    //TODO Create config file with valid keywords and create in stream to build an array of 'valid keywords'
    String headerInfo = imgplus.getInfoProperty(); //all header info pulled into a string
    String[] headerLines = headerInfo.split("\n");
    //Temporary Sanity check on headerLines
    /*for(String s: headerLines)
      System.out.println(s);*/

    //TODO v- will be uncommented after I code config file setup
    ArrayList<String> validKeywordList = new ArrayList<>();
    //build arraylist of valid fits header metadata from config file HeaderKeywords.cfg
    try{
      /*****************************************************************************/
      /* these lines not needed in plugin, because the imp is passed to the plugin */

      //loads an image into an ImagePlus object
      /*if(isMac)
        JOptionPane.showMessageDialog( null, "Select Fits Keyword Config File");*/
      //OpenDialog od2 = new OpenDialog("Select Fits Keyword Config File");
      //String configFilePath = od2.getPath();
      //File file = new File(configFilePath);
      //set hard coded files for debugging
      File file = new File("/home/josephlmyers/Documents/Projects/TransientTracker/ij/HeaderKeywords.cfg");

      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      while((line = br.readLine()) != null){
        validKeywordList.add(line);

      }

    }catch (IOException e){
      e.printStackTrace();

    }
    //begin extracting header data
    FitsHeader fh = new FitsHeader();
    for(String s: headerLines){
      String value = fh.parseValue(s);
      if(s.startsWith("SIMPLE")){
        //find = and / and scan between for useful value
        if(value.toLowerCase().contains("t") || value.toLowerCase().contains("true"))
          fh.setSIMPLE(true);

        else{
          JOptionPane.showMessageDialog(null, "SIMPLE keyword not set to TRUE, not a valid FITS HEADER");
          System.exit(0);

        }//complete

      }else if(s.startsWith("BITPIX")){
        ArrayList<Integer> imagebitdepths = new ArrayList<>(Arrays.asList(8, 16, 32, 64, -32, -64)); //acceptable bitpix values
        int i = Integer.parseInt(value);
        if(imagebitdepths.contains(i))
          fh.setBITPIX(i);

        else{
          JOptionPane.showMessageDialog(null, "BITPIX does not contain an appropriate value for" +
              " FITS IMAGE.  Invalid FITS header data!");
          System.exit(0);

        }

      }else if(s.contains("NAXIS")){
        //set NASXI & NAXISn
        char c = s.charAt(5);
        int i = Integer.parseInt(value);
        if(c == ' ')
          fh.setNAXIS(i);

        else //add value to NAXISn ArrayList
          fh.addNAXISn(i);

      }else if(s.startsWith("CRVAL")){
        Double  d = Double.parseDouble(value);
        fh.addCRVALn(d);

      }else if(s.startsWith("RADESYS")){
        String news = value.substring(1, value.length() - 1); //parse out single quotes
        fh.setRADESYS(news);

      }else if(s.startsWith("EQUINOX")){
        Float f = Float.parseFloat(value);
        fh.setEQUINOX(f);

      }else if(s.contains("CTYPE")){
        String news = value.substring(1, value.length() - 1);
        fh.addCTYPEn(news);

      }else if(s.contains("CRPIX")){
        Float f = Float.parseFloat(value);
        fh.addCRPIXn(f);

      }else if(s.contains("CDELT")){
        Double d = Double.parseDouble(value);
        fh.addCDELTn(d);

      }else if(s.startsWith("COMMENT")){
        //research usefulness of keywords in comments and history
        String com = s.substring(8, s.length());
        fh.addCOMMENT(com);

      }else if(s.startsWith("HISTORY")){
        //research usefulness of keywords in comments and history
        String his = s.substring(8, s.length());
        fh.addHISTORY(his);


      }else if(s.startsWith("ORIGIN")){
        String news = value.substring(1, value.length() - 1);
        fh.setORIGIN(news);

      }else if(s.startsWith("END"))
        break;
    }
    //Sanity check of object build
    System.out.println(fh.toString());
    //Create file stream
    try{
      File file = new File("output.txt");
      file.createNewFile();
      FileWriter fw = new FileWriter(file);
      fw.write(fh.toString());

    }catch(Exception e){
      e.printStackTrace();
    }



  }//end main method
  private static class FitsHeader {
    //Definitions of FITS keywords can be found at https://heasarc.gsfc.nasa.gov/docs/fcg/standard_dict.html
    boolean SIMPLE;
    int BITPIX;
    int NAXIS;
    ArrayList<Integer> NAXISn = null;
    ArrayList<Double> CRVALn = null;
    String RADESYS;
    Float EQUINOX;
    ArrayList<String> CTYPEn = null;
    ArrayList<Float> CRPIXn = null;
    ArrayList<Double> CDELTn = null;
    String ORIGIN = "";
    ArrayList<String> COMMENT = null;
    ArrayList<String> HISTORY = null;

    //ctors
    public FitsHeader() {

    }
    //getters

    //setters

    //toString
    public String toString(){
      String NAX = ""; String CRV = ""; String CTY = ""; String CRP = "";
      String CDE = ""; String COM = ""; String HIS = "";
      for(int i = 0; i < this.NAXIS; i++) {
        if(this.NAXISn != null)
          NAX += "NAXIS" + (i + 1) + " " + this.NAXISn.get(i) + "\n";
        if(this.CRVALn != null)
          CRV += "CRVAL" + (i + 1) + " " + this.CRVALn.get(i) + "\n";
        if(this.CTYPEn != null)
          CTY += "CTYPE" + (i + 1) + " " + this.CTYPEn.get(i) + "\n";
        if(this.CRPIXn != null)
          CRP += "CRPIX" + (i + 1) + " " + this.CRPIXn.get(i) + "\n";
        if(this.CDELTn != null)
          CDE += "CDELT" + (i + 1) + " " + this.CDELTn.get(i) + "\n";
      }
      if(this.COMMENT != null)
        for(int i = 0; i < this.COMMENT.size(); i++)
          COM += "COMMENT " + this.COMMENT.get(i) + "\n";
      if(this.HISTORY != null)
        for(int i = 0; i < this.HISTORY.size(); i++)
          HIS += "HISTORY " + this.HISTORY.get(i) + "\n";

      String str = "SIMPLE " + this.SIMPLE + "\n" +
          "BITPIX " + this.BITPIX + "\n" + "NAXIS " + this.NAXIS + "\n" + NAX + CRV + "RADESYS " +
              this.RADESYS + "\n"+"EQUINOX " + this.EQUINOX + "\n" + "ORIGIN " + this.ORIGIN + "\n" + CTY + CRP +
              CDE + COM + HIS;

      return str;

    }
    //getters
    public boolean isSIMPLE() {
      return SIMPLE;

    }

    public int getBITPIX() {
      return BITPIX;

    }

    public int getNAXIS() {
      return NAXIS;

    }

    public ArrayList<Integer> getNAXISn() {
      return NAXISn;

    }

    public ArrayList<Double> getCRVALn() {
      return CRVALn;

    }

    public String getRADESYS() {
      return RADESYS;

    }

    public Float getEQUINOX() {
      return EQUINOX;

    }

    public ArrayList<String> getCTYPEn() {
      return CTYPEn;

    }

    public ArrayList<Float> getCRPIXn() {
      return CRPIXn;

    }

    public ArrayList<Double> getCDELTn() {
      return CDELTn;

    }

    public String getORIGIN() {
      return ORIGIN;

    }

    public ArrayList<String> getCOMMENT() {
      return COMMENT;

    }

    public ArrayList<String> getHISTORY() {
      return HISTORY;

    }
    //setters
    public void setSIMPLE(boolean SIMPLE) {
      this.SIMPLE = SIMPLE;

    }

    public void setBITPIX(int BITPIX) {
      this.BITPIX = BITPIX;

    }

    public void setNAXIS(int NAXIS) {
      this.NAXIS = NAXIS;

    }

    public void addNAXISn(Integer NAXISn) {
      if(this.NAXISn == null)
        this.NAXISn = new ArrayList<>();

      this.NAXISn.add(NAXISn);

    }

    public void addCRVALn(Double CRVALn) {
      if(this.CRVALn == null)
        this.CRVALn = new ArrayList<>();

      this.CRVALn.add(CRVALn);

    }

    public void setRADESYS(String RADESYS) {
      this.RADESYS = RADESYS;

    }

    public void setEQUINOX(Float EQUINOX) {
      this.EQUINOX = EQUINOX;

    }

    public void addCTYPEn(String CTYPEn) {
      if(this.CTYPEn == null)
        this.CTYPEn = new ArrayList<>();

      this.CTYPEn.add(CTYPEn);

    }

    public void addCRPIXn(Float CRPIXn) {
      if(this.CRPIXn == null)
        this.CRPIXn = new ArrayList<>();

      this.CRPIXn.add(CRPIXn);

    }

    public void addCDELTn(Double CDELTn) {
      if(this.CDELTn == null)
        this.CDELTn = new ArrayList<>();

      this.CDELTn.add(CDELTn);

    }

    public void setORIGIN(String ORIGIN){
      this.ORIGIN += ORIGIN;

    }

    public void addCOMMENT(String COMMENT) {
      if(this.COMMENT == null)
        this.COMMENT = new ArrayList<>();
      this.COMMENT.add(COMMENT);

    }

    public void addHISTORY(String HISTORY) {
      if(this.HISTORY == null)
        this.HISTORY = new ArrayList<>();
      this.HISTORY.add(HISTORY);

    }

    //methods
    String parseValue(String s){
      int b = s.indexOf("=") + 1;
      int e;
      if(!s.contains("/"))
        e = s.length() - 1;

      else
        e = s.indexOf("/");


      String value = s.substring(b, e);
      value = value.replaceAll("\\s+", "");
      return value;

    }

  }

}
