package gin;

import java.io.File;
import java.util.*;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.rng.simple.JDKRandomBridge;
import org.apache.commons.rng.simple.RandomSource;
import org.pmw.tinylog.Logger;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

import gin.edit.Edit;
import gin.test.InternalTestRunner;
import gin.test.UnitTestResult;
import gin.test.UnitTestResultSet;

/**
 * Simple local search. Takes a source filename and a method signature, optimises it.
 * Assumes the existence of accompanying Test Class.
 * The class must be in the top level package, if classPath not provided.
 */
public class LocalSearch {

    private static final int WARMUP_REPS = 10;
    private static final int REPS = 1;

    @Argument(alias = "f", description = "Required: Source filename", required=true)
    protected File filename = null;

    @Argument(alias = "m", description = "Required: Method signature including arguments." +
                                         "For example, \"classifyTriangle(int,int,int)\"", required=true)
    protected String methodSignature = "";

    @Argument(alias = "s", description = "Seed")
    protected Integer seed = 123;

    @Argument(alias = "n", description = "Number of steps")
    protected Integer numSteps = 100;

    @Argument(alias = "d", description = "Top directory")
    protected File packageDir;

    @Argument(alias = "c", description = "Class name")
    protected String className;

    @Argument(alias = "cp", description = "Classpath")
    protected String classPath;

    @Argument(alias = "t", description = "Test class name")
    protected String testClassName;

    @Argument(alias = "o", description = "Oracle class name")
    protected String oracleClassName;

    @Argument(alias = "et", description = "Edit Type")
    protected String editType = "LINE";

    protected SourceFile sourceFile;
    InternalTestRunner testRunner;
    protected Random rng;

    InternalTestRunner oracleRunner;

    // Instantiate a class and call search
    public static void main(String[] args) {
        LocalSearch simpleLocalSearch = new LocalSearch(args);
        simpleLocalSearch.search();
    }

    // Constructor parses arguments
    LocalSearch(String args[]) {

        Args.parseOrExit(this, args);

        this.sourceFile = new SourceFileLine(this.filename, this.methodSignature);
        this.rng = new JDKRandomBridge(RandomSource.MT, Long.valueOf(seed));
        if (this.packageDir == null) {
            this.packageDir = this.filename.getParentFile().getAbsoluteFile();
        }
        if (this.classPath == null) {
            this.classPath = this.packageDir.getAbsolutePath();
        }
        if (this.className == null) {
            this.className = FilenameUtils.removeExtension(this.filename.getName());
        }
        if (this.testClassName == null) {
            this.testClassName = this.className + "Test";
        }
        if (this.oracleClassName == null) {
            this.oracleClassName = this.className + "_ESTest";
        }
        this.testRunner = new InternalTestRunner(className, classPath, testClassName);
        this.oracleRunner = new InternalTestRunner(className, classPath, oracleClassName);

    }

    //Alternate public constructor for chaining
    public LocalSearch(File filename, String methodSignature, Integer seed, Integer numSteps, File packageDir, String className, String classPath, String testClassName, String oracleClassName, String editType) {
        this.filename = filename;
        this.methodSignature = methodSignature;
        this.seed = seed;
        this.numSteps = numSteps;
        this.packageDir = packageDir;
        this.className = className;
        this.classPath = classPath;
        this.testClassName = testClassName;
        this.editType = editType;
        this.oracleClassName = oracleClassName;

        this.rng = new JDKRandomBridge(RandomSource.MT, Long.valueOf(seed));
        if (this.editType.equals("LINE")) {
            Logger.info("Using LINE edits");
            this.sourceFile = new SourceFileLine(this.filename, this.methodSignature);
        }
        else {
            Logger.info("Using STATEMENT edits: editType = " + editType);
            this.sourceFile = new SourceFileTree(this.filename, this.methodSignature);
        }
        if (this.packageDir == null) {
            this.packageDir = this.filename.getParentFile().getAbsoluteFile();
        }
        if (this.classPath == null) {
            this.classPath = this.packageDir.getAbsolutePath();
        }
        if (this.className == null) {
            Logger.info("Null className");
            this.className = FilenameUtils.removeExtension(this.filename.getName());
            Logger.info("New classname is : " + this.className);
        }
        if (this.testClassName == null) {
            this.testClassName = this.className + "Test";
        }
        if (this.oracleClassName == null) {
            this.oracleClassName = this.className + "_ESTest";
        }
        this.testRunner = new InternalTestRunner(this.className, this.classPath, this.testClassName);
        this.oracleRunner = new InternalTestRunner(this.className, this.classPath, this.oracleClassName);
    }

    // Apply empty patch and return execution time
    private long timeOriginalCode() {

        Patch emptyPatch = new Patch(this.sourceFile);
        UnitTestResultSet resultSet = testRunner.runTests(emptyPatch, WARMUP_REPS);

        if (!resultSet.allTestsSuccessful()) {

            if (!resultSet.getCleanCompile()) {

                Logger.error("Original code failed to compile");

            } else {

                Logger.error("Original code failed to pass unit tests");

                for (UnitTestResult testResult: resultSet.getResults()) {
                    Logger.error(testResult);
                }

            }

            System.exit(0);

        }

        return resultSet.totalExecutionTime() / WARMUP_REPS;

    }

