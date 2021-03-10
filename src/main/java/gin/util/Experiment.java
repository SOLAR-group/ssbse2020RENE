package gin.util;

import com.opencsv.CSVWriter;
import com.sampullara.cli.Argument;
import com.sampullara.cli.Args;

import gin.LocalSearch;
import gin.Patch;
import gin.PatchAnalyser;
import org.apache.commons.lang3.ObjectUtils;
import org.pmw.tinylog.Logger;

import java.io.*;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;

import java.lang.InterruptedException;

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

import gin.edit.Edit;
import gin.test.UnitTestResult;
import gin.test.UnitTestResultSet;
import gin.test.InternalTestRunner;
import org.apache.commons.io.FilenameUtils;
import org.pmw.tinylog.Logger;

public class Experiment {
    @Argument(alias = "d", description = "Project directory: required", required=true)
    protected File projectDir;

    @Argument(alias = "p", description = "Project name: required", required=true)
    protected String projectName;

    @Argument(alias = "h", description = "Path to maven bin directory e.g. /usr/local/")
    protected File mavenHome = new File("/usr/local/");  // default on OS X

    @Argument(alias = "v", description = "Set Gradle version")
    protected String gradleVersion;

    @Argument(alias = "evosuiteCP", description = "Path to evosuite jar, set to testgeneration/evosuite-1.0.6.jar by default")
    protected File evosuiteCP = new File("testgeneration/evosuite-1.0.6.jar");

    @Argument(alias = "classNames", description = "List of classes for which to generate tests")
    protected String[] classNames;

    @Argument(alias = "projectTarget", description = "Directory for project class files or jar file of the project; ignored if classNames parameter is set")
    protected File projectTarget;

    @Argument(alias = "outputDir", description = "Output directory for generated tests; for maven projects the pom file will be updated automatically")
    protected File outputDir;

    @Argument(alias = "removeTests", description = "Remove existing tests from outputDir, set to projectDir/src/java/test if outputDir not specified")
    protected boolean removeTests = false;

    @Argument(alias = "generateTests", description = "Generate tests for classNames or projectTarget")
    protected boolean generateTests = false;

    @Argument(alias = "test", description = "Run all tests in outputDir, set to projectDir/src/test/java if outputDir not specified")
    protected boolean test = true;

    @Argument(alias = "classNumber", description = "Number of classes to generate EvoSuite tests for, used for debugging purposes")
    protected Integer classNumber = 0;

    @Argument(alias = "seed", description = "Random seed for test case generation, set to 88 by default")
    protected String evo_seed = "88"; // random seed, need this to get deterministic results

    @Argument(alias = "maxStatements", description = "Search budget for test case generation, set to 50000 statements by default")
    protected String search_budget = "50000"; // search budget for MaxStatements stopping condition

    @Argument(alias = "criterion", description = "Criterion for test generation. Set to line by default")
    protected String criterion = "line"; // coverage goal for single test generation

    @Argument(alias = "criterionList", description = "Criterion list for test generation.")
    protected String[]  criterion_list = {};

    @Argument(alias = "output_variables", description = "Output variables for test report")
    protected String output_variables = "TARGET_CLASS,criterion,Size,Length,Fitness,Total_Time"; // coverage goal for test generation

    // Local Search Variables

    @Argument(alias = "et", description = "Edit Type")
    protected String editType = "LINE";

    @Argument(alias = "f", description = "Required: Source filename", required=true)
    protected File filename = null;

    @Argument(alias = "m", description = "Required: Method signature including arguments." +
            "For example, \"classifyTriangle(int,int,int)\"", required=true)
    protected String methodSignature = "";

    @Argument(alias = "s", description = "Seed")
    protected Integer seed = 123;

    @Argument(alias = "n", description = "Number of steps")
    protected Integer numSteps = 100;

    /*@Argument(alias = "c", description = "Class name")
    protected String className;*/ //Not in use. Use classNames[0] instead

