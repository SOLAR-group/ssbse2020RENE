package gin.util;

import java.io.File;
import java.io.FileReader;
import org.apache.commons.io.FileUtils;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;
import java.text.ParseException;

import com.opencsv.CSVReaderHeaderAware;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import org.pmw.tinylog.Logger;
import org.zeroturnaround.exec.ProcessExecutor;

import gin.Patch;
import gin.SourceFileLine;
import gin.SourceFileTree;
import gin.edit.Edit;
import gin.edit.Edit.EditType;
import gin.test.UnitTestResultSet;

public class PatchTester extends Sampler{

    @Argument(alias = "patchFile", description = "File with a list of patches (e.g., edits_overlaps_all.csv from results/data)")
    protected File patchFile;

    protected List<Entry<String, Entry<Integer,String>>> patchData = new ArrayList<>();

    protected int countSeen = 0;

    public static void main(String[] args) {
        PatchTester sampler = new PatchTester(args);
        sampler.sampleMethodsHook();
    }

    public PatchTester(String[] args) {
        super(args);
        Args.parseOrExit(this, args);
        printAdditionalArguments();
        this.patchData = processPatchFile();
        if (patchData.isEmpty()) {
            Logger.info("No patches to process.");
            System.exit(0);
        }
    }

    // Constructor used for testing
    public PatchTester(File projectDir, File methodFile, File patchFile) {
        super(projectDir, methodFile);
        this.patchFile = patchFile;
    }

    private void printAdditionalArguments() {
        Logger.info("Patch file: "+ patchFile);
    }

    protected void sampleMethodsHook() {
        
        writeHeader();
	int counter = 0;
	int wrong = 0;

        for (Entry<String, Entry<Integer,String>> entry : patchData) {

	    //Logger.info("counter: " + counter);

            String patchText = entry.getKey();
            Integer methodID = (entry.getValue()).getKey();
	    String editResult = (entry.getValue()).getValue();

            TargetMethod method = null;

            for (TargetMethod m : methodData) {
                if (m.getMethodID().equals(methodID)) {
                    method = m;
                    break;
                }
            }
           
            if (method == null) {

		wrong++;
                Logger.info("Method with ID: " + methodID.toString() + " not found for patch " + patchText);

            } else { 

	        counter++;
                // Get method location
                File source = method.getFileSource();
                String className = method.getClassName();

                // Create source files for edits for the  method
                SourceFileLine sourceFileLine = new SourceFileLine(source.getPath(), null);
                SourceFileTree sourceFileTree = new SourceFileTree(source.getPath(), null);
                
                // Parse patch
                Patch patch = parsePatch(patchText, sourceFileLine, sourceFileTree, editResult);

                //Logger.info("Running tests on patch: " + patch.toString());

                //// Run all project tests (example sourceFile and className needed for TestRunner setup)
                ////UnitTestResultSet results = testPatch(className, super.testData, patch);
                //UnitTestResultSet results = testPatch(className, method.getGinTests(), patch);

                //writeResults(results, methodID);

                //Logger.info("All tests successful: " + results.allTestsSuccessful());
            }
	    //if (counter > 10) {
	    //    break;
	    //}
        }

	Logger.info("Total processed: " + counter);
	Logger.info("Total wrong: " + wrong);
	Logger.info("Total seen: " + countSeen);
        //Logger.info("Results saved to: " + super.outputFile.getAbsolutePath());
    }

