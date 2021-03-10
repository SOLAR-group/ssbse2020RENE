package gin.util;

import com.sampullara.cli.Argument;
import com.sampullara.cli.Args;

import org.pmw.tinylog.Logger;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

import java.util.*;
import java.util.concurrent.TimeoutException;

import java.lang.InterruptedException;

import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.model.Dependency;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoverageMeasurer {

    @Argument(alias = "classNames", description = "Class under test", required=true)
    protected String className;

    @Argument(alias = "criterion", description = "Criterion for test generation. Set to line by default", required=true)
    protected String criterion = "line";

    @Argument(alias = "evosuiteCP", description = "Path to evosuite jar, set to testgeneration/evosuite-1.0.6.jar by default", required=true)
    protected File evosuiteCP = new File("testgeneration/evosuite-1.0.6.jar");

    @Argument(alias = "projectCP", description = "Path class files for test and main", required=true)
    protected File projectCP;

    @Argument(alias = "junit", description = "Name of junit test", required=true)
    protected String junitTest;

    private String[] parsedCriterion;

    public CoverageMeasurer(String[] args) {

        Args.parseOrExit(this, args);
        //checks key parameters
        if (!evosuiteCP.isFile()) {

            Logger.info("Path to evosuite jar is required for coverage analysis, can be found in gin/testgeneration");
            System.exit(0);

        }
        parsedCriterion = criterion.split(":");
        for (int x=0; x< parsedCriterion.length;x++) {
            parsedCriterion[x] = parsedCriterion[x].toUpperCase();
        }


    }

    public CoverageMeasurer(String className, String criterion, File evosuiteCP, File projectCP, String junitTest) {
        this.className = className;
        this.criterion=criterion;
        this.evosuiteCP = evosuiteCP;
        this.projectCP = projectCP;
        this.junitTest = junitTest;

        if (!evosuiteCP.isFile()) {

            Logger.info("Path to evosuite jar is required for coverage analysis, can be found in gin/testgeneration");
            System.exit(0);

        }
        parsedCriterion = criterion.split(":");
        for (int x=0; x< parsedCriterion.length;x++) {
            parsedCriterion[x] = parsedCriterion[x].toUpperCase();
        }

    }


    public String measureCoverage() {
        String[] cmd = {"java", "-jar", evosuiteCP.getAbsolutePath()
                , "-measureCoverage"
                ,"-projectCP", projectCP.getAbsolutePath()
                , "-class", className
                , "-criterion", criterion
                , "-Djunit=" + junitTest

        };

	//Logger.info(Arrays.toString(cmd));

        String output = "";
        try {

            ProcessResult result = new ProcessExecutor().command(cmd)
                    .readOutput(true)
                    .redirectOutput(Slf4jStream.ofCaller().asInfo())
                    .redirectError(Slf4jStream.ofCaller().asInfo())
                    .destroyOnExit() // Destroy the process when VM exits
                    .execute();

            output = result.getOutput().getString();

        } catch (IOException e){
            Logger.error("IO Exception encountered when generating EvoSuite tests: " + e);
        } catch (InterruptedException e){
            Logger.error("Interrupted Exception encountered when generating EvoSuite tests: " + e);
        } catch (TimeoutException e){
            Logger.error("Timeout Exception encountered when generating EvoSuite tests: " + e);
        }

        return output;

    }

    public HashMap<String, String> parseOutput(String output) {

        HashMap<String, String> parsedResult = new HashMap<String, String>();
        Pattern lineregex;
        Pattern numberregex = Pattern.compile("\\d+");
        Matcher linematcher;
        Matcher numbermatcher;
        String number;
        String line;
        for (int x=0; x < parsedCriterion.length; x++) {
            lineregex = Pattern.compile(String.format("Coverage of criterion %s: \\d+%%", parsedCriterion[x]));
            //Logger.info(lineregex.toString());
            linematcher = lineregex.matcher(output);
            if (linematcher.find()) {
                line = linematcher.group(0);
                //Logger.info(linematcher.group(0));
                numbermatcher = numberregex.matcher(line);
                if (numbermatcher.find()) {
                    number = numbermatcher.group(0);
                    Logger.info(numbermatcher.group(0));
                }
                else {
                    Logger.error(String.format("No coverage value found during parsing of coverage criterion %s", parsedCriterion[x]));
                    number = "NaN";
                }
            }
            else {
                Logger.error(String.format("No coverage metrics found during parsing of coverage criterion %s", parsedCriterion[x]));
                number = "NaN";
            }
            parsedResult.put(parsedCriterion[x] + "_Coverage", number);
        }
        return parsedResult;
    }

    public static void main(String args[]) {

        CoverageMeasurer measurer = new CoverageMeasurer(args);
        String coverageResults = measurer.measureCoverage();
        Logger.info(coverageResults);
        HashMap<String, String> results = measurer.parseOutput(coverageResults);
        Logger.info(results.toString());


    }




}
