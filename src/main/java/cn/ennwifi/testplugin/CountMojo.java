package cn.ennwifi.testplugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * count the number of file.
 *
 */
@Mojo(name = "count")
public class CountMojo extends AbstractMojo {

  private static final String[] INCLUDES_DEFAULT = {"java", "xml", "sql", "properties"};
  private static final String[] RATIOS_DEFAULT = {"1.0", "0.25", "0.25", "0.25"};
  private static final String DOT = ".";
  /**
   * basedir.
   */
  @Parameter(defaultValue = "${project.basedir}")
  private File basedir;
  /**
   * sourcedir.
   */
  @Parameter(defaultValue = "${project.build.sourceDirectory}")
  private File sourcedir;
  /**
   * testSourcedir.
   */
  @Parameter(defaultValue = "${project.build.testSourceDirectory}")
  private File testSourcedir;
  /**
   * resources.
   */
  @Parameter(defaultValue = "${project.resources}")
  private List<Resource> resources;
  /**
   * testResources.
   */
  @Parameter(defaultValue = "${project.testResources}")
  private List<Resource> testResources;
  /**
   * includes.
   */
  @Parameter(property = "includes")
  private String[] includes;
  /**
   * ratios.
   */
  @Parameter(property = "ratios")
  private String[] ratios;

  private Map<String, Double> ratioMap = new HashMap<String, Double>();
  private long realTotal;
  private long fakeTotal;

  public void execute() throws MojoExecutionException, MojoFailureException {
    initRatioMap();
    try {
      countDir(sourcedir);
      countDir(testSourcedir);

      for (Resource res : resources) {
        countDir(new File(res.getDirectory()));
      }
      for (Resource res : testResources) {
        countDir(new File(res.getDirectory()));
      }

      getLog().info("TOTAL LINES:" + fakeTotal + " (" + realTotal + ")");

    } catch (IOException e) {
      throw new MojoExecutionException("Unable to count lines of code", e);
    }
  }

  private void initRatioMap() throws MojoExecutionException {
    if (includes == null || includes.length == 0) {
      includes = INCLUDES_DEFAULT;
      ratios = RATIOS_DEFAULT;
    }
    if (ratios == null || ratios.length == 0) {
      ratios = new String[includes.length];
      for (int i = 0; i < includes.length; i++) {
        ratios[i] = "1.0";
      }
    }
    if (includes.length != ratios.length) {
      throw new MojoExecutionException("pom.xml error: the length of includes is inconsistent with ratios!");
    }
    ratioMap.clear();
    for (int i = 0; i < includes.length; i++) {
      ratioMap.put(includes[i].toLowerCase(), Double.parseDouble(ratios[i]));
    }
  }

  private void countDir(File dir) throws IOException {
    if (!dir.exists()) {
      return;
    }
    List<File> collected = new ArrayList<File>();
    collectFiles(collected, dir);

    int realLine = 0;
    int fakeLine = 0;
    for (File file : collected) {
      int[] line = countLine(file);
      realLine += line[0];
      fakeLine += line[1];
    }

    String path = dir.getAbsolutePath().substring(basedir.getAbsolutePath().length());
    StringBuilder info = new StringBuilder().append(path).append(" : ").append(fakeLine).append(" (" + realLine + ")")
        .append(" lines of code in ").append(collected.size()).append(" files");
    getLog().info(info.toString());

  }

  private void collectFiles(List<File> collected, File file) throws IOException {
    if (file.isFile()) {
      if (isFileTypeInclude(file)) {
        collected.add(file);
      }
    } else {
      for (File files : file.listFiles()) {
        collectFiles(collected, files);
      }
    }
  }

  private int[] countLine(File file) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(file));
    int realLine = 0;
    try {
      while (reader.ready()) {
        reader.readLine();
        realLine++;
      }
    } finally {
      reader.close();
    }
    int fakeLine = (int) (realLine * getRatio(file));
    realTotal += realLine;
    fakeTotal += fakeLine;

    StringBuilder info = new StringBuilder().append(file.getName()).append("  : ").append(fakeLine)
        .append(" (" + realLine + ")").append(" lines");
    getLog().debug(info.toString());

    return new int[] {realLine, fakeLine};
  }

  private double getRatio(File file) {
    double ratio = 1.0;
    String type = getFileType(file);
    if (ratioMap.containsKey(type)) {
      ratio = ratioMap.get(type);
    }
    return ratio;
  }

  private boolean isFileTypeInclude(File file) {
    boolean result = false;
    String fileType = getFileType(file);
    if (fileType != null && ratioMap.keySet().contains(fileType.toLowerCase())) {
      result = true;
    }
    return result;
  }

  private String getFileType(File file) {
    String result = null;
    String fname = file.getName();
    int index = fname.lastIndexOf(DOT);
    if (index > 0) {
      String type = fname.substring(index + 1);
      result = type.toLowerCase();
    }
    return result;
  }

}
