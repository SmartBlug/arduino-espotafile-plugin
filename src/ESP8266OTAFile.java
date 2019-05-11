/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

package com.esp8266.espOTAFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;

import java.lang.reflect.InvocationTargetException;

import processing.app.PreferencesData;
import processing.app.Editor;
import processing.app.Base;
import processing.app.BaseNoGui;
//import processing.app.Platform;
import processing.app.Sketch;
import processing.app.tools.Tool;
import processing.app.helpers.ProcessUtils;
//import processing.app.debug.TargetPlatform;

import org.apache.commons.codec.digest.DigestUtils;
import processing.app.helpers.FileUtils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.Charset;
import java.util.stream.Stream;
import java.util.zip.CRC32;

/**
 * Tools menu entry.
 */
public class ESP8266OTAFile implements Tool {
  Editor editor;
  String Key,Version;

  public void init(Editor editor) {
    this.editor = editor;
  }

  public String getMenuTitle() {
    return "ESP8266 OTA File Creation";
  }

  private String getBuildFolderPath(Sketch s) {
    // first of all try the getBuildPath() function introduced with IDE 1.6.12
    // see commit arduino/Arduino#fd1541eb47d589f9b9ea7e558018a8cf49bb6d03
    try {
      String buildpath = s.getBuildPath().getAbsolutePath();
      return buildpath;
    }
    catch (IOException er) { editor.statusError(er); }
    catch (Exception er) {
      try {
        File buildFolder = FileUtils.createTempFolder("build", DigestUtils.md5Hex(s.getMainFilePath()) + ".tmp");
        return buildFolder.getAbsolutePath();
      }
      catch (IOException e) { editor.statusError(e); }
      catch (Exception e) {
        // Arduino 1.6.5 doesn't have FileUtils.createTempFolder
        // String buildPath = BaseNoGui.getBuildFolder().getAbsolutePath();
        java.lang.reflect.Method method;
        try {
          method = BaseNoGui.class.getMethod("getBuildFolder");
          File f = (File) method.invoke(null);
          return f.getAbsolutePath();
        } catch (SecurityException ex) { editor.statusError(ex); } 
          catch (IllegalAccessException ex) { editor.statusError(ex); } 
          catch (InvocationTargetException ex) { editor.statusError(ex); } 
          catch (NoSuchMethodException ex) { editor.statusError(ex); }
      }
    }
    return "";
  }

  private void loadParams(String path) {
    File src = new File(path);
    if (src.exists()) {
      try (Stream<String> lines = Files.lines(Paths.get(path))) {
        lines.forEach( line -> {
          String[] parts = line.split("\\s+(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
          if (parts.length>2) {
            if (parts[0].equals("#define")) {
              switch (parts[1]) {
                case "OTA_Key": this.Key = parts[2]; break; //.replace("\"", ""); break;
                case "OTA_Version": this.Version = parts[2]; break; //.replace("\"", ""); break;
              }
            }
          }
        });
      } catch (IOException e) { e.printStackTrace(); }
    }
  }

  public void run() {
    String sketchName = editor.getSketch().getName();
    String imagePath = getBuildFolderPath(editor.getSketch()) + "/" + sketchName + ".ino.bin";

    this.Key = "";
    this.Version = "";

    loadParams(editor.getSketch().getMainFilePath());

    File f = new File(imagePath);
    if(f.exists() && !f.isDirectory()) { 
      if ((!this.Key.equals(""))&&(!this.Version.equals(""))) {
        editor.statusNotice("Creating "+sketchName+".upgrade");
        String upgradeFile = getBuildFolderPath(editor.getSketch()) + "/" + sketchName + ".upgrade";
        try {
          CRC32 crc = new CRC32();
          InputStream inputStream = new FileInputStream(imagePath);
          // Create the byte array to hold the data
          int cnt;
          while ((cnt = inputStream.read()) != -1) {
            crc.update(cnt);
          }
          inputStream.close();

          // creates the file
          String Header = "{\"key\":"+this.Key+",\"version\":"+this.Version+",\"crc32\":"+crc.getValue()+"}\r\n";
          OutputStream outputStream = new FileOutputStream(upgradeFile);
          outputStream.write(Header.getBytes(Charset.forName("UTF-8")));
          inputStream = new FileInputStream(imagePath);
          while ((cnt = inputStream.read()) != -1) {
            outputStream.write(cnt);
          }
					outputStream.close();
        } catch (Exception e) {
          e.printStackTrace();
        }

        editor.statusNotice("File "+sketchName+".upgrade created");
      }
      else editor.statusError("You need to specify OTA_Key and OTA_Version in your sketch like his : #define OTA_Key \"SoftwareKey\" and #define OTA_Version \"1.3.1\"");
    }
    else editor.statusError("You need to compile first");
  }
}