    @Argument(alias = "cp", description = "Classpath")
    protected String classPath;

    //Exclusive parameter for patchAnalyser. Also requires Patch
    @Argument(alias = "t", description = "Test class name")
    protected String testClassName = "";

    //TestSampler required
    @Argument(alias = "evoTestSource", description = "Path to generated evosuite tests")
    protected File evoTestSource;


    @Argument(alias = "oracle", description = "Oracle test class name. Test Class used to compare patch")
    protected String oracleTestClassName;

    @Argument(alias = "iter", description = "Number of iterations of experiment")
    protected Integer iterations = 1;

    @Argument(alias = "ginRuns", description = "Number of local search runs")
    protected Integer ginRuns = 20;


    protected int totalIterations = 1;

    protected String patchText = "|";

    protected TestSampler testsampler;

    protected TestCaseGenerator testCaseGen;

    String sampledClassName;

    @Argument(alias = "of", description = "Output File")
    protected File outputFile;

    //Utility Variables
    protected String[] EXPERIMENT_HEADER = {"Index", "TARGET_CLASS",
            "Criterion", "Size",
            "Length", "Fitness",
            "Total_Time", "evo_seed",
            "gin_seed", "patch_text",
            "valid_patch", "all_tests_passed",
            "execution_time", "speedup(%)", "intermediate"
    };

    protected String[] gin_headers = {"patch", "validpatch", "success", "avgtime","speedup"};

    private String[] evoOutputVariablesList;


    protected CSVWriter outputFileWriter;

    //we want to first take in all CLI arguments into a single call for all 3 programs.
    // we then want to format strings to be passed into TestGeneration, LocalSearch and PatchAnalyser as though they were command lines trings
    // With this, we can create wrappers around each argument to return some value to be passed forward in the pipeline.
    //Finally, append the combined results of each run of the experiment into a CSV file. This ensures line integrity.

    Experiment(String[] args) {

        Args.parseOrExit(this, args);
        if (this.criterion_list.length == 0) {
            Logger.info("no criterion list");
            this.criterion_list = new String[]{criterion};

        }
        for (int x =0;x<criterion_list.length;x++) {
            criterion_list[x] = criterion_list[x].toUpperCase();
            //Logger.info("Criterion list: " + criterion_list[x]);
        }
	String[] newArray = new String[criterion_list.length + 1];
        System.arraycopy(criterion_list, 0, newArray, 0, criterion_list.length);
	newArray[criterion_list.length] = String.join(":",criterion_list);
	criterion_list = newArray;
        for (int x =0;x<criterion_list.length;x++) {
            Logger.info("Criterion list: " + criterion_list[x]);
        }

        this.totalIterations = iterations * this.criterion_list.length;

        if (outputFile == null) {
            String datestring = new SimpleDateFormat("dd-MM-yy-hhmm").format(new Date());
            outputFile = new File(String.format("experiment-results/experiment_results_%s_%d_iter_%s.csv", this.projectName, iterations, datestring));
        }

        this.evoOutputVariablesList = output_variables.split(",");
        initialiseExperimentHeader();
        sampledClassName = this.testClassName + "_SAMPLED";
        Logger.info("EditType is : " + this.editType);



    }

    public String[] getEvoOutputVariablesList() {
        return evoOutputVariablesList;
    }

    public void initialiseExperimentHeader() {
        ArrayList<String> headers = new ArrayList<String>();
        headers.add("Index");
        Collections.addAll(headers, evoOutputVariablesList);
        headers.add("evo_seed");
        for (int x=0; x< criterion_list.length; x++) {
            headers.add(criterion_list[x] + "_Coverage");
        }
        headers.add("sampled");
        headers.add("gin_seed");
        Collections.addAll(headers, gin_headers);
        headers.add("intermediate");
        String[] a = {""};
        EXPERIMENT_HEADER = headers.toArray(a);
        printArr(EXPERIMENT_HEADER);
    }

    private static void printArr(String[] arr) {
        for (int x = 0; x < arr.length; x++) {
            Logger.info(arr[x]);
        }
    }