    private List<Entry<String,Entry<Integer,String>>> processPatchFile() {

        try {
            CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(patchFile));
            Map<String, String> data = reader.readMap();
            if ( (!data.containsKey("Patch")) || (!data.containsKey("MethodIndex")) || (!data.containsKey("EditCompiled"))) {
                throw new ParseException("Both \"Patch\" and \"MethodIndex\" and \"EditCompiled\" fields are required in the patch file.", 0);
            }

            List<Entry<String,Entry<Integer,String>>> patches = new ArrayList<>();

            while (data != null) {

                String patch = data.get("Patch");
                Integer methodID = Integer.valueOf(data.get("MethodIndex"));
		String editResult = data.get("EditCompiled");
                patches.add(new SimpleEntry( patch, new SimpleEntry(methodID,editResult) ) );

                data = reader.readMap();
            }        
            reader.close();

            return patches;

        } catch (ParseException e) {
            Logger.error(e.getMessage());
            Logger.trace(e);
        } catch (IOException e) {
            Logger.error("Error reading patch file: " + patchFile);
            Logger.trace(e);
        }
        return new ArrayList<>();

    }

    private Patch parsePatch(String patchText, SourceFileLine sourceFileLine, SourceFileTree sourceFileTree, String editResult) {

        List<Edit> editInstances = new ArrayList<>();
        
        String patchTrim = patchText.trim();
        String cleanPatch = patchTrim;

        if (patchTrim.startsWith("|")) {
            cleanPatch = patchText.replaceFirst("\\|", "").trim();
        }

        String[] editStrings = cleanPatch.trim().split("\\|");
        
        boolean allLineEdits = true;
        boolean allStatementEdits = true;
        
        for (String editString: editStrings) {

            String[] tokens = editString.trim().split("\\s+");

            String editAction = tokens[0];

            Class<?> clazz = null;

            try {
                clazz = Class.forName(editAction);
            } catch (ClassNotFoundException e) {
                Logger.error("Patch edit type unrecognised: " + editAction);
                Logger.trace(e);
                System.exit(-1);
            }

            Method parserMethod = null;
            try {
                parserMethod = clazz.getMethod("fromString", String.class);
            } catch (NoSuchMethodException e) {
                Logger.error("Patch edit type has no fromString method: " + clazz.getCanonicalName());
                Logger.trace(e);
                System.exit(-1);
            }

            Edit editInstance = null;
            try {
                editInstance = (Edit) parserMethod.invoke(null, editString.trim());
            } catch (IllegalAccessException e) {
                Logger.error("Cannot parse patch: access error invoking edit class.");
                Logger.trace(e);
                System.exit(-1);
            } catch (InvocationTargetException e) {
                Logger.error("Cannot parse patch: invocation error invoking edit class.");
                Logger.trace(e);
                System.exit(-1);
            }

            allLineEdits &= editInstance.getEditType() == EditType.LINE;
            allStatementEdits &= editInstance.getEditType() != EditType.LINE;
            editInstances.add(editInstance);
            
        }
        
        if (!allLineEdits && !allStatementEdits) {
            Logger.error("Cannot proceed: mixed line/statement edit types found in patch");
            System.exit(-1);
        }
        
        Patch patch = new Patch(allLineEdits ? sourceFileLine : sourceFileTree);

        String original = patch.getSourceFile().toString();
        try {
            FileUtils.writeStringToFile(new File("source.original"), original, Charset.defaultCharset());
        } catch (IOException e) {
            Logger.error("Could not write original source.");
            Logger.trace(e);
            System.exit(-1);
        }

	List<String> patchedSources = new ArrayList<>();
	patchedSources.add(original);
	int falseIndex = editResult.indexOf("FP");
	assert(falseIndex != -1);
	boolean seen = false;
        for (Edit e : editInstances) {
            patch.add(e);
            //Logger.info("Added next edit: " + e.toString());
            String patched = patch.apply();
	    if (patch.getEdits().size() == falseIndex + 2) {
	      Logger.info(falseIndex);
	      Logger.info(patch.getEdits().size());
	      for (String previous : patchedSources) {
	          if (isPatchedSourceSame(previous, patched)) {
	              seen = true;
		      countSeen++;
	          }
	      }
	    }
	    patchedSources.add(patched);
        }

	if (!seen) {



	    String patchedP = patchedSources.get(patchedSources.size()-1);
            try {
                FileUtils.writeStringToFile(new File("source.patchedP"), patchedP, Charset.defaultCharset());
            } catch (IOException ex) {
                Logger.error("Could not write patched source.");
                Logger.trace(ex);
                System.exit(-1);
            }
	    Logger.info("diff between original and new patch");
            try {
                String output = new ProcessExecutor().command("diff", "source.original", "source.patchedP")
                          .readOutput(true).execute()
                          .outputUTF8(); 
                Logger.info(output);
            } catch (IOException ex) {
                Logger.trace(ex);
                System.exit(-1);
            } catch (InterruptedException ex) {
                Logger.trace(ex);
                System.exit(-1);
            } catch (TimeoutException ex) {
                Logger.trace(ex);
                System.exit(-1);
            }

	    String originalP = patchedSources.get(0);
	    originalP = originalP.replaceAll("[\\n\\t ]", "");
	    patchedP = patchedP.replaceAll("[\\n\\t ]", "");
	    System.out.println(originalP==patchedP);
	   // String patchedF = patchedSources.get(falseIndex+1);
	   // String patchedP = patchedSources.get(falseIndex+2);
           // try {
           //     FileUtils.writeStringToFile(new File("source.patchedF"), patchedF, Charset.defaultCharset());
           // } catch (IOException ex) {
           //     Logger.error("Could not write patched source.");
           //     Logger.trace(ex);
           //     System.exit(-1);
           // }
           // try {
           //     FileUtils.writeStringToFile(new File("source.patchedP"), patchedP, Charset.defaultCharset());
           // } catch (IOException ex) {
           //     Logger.error("Could not write patched source.");
           //     Logger.trace(ex);
           //     System.exit(-1);
           // }

	   // Logger.info("diff between original and failing patch");
           // try {
           //     String output = new ProcessExecutor().command("diff", "source.original", "source.patchedF")
           //               .readOutput(true).execute()
           //               .outputUTF8(); 
           //     Logger.info(output);
           // } catch (IOException ex) {
           //     Logger.trace(ex);
           //     System.exit(-1);
           // } catch (InterruptedException ex) {
           //     Logger.trace(ex);
           //     System.exit(-1);
           // } catch (TimeoutException ex) {
           //     Logger.trace(ex);
           //     System.exit(-1);
           // }

	   // Logger.info("diff between original and next passing patch");
           // try {
           //     String output = new ProcessExecutor().command("diff", "source.original", "source.patchedP")
           //               .readOutput(true).execute()
           //               .outputUTF8(); 
           //     Logger.info(output);
           // } catch (IOException ex) {
           //     Logger.trace(ex);
           //     System.exit(-1);
           // } catch (InterruptedException ex) {
           //     Logger.trace(ex);
           //     System.exit(-1);
           // } catch (TimeoutException ex) {
           //     Logger.trace(ex);
           //     System.exit(-1);
           // }
	}

        return patch;

    }

    protected boolean isPatchedSourceSame(String original, String patchedSource) {
        String normalisedPatched = patchedSource.replaceAll("//.*\\n", "");
        String normalisedOriginal = original.replaceAll("//.*\\n", "");
        normalisedPatched = normalisedPatched.replaceAll("\\s+", " ");
        normalisedOriginal = normalisedOriginal.toString().replaceAll("\\s+", " ");
        normalisedOriginal = normalisedOriginal.toString().replaceAll("\\s+", " ");
        boolean noOp = normalisedPatched.equals(normalisedOriginal);
        return noOp;
    }
}