    // Simple local search
    private void search() {

        Logger.info(String.format("Localsearch on file: %s method: %s", filename, methodSignature));

        // Time original code
        long origTime = timeOriginalCode();

        Patch emptyPatch = new Patch(this.sourceFile);
        UnitTestResultSet origResultSet = testRunner.runTests(emptyPatch, REPS);
	origTime = origResultSet.totalExecutionTime();

        Logger.info("Original execution time: " + origTime + "ns");

        // Start with empty patch
        Patch bestPatch = new Patch(this.sourceFile);
        long bestTime = origTime;

        for (int step = 1; step <= numSteps; step++) {

            Patch neighbour = neighbour(bestPatch);
            UnitTestResultSet testResultSet = testRunner.runTests(neighbour, REPS);

            String msg;

            if (!testResultSet.getValidPatch()) {
                msg = "Patch invalid";
            } else if (!testResultSet.getCleanCompile()) {
                msg = "Failed to compile";
            } else if (!testResultSet.allTestsSuccessful()) {
                msg = ("Failed to pass all tests");
            } else if (testResultSet.totalExecutionTime() >= bestTime) {
                msg = "Time: " + testResultSet.totalExecutionTime() + "ns";
            } else {
                bestPatch = neighbour;
                bestTime = testResultSet.totalExecutionTime();
                msg = "New best time: " + bestTime + "(ns)";
            }

            Logger.info(String.format("Step: %d, Patch: %s, %s ", step, neighbour, msg));

        }

        Logger.info(String.format("Finished. Best time: %d (ns), Speedup (%%): %.2f, Patch: %s",
                                    bestTime,
                                    100.0f *((origTime - bestTime)/(1.0f * origTime)),
                                    bestPatch));

        //bestPatch.writePatchedSourceToFile(sourceFile.getFilename() + ".optimised");
        //bestPatch.writePatchStringToFile("bestpatch.txt");

    }

    public String getPatchFromSearch() {

        Logger.info(String.format("Localsearch on file: %s method: %s", filename, methodSignature));

        // Time original code
        long origTime = timeOriginalCode();

        Patch emptyPatch = new Patch(this.sourceFile);
        UnitTestResultSet origResultSet = testRunner.runTests(emptyPatch, REPS);
	origTime = origResultSet.totalExecutionTime();

        Logger.info("Original execution time: " + origTime + "ns");

        // Start with empty patch
        Patch bestPatch = new Patch(this.sourceFile);
        long bestTime = origTime;

        for (int step = 1; step <= numSteps; step++) {

            Patch neighbour = neighbour(bestPatch);
            UnitTestResultSet testResultSet = testRunner.runTests(neighbour, 1);

            String msg;

            if (!testResultSet.getValidPatch()) {
                msg = "Patch invalid";
            } else if (!testResultSet.getCleanCompile()) {
                msg = "Failed to compile";
            } else if (!testResultSet.allTestsSuccessful()) {
                msg = ("Failed to pass all tests");
            } else if (testResultSet.totalExecutionTime() >= bestTime) {
                msg = "Time: " + testResultSet.totalExecutionTime() + "ns";
            } else {
                bestPatch = neighbour;
                bestTime = testResultSet.totalExecutionTime();
                msg = "New best time: " + bestTime + "(ns)";
            }

            Logger.info(String.format("Step: %d, Patch: %s, %s ", step, neighbour, msg));

        }

        Logger.info(String.format("Finished. Best time: %d (ns), Speedup (%%): %.2f, Patch: %s",
                bestTime,
                100.0f *((origTime - bestTime)/(1.0f * origTime)),
                bestPatch));

        //bestPatch.writePatchedSourceToFile(sourceFile.getFilename() + ".optimised");
        //bestPatch.writePatchStringToFile("bestpatch.txt");
        return bestPatch.toString();
    }