    private void generateEvosuiteTests(String currentCriterion, int currentSeed) {
        String cSeed = Integer.toString(currentSeed);

        TestCaseGenerator testCaseGen = new TestCaseGenerator(this.projectDir, this.projectName, this.mavenHome, gradleVersion,
                this.evosuiteCP, this.classNames, this.projectTarget, this.outputDir,
                this.removeTests,  this.generateTests, this.test, this.classNumber,
                cSeed, this.search_budget, currentCriterion, this.output_variables);
        this.testCaseGen = testCaseGen;
    }

    private String getLocalSearchPatch(Integer currentSeed, Boolean oracleTest, boolean sampleTest) {
        Logger.info(this.classNames[0]);
        LocalSearch simpleLocalSearch;
        if (oracleTest == false) {
            if (sampleTest == false) {
                simpleLocalSearch = new LocalSearch(filename, methodSignature, currentSeed, numSteps, projectDir, classNames[0], classPath, testClassName, oracleTestClassName, editType);
            }
            else {
                simpleLocalSearch = new LocalSearch(filename, methodSignature, currentSeed, numSteps, projectDir, classNames[0], classPath, sampledClassName, oracleTestClassName, editType);
            }
        }

        else {
            simpleLocalSearch = new LocalSearch(filename, methodSignature, currentSeed, numSteps, projectDir, classNames[0], classPath, oracleTestClassName, testClassName, editType);
        }
        String patchText = simpleLocalSearch.getPatchFromSearch();


        return patchText;
    }

    private HashMap<String, String> getBestLocalSearchPatch(Integer currentSeed, Integer iterations, Boolean oracleTest, Boolean sampledTest) {

        List<HashMap<String,String>> patchResults = new ArrayList<HashMap<String,String>>();
        HashMap<String, String> patchAnalysisResult;
        for (int iter=0;iter<iterations;iter++) {
            this.patchText = getLocalSearchPatch(currentSeed + iter, oracleTest, sampledTest);

             patchAnalysisResult = getPatchAnalysis(oracleTest);
             patchResults.add(patchAnalysisResult);

        }

        int bestSpeedupIndex = 0;
        Float bestSpeedupTime = 0.0f;
        Float speeduptime;
        Boolean validity;
        Boolean success;
        for (int x = 0; x < iterations; x++) {
            speeduptime = Float.parseFloat(patchResults.get(x).get("speedup"));
            validity = Boolean.parseBoolean(patchResults.get(x).get("validpatch"));
            success = Boolean.parseBoolean(patchResults.get(x).get("success"));
            if (speeduptime > bestSpeedupTime && validity && success) {
                bestSpeedupTime = speeduptime;
                bestSpeedupIndex = x;
            }
        }
        HashMap<String, String> bestPatch = patchResults.get(bestSpeedupIndex);
        return bestPatch;

    }

    private List<HashMap<String, String>> getLocalSearchPatchWithIntermediate(Integer currentSeed, Boolean oracleTest, boolean sampleTest) {
        Logger.info(this.classNames[0]);
        LocalSearch intermediateLocalSearch;
        if (oracleTest == false) {
            if (sampleTest == false) {
                intermediateLocalSearch = new LocalSearch(filename, methodSignature, currentSeed, numSteps, projectDir, classNames[0], classPath, testClassName, oracleTestClassName, editType);
            }
            else {
                intermediateLocalSearch = new LocalSearch(filename, methodSignature, currentSeed, numSteps, projectDir, classNames[0], classPath, sampledClassName, oracleTestClassName, editType);
            }
        }

        else {
            intermediateLocalSearch = new LocalSearch(filename, methodSignature, currentSeed, numSteps, projectDir, classNames[0], classPath, oracleTestClassName, testClassName, editType);
        }
        List<HashMap<String, String>> patchResultsList = intermediateLocalSearch.getPatchFromSearchWithIntermediate();


        return patchResultsList;
    }

    private List<HashMap<String, String>> getBestLocalSearchPatchWithIntermediate(Integer currentSeed, Integer iterations, Boolean oracleTest, Boolean sampledTest) {
        List<HashMap<String, String>> currentPatchList;
        HashMap<String, String> currentPatch;
        List<HashMap<String,String>> patchResults = new ArrayList<HashMap<String,String>>();
        List<List<HashMap<String,String>>> patchResultsList = new ArrayList<>();
        HashMap<String, String> patchAnalysisResult;
        HashMap<String, String> finalPatch;
        for (int iter=0;iter<iterations;iter++) {
            currentPatchList = getLocalSearchPatchWithIntermediate(currentSeed + iter, oracleTest, sampledTest);
            int rev = 1;

            this.patchText = currentPatchList.get(currentPatchList.size() - 1).get("patch");

            patchAnalysisResult = getPatchAnalysis(oracleTest);
            patchResults.add(patchAnalysisResult);
            patchResultsList.add(currentPatchList);

        }

        int bestSpeedupIndex = 0;
        Float bestSpeedupTime = 0.0f;
        Float speeduptime;
        Boolean validity;
        Boolean success;
        for (int x = 0; x < iterations; x++) {
            speeduptime = Float.parseFloat(patchResults.get(x).get("speedup"));
            validity = Boolean.parseBoolean(patchResults.get(x).get("validpatch"));
            success = Boolean.parseBoolean(patchResults.get(x).get("success"));
            if (speeduptime > bestSpeedupTime && validity && success) {
                bestSpeedupTime = speeduptime;
                bestSpeedupIndex = x;
            }
        }
        List<HashMap<String,String>> bestPatchList = patchResultsList.get(bestSpeedupIndex);
        HashMap<String, String> bestPatch = patchResults.get(bestSpeedupIndex);
        bestPatchList.get(bestPatchList.size() - 1).putAll(bestPatch);
        return bestPatchList;

    }

    private HashMap<String, String> getTestCoverageResults(boolean oracle, boolean sampled) {
        String testClass;

        if (oracle) {
            testClass = oracleTestClassName;
        }
        else {
            if (sampled) {
                testClass = sampledClassName;
            }
            else {
                testClass = testClassName;
            }
        }
        Logger.info("Getting coverage results for class: " + testClass);
        CoverageMeasurer measurer = new CoverageMeasurer(classNames[0], String.join(":",criterion_list), evosuiteCP, new File(classPath), testClass);
        String coverageOutput = measurer.measureCoverage();
        HashMap<String, String> outputMap = measurer.parseOutput(coverageOutput);
        return outputMap;
    }

    private HashMap<String, String> generateEmptyCoverageResult() {
        HashMap<String ,String> returnMap = new HashMap<String,String>();
        for (int x = 0; x < criterion_list.length; x++) {
            returnMap.put(criterion_list[x].toUpperCase() + "_Coverage", "0");
        }
        return returnMap;
    }

    /*private List<String> parsePatchResult(HashMap<String, String> bestPatch) {
        List<String> finalResults = new ArrayList<String>();
        finalResults.add(bestPatch.get("patch"));
        finalResults.add(bestPatch.get("validpatch"));
        finalResults.add(bestPatch.get("success"));
        finalResults.add(bestPatch.get("avgtime"));
        finalResults.add(bestPatch.get("speedup"));
        return finalResults;

    }*/