    //Returns hashmap of all intermediate patches and their properties
    public List<HashMap<String,String>> getPatchFromSearchWithIntermediate() {

        Logger.info(String.format("Localsearch on file: %s method: %s", filename, methodSignature));

        // Time original code
        long origTime = timeOriginalCode();

        Patch emptyPatch = new Patch(this.sourceFile);
        UnitTestResultSet origResultSet = testRunner.runTests(emptyPatch, REPS);
	origTime = origResultSet.totalExecutionTime();

        Logger.info("Original execution time: " + origTime + "ns");

        // Start with empty patch
        Patch bestPatch = new Patch(this.sourceFile);
        long bestTime = origTime;
        ArrayList<HashMap<String,String>> patchResultsList = new ArrayList<>();
        for (int step = 1; step <= numSteps; step++) {

            Patch neighbour = neighbour(bestPatch);

	    UnitTestResultSet testResultSet;
            UnitTestResultSet oracleResultSet;
            HashMap<String, String> oracleResultParsed;
            String msg;

	    if (neighbour.getEdits().size()==0) {
		testResultSet = origResultSet;	
		oracleResultSet = origResultSet;	
                msg = "Time: " + origTime + "ns";
                oracleResultParsed = new HashMap<String, String>();
                oracleResultParsed.put("patch", neighbour.toString());
                oracleResultParsed.put("success", Boolean.toString(oracleResultSet.allTestsSuccessful()));
                oracleResultParsed.put("intermediate", Boolean.toString(true));
                oracleResultParsed.put("validpatch", Boolean.toString(true));
                oracleResultParsed.put("speedup", "--");
                oracleResultParsed.put("avgtime", "-");
                patchResultsList.add(oracleResultParsed);
	    } else {
                testResultSet = testRunner.runTests(neighbour, 1);
                if (!testResultSet.getValidPatch()) {
                    msg = "Patch invalid";
                } else if (!testResultSet.getCleanCompile()) {
                    msg = "Failed to compile";
                } else if (!testResultSet.allTestsSuccessful()) {
                    msg = ("Failed to pass all tests");
                } else if (testResultSet.totalExecutionTime() >= bestTime) {
                    msg = "Time: " + testResultSet.totalExecutionTime() + "ns";
                    oracleResultSet = oracleRunner.runTests(neighbour, 1);
                    oracleResultParsed = new HashMap<String, String>();
                    oracleResultParsed.put("patch", neighbour.toString());
                    oracleResultParsed.put("success", Boolean.toString(oracleResultSet.allTestsSuccessful()));
                    oracleResultParsed.put("intermediate", Boolean.toString(true));
                    oracleResultParsed.put("validpatch", Boolean.toString(true));
                    oracleResultParsed.put("speedup", "--");
                    oracleResultParsed.put("avgtime", "-");
                    patchResultsList.add(oracleResultParsed);
                } else {
                    bestPatch = neighbour;
                    bestTime = testResultSet.totalExecutionTime();
                    msg = "New best time: " + bestTime + "(ns)";

                    //Evaluate intermediate patch against oracle and save results
                    oracleResultSet = oracleRunner.runTests(neighbour, 1);
                    oracleResultParsed = new HashMap<String, String>();
                    oracleResultParsed.put("patch", bestPatch.toString());
                    oracleResultParsed.put("success", Boolean.toString(oracleResultSet.allTestsSuccessful()));
                    oracleResultParsed.put("intermediate", Boolean.toString(true));
                    oracleResultParsed.put("validpatch", Boolean.toString(true));
                    oracleResultParsed.put("speedup", "-");
                    oracleResultParsed.put("avgtime", "-");
                    patchResultsList.add(oracleResultParsed);
                }
	    }

            Logger.info(String.format("Step: %d, Patch: %s, %s ", step, neighbour, msg));

        }

        Logger.info(String.format("Finished. Best time: %d (ns), Speedup (%%): %.2f, Patch: %s",
                bestTime,
                100.0f *((origTime - bestTime)/(1.0f * origTime)),
                bestPatch));
        //Mark best patch as non-intermediate patch
        if (patchResultsList.size() > 0) {
            int rev = 1;
            while (rev < patchResultsList.size()) {
                if (patchResultsList.get(patchResultsList.size() - rev).get("speedup") == "-") {
                    break;
                }
                rev++;
            }
            patchResultsList.add(patchResultsList.get(patchResultsList.size() - rev));
            patchResultsList.get(patchResultsList.size() - 1).put("intermediate", Boolean.toString(false));

        }
        else { // case where no patch is found
            HashMap<String, String> emptyPatchResult = new HashMap<String, String>();
            emptyPatchResult.put("patch", "|");
            emptyPatchResult.put("intermediate", Boolean.toString(false));
            patchResultsList.add(emptyPatchResult);
        }
        //bestPatch.writePatchedSourceToFile(sourceFile.getFilename() + ".optimised");
        //bestPatch.writePatchStringToFile("bestpatch.txt");
        return patchResultsList;
    }


    /**
     * Generate a neighbouring patch, by either deleting an edit, or adding a new one.
     * @param patch Generate a neighbour of this patch.
     * @return A neighbouring patch.
     */
    Patch neighbour(Patch patch) {

        Patch neighbour = patch.clone();

        Edit.EditType thisEditType;
        if (this.editType.equals("LINE")) {
            thisEditType = Edit.EditType.LINE;
        }
        else {
            thisEditType = Edit.EditType.STATEMENT;
        }
        if (neighbour.size() > 0 && rng.nextFloat() > 0.5) {
            neighbour.remove(rng.nextInt(neighbour.size()));
        } else {
            neighbour.addRandomEdit(rng, Collections.singletonList(thisEditType));
        }

        return neighbour;

    }


}