    public HashMap<String, String> getPatchAnalysis(boolean manual) {
        String evaluationTest;
        if (manual == false) {
            evaluationTest = oracleTestClassName;
        }
        else {
            evaluationTest = testClassName;
        }
        Logger.info("Evaluation suite for this patch: " + evaluationTest);
        String patchTrim = patchText.trim();
        Logger.info("PatchTrim: " + patchTrim);
        Logger.info("PatchTrimLength: " + patchTrim.length());
        if (patchTrim.length() > 1) {
            Logger.info("Patch trim is legit");
            PatchAnalyser analyser = new PatchAnalyser(filename, patchText, projectDir, classNames[0], classPath, evaluationTest);
            return analyser.getAnalysisResults();
        }
        else {
            Logger.info("Returning empty patch analysis");
            HashMap<String, String> nullPatchList = new HashMap<String, String>();
            nullPatchList.put("patch", patchText);
            nullPatchList.put("validpatch", Boolean.toString(false));
            nullPatchList.put("success", Boolean.toString(true));
            nullPatchList.put("avgtime", Long.toString(0));
            nullPatchList.put("speedup", Float.toString(0));
            return nullPatchList;
        }

    }

    public static List<String> readLastLine(File filename) { // Test if is able to correctly read last line, check for exceptions (invalid input))

        String last, line;
        last = "";
        BufferedReader input = null;
        try {
            input = new BufferedReader(new FileReader(filename));
            while ((line = input.readLine()) != null) {
                last = line;
            }
        }
        catch (IOException e){
            e.printStackTrace();
            Logger.info("Unable to get line, terminating program for file safety");
            System.exit(1);
        }
        finally {
            try {
                if (input != null) {
                    input.close();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

        }

        String[] output = last.split(",");
        ArrayList<String> outputList = new ArrayList<String>();
        for (int i=0; i<output.length; i++) {
            outputList.add(output[i]);
        }
        return outputList;
    }

    protected void writeExperimentHeader() { //stolen code from Sampler Class

        String parentDirName = outputFile.getParent();
        Logger.info("Parent dir: " + parentDirName);
        if (parentDirName == null) {
            parentDirName = "."; // assume outputFile is in the current directory
        }
        File parentDir = new File(parentDirName);
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        try {
            //CSVWriter writer = new CSVWriter(new FileWriter(outputFile));
            //writer.writeNext(EXPERIMENT_HEADER);
            //writer.close();
            outputFileWriter = new CSVWriter(new FileWriter(outputFile));
            outputFileWriter.writeNext(EXPERIMENT_HEADER);
        } catch (IOException e) {
            Logger.error(e, "Exception writing header to the output file: " + outputFile.getAbsolutePath());
            Logger.trace(e);
            System.exit(-1);
        }
    }

    /* private void writeResult(List<String> entries) { //also taken from Sampler class and simplified to take in an array instead
        int resultLength = entries.size();
        String[] entry = new String[resultLength];
        for (int x=0; x<resultLength; x++) {
            entry[x] = entries.get(x);
        }

        try {

            CSVWriter writer = new CSVWriter(new FileWriter(outputFile, true));
            writer.writeNext(entry);
            writer.close();

        } catch (IOException e) {

            Logger.error(e, "Exception writing to the output file: " + outputFile.getAbsolutePath());
            Logger.trace(e);
            System.exit(-1);

        }
    } */

    private void writeResult(List<String[]> entries) { //also taken from Sampler class and simplified to take in an array instead
        //try {

        //    CSVWriter writer = new CSVWriter(new FileWriter(outputFile, true));
        //    //for (List<String> nextOne : entries) {
        //    //   int resultLength = nextOne.size();
        //    //   String[] entry = new String[resultLength];
        //    //   for (int x=0; x<resultLength; x++) {
        //    //       entry[x] = nextOne.get(x);
        //    //   }
        //    //   writer.writeNext(entry);
        //    //}
            for (String[]  entry : entries) {
               outputFileWriter.writeNext(entry);
	    }

        //    writer.close();

        //} catch (IOException e) {

        //    Logger.error(e, "Exception writing to the output file: " + outputFile.getAbsolutePath());
        //    Logger.trace(e);
        //    System.exit(-1);

        //}
    }

    protected void close() {
        try {
            if(outputFileWriter != null){
                outputFileWriter.close();
            }
        } catch(IOException ex){
            Logger.error(ex, "Exception closing the output file: " + outputFile.getAbsolutePath());
            Logger.trace(ex);
            System.exit(-1);
        }
    }

    public HashMap<String,String> generateManualTestStatistics() {
        //"TARGET_CLASS,criterion,Size,Length,Fitness,Total_Time"
        HashMap<String, String> testStatistics = new HashMap<String, String>();
        testStatistics.put("TARGET_CLASS", this.classNames[0]);
        testStatistics.put("criterion", "MANUAL");
        testStatistics.put("Size", "");
        testStatistics.put("Length", "0");
        testStatistics.put("Fitness", "0");
        testStatistics.put("Total_Time", "0");
        testStatistics.putAll(getTestCoverageResults(true, false));
        return testStatistics;
    }

    public static void main(String[] args) {

        Random seedGen = new Random();
        Experiment this_experiment = new Experiment(args);
        Logger.info("evoTestSource: " + this_experiment.evoTestSource.getPath());
        Logger.info("Total iterations: " + this_experiment.totalIterations);
	try {

        this_experiment.writeExperimentHeader();
        int currentEvoSeed = 88;
        int currentGinSeed = 12;
        String currentCriterion;
        int currentIteration = 0;
        HashMap<String, String> coverageResults;
        HashMap<String, String> experimentResults;
        String[] evoOutputHeaders = this_experiment.getEvoOutputVariablesList();
        String[] headers = this_experiment.EXPERIMENT_HEADER;

        for (int iteration = 0; iteration < this_experiment.totalIterations; iteration++) {
            experimentResults = new HashMap<String, String>();
            currentEvoSeed = seedGen.nextInt(100);
	    //currentEvoSeed -= 12; // hack due to EvoSuite non-terminating for some seeds on jcodec
            seedGen.setSeed(currentEvoSeed);
            experimentResults.put("evo_seed", Integer.toString(currentEvoSeed));

            currentCriterion = this_experiment.criterion_list[Math.floorDiv(iteration, this_experiment.iterations)];
            Logger.info("current Iteration is " + iteration);
            Logger.info("current Criterion is " + currentCriterion);

            // End of admin: Test Generation Starts
            this_experiment.generateEvosuiteTests(currentCriterion, currentEvoSeed);
            File testStatisticsFile = new File("evosuite-report/statistics.csv");
            List<String> testStatistics = readLastLine(testStatisticsFile);
            Logger.info("End of test generation");

            //read testStatistics into HashMap

            for (int x = 0; x < evoOutputHeaders.length; x++) {
                experimentResults.put(evoOutputHeaders[x], testStatistics.get(x));
            }
            //get coverage results and place into HashMap
            coverageResults = this_experiment.generateEmptyCoverageResult();
            coverageResults = this_experiment.getTestCoverageResults(false, false);
            Logger.info("Obtained coverage results");

            //Testsampler portion:
            this_experiment.testsampler = new TestSampler(this_experiment.evoTestSource);
            TestSampler sampler = this_experiment.testsampler;
            int intervals = 4;
            int decrement = Math.floorDiv(sampler.getTotalTests(), (intervals - 1));
            if (decrement < 1) {
                decrement = 1;
            }
            for (int x = 0; x < intervals; x++) {
                currentGinSeed = seedGen.nextInt(100);
                seedGen.setSeed(currentGinSeed);
                experimentResults.put("gin_seed", Integer.toString(currentGinSeed));
                Logger.info("Interval " + x);
                experimentResults.put("sampled", "False");
                if (x > 0) { // don't do anything for first iteration, use coverage obtained from main loop
                    experimentResults.put("sampled", "True");
                    sampler.commentOutNTests(sampler.getSampledTestFile(), decrement, currentGinSeed);
                    Logger.info("commentedOut " + decrement + " Tests");
                    this_experiment.testCaseGen.runAllTests();
                    Logger.info("Tests are run and compiled");
                    coverageResults = this_experiment.generateEmptyCoverageResult();
                    if (experimentResults.containsKey("Size")) {
                        int newSize = Integer.parseInt(experimentResults.get("Size")) - decrement;
                        if (newSize < 1) {
                            newSize = 1;
                        }
                        experimentResults.put("Size", String.valueOf(newSize));
                    }
                    coverageResults = this_experiment.getTestCoverageResults(false, true);
                    Logger.info("Got Coverage Results");


                }
                experimentResults.putAll(coverageResults);
                Logger.info("Experiment Results so far: " + experimentResults.toString());
                for (int lsCount = 0; lsCount < this_experiment.ginRuns; lsCount++) {
                    experimentResults.put("Index", Integer.toString(currentIteration + 1));
                    currentGinSeed = seedGen.nextInt(100);
                    experimentResults.put("gin_seed", Integer.toString(currentGinSeed));
                    List<HashMap<String, String>> patchAnalysisResultsList = new ArrayList<>();
                    HashMap<String, String> patchAnalysisResults = new HashMap<>();

                    patchAnalysisResultsList = this_experiment.getBestLocalSearchPatchWithIntermediate(currentGinSeed, 1, false, true);
                    //patchAnalysisResultsList.add(this_experiment.getBestLocalSearchPatch(currentGinSeed, 1, false, true));
                    Logger.info("Got best localsearch patch list");
                    List<String[]> dataEntries = new ArrayList<String[]>();
                    for (int intermediate = 0; intermediate < patchAnalysisResultsList.size(); intermediate++) {
                        patchAnalysisResults = patchAnalysisResultsList.get(intermediate);
                        experimentResults.putAll(patchAnalysisResults);
                        ArrayList<String> dataEntry = new ArrayList<String>();

                        for (int ind = 0; ind < headers.length; ind++) {
                            dataEntry.add(experimentResults.get(headers[ind]));
                        }
                        dataEntries.add(dataEntry.toArray(new String[0]));
                        //this_experiment.writeResult(dataEntry);
                    }
                    this_experiment.writeResult(dataEntries);
                    currentIteration++;
                    Logger.info("CurrentIteration " + currentIteration);
                }

            }

            //global counter for iterations

        }
        //Additional Patch using manually written Test (oracle)
        // First, generate evosuite test which optimises for all coverage.
        this_experiment.generateEvosuiteTests(String.join(":",this_experiment.criterion_list), currentEvoSeed);

        HashMap<String, String> manualExperiment = new HashMap<String, String>();
        HashMap<String, String> manualTestStatistics = this_experiment.generateManualTestStatistics();
        manualExperiment.putAll(manualTestStatistics);

        //Generate patches and datalines for current test file
        List<HashMap<String, String>> patchAnalysisResultsList = new ArrayList<>();
        HashMap<String, String> patchAnalysisResults = new HashMap<>();

        for (int lsCount = 0; lsCount < this_experiment.ginRuns; lsCount++) {
            manualExperiment.put("Index", Integer.toString(currentIteration + 1));
            currentGinSeed = seedGen.nextInt(100);
            patchAnalysisResults = new HashMap<>();

            patchAnalysisResultsList = this_experiment.getBestLocalSearchPatchWithIntermediate(currentGinSeed, 1, true, false);
            //patchAnalysisResultsList.add(this_experiment.getBestLocalSearchPatch(currentGinSeed, 1, true, false));

            List<String[]> dataEntries = new ArrayList<String[]>();
            for (int intermediate = 0; intermediate < patchAnalysisResultsList.size(); intermediate++) {
                patchAnalysisResults = patchAnalysisResultsList.get(intermediate);
                manualExperiment.putAll(patchAnalysisResults);

                ArrayList<String> dataEntry = new ArrayList<String>();

                for (int ind = 0; ind < headers.length; ind++) {
                    dataEntry.add(manualExperiment.get(headers[ind]));
                }
                dataEntries.add(dataEntry.toArray(new String[0]));
                //this_experiment.writeResult(dataEntry);
            }
            this_experiment.writeResult(dataEntries);
            currentIteration++;
        }

    } finally {
        this_experiment.close();
    }


    }



}

