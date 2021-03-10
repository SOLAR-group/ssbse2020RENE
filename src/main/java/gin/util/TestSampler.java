package gin.util;

import org.unix4j.builder.Unix4jCommandBuilder;
import org.unix4j.line.Line;
import org.unix4j.builder.To;
import org.unix4j.unix.Grep;
import org.unix4j.Unix4j;
import org.unix4j.unix.Sed;
import org.unix4j.unix.grep.GrepOption;
import org.unix4j.unix.grep.GrepOptions;
import org.unix4j.unix.sed.SedOptions;

import org.pmw.tinylog.Logger;

import java.io.*;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class TestSampler {
    private File testFile;
    private File sampledTestFile;
    private int totalTests;
    private int testsToSample;
    private HashSet<Integer> openComments;
    private HashSet<Integer> closeComments;
    private HashMap<Integer, Integer> testLineMap;
    private HashMap<Integer, Integer> lineIndexMap;
    private HashSet<Integer> closedTests;

    public TestSampler(File testFile) {
        this.testFile = testFile;
        this.openComments = new HashSet<Integer>();
        this.closeComments = new HashSet<Integer>();
        this.closedTests = new HashSet<Integer>();
        this.testLineMap = new HashMap<Integer, Integer>();
        this.lineIndexMap = new HashMap<Integer, Integer>();
        generateSamplePatchFile(generateNewFileName());
        generateTestLineMaps();
        this.totalTests = getLineNumbersForPattern(sampledTestFile, "@Test").size();
        this.testsToSample = totalTests;
    }

    public String generateNewFileName() {
        String originalName = testFile.getName();
        Logger.info("original Name: " + originalName );
        int lastDotInName = originalName.lastIndexOf('.');
        String newFileName = getClassName(this.testFile) + "_SAMPLED" + getSuffix(this.testFile);
        Logger.info(newFileName);
        return newFileName;
    }

    private String getClassName(File file) {
        String originalName = file.getName();
        Logger.info("original Name: " + originalName );
        int lastDotInName = originalName.lastIndexOf('.');
        return originalName.substring(0,lastDotInName);
    }

    private String getSuffix(File file) {
        String originalName = file.getName();
        int lastDotInName = originalName.lastIndexOf('.');
        return originalName.substring(lastDotInName);
    }

    private static String addSpaces(String str) {
        return " " + str + " ";
    }


    public void generateSamplePatchFile(String newFileName)  {
        String originalFilePath = testFile.getAbsolutePath();
        String originalFileName = testFile.getName();
        String originalClassName = getClassName(testFile);
        Logger.info("original file name: " + originalFileName);
        String newFilePath = originalFilePath.replace(originalFileName, newFileName);
        Logger.info("NewFilePath: " + newFilePath);
        sampledTestFile = new File(newFilePath);
        String newClassName = getClassName(sampledTestFile);
        try {
            if ((Files.exists(sampledTestFile.toPath()))) {
                Logger.info("File exists already now, deleting");
                Files.deleteIfExists(sampledTestFile.toPath());
                //sampledTestFile.createNewFile();
            }

            Files.copy(testFile.toPath(), sampledTestFile.toPath());
            Logger.info("Trying to replace " + originalClassName + " with " + newClassName);

            String lineReplacement = Unix4j.cat(newFilePath).grep(addSpaces(originalClassName)).sed(("s/"+addSpaces(originalClassName)+"/"+addSpaces(newClassName)+"/g")).toStringResult();
            Logger.info(lineReplacement);

            Integer lineNumberOfClassName = getLineNumbersForPattern(sampledTestFile, originalClassName).get(0);
            replaceLineAtNumber(sampledTestFile, lineNumberOfClassName, lineReplacement);

            this.testsToSample = totalTests;
            this.closedTests = new HashSet<Integer>();
            this.closeComments = new HashSet<Integer>();
            this.openComments = new HashSet<Integer>();


        }
        catch (IOException e) {
            Logger.info("Exception: " + e.toString());

        }

    }

    public void commentOutTests(List<Integer> testIndexes) {
        HashMap<Integer, Integer> instructionMap = new HashMap<Integer, Integer>();
        // search for testLines using index (hashmap).
        // if position starts with /* already, leave it. IF starts with */, remove the */. Otherwise add a /*
        // go to line at testIndex + 1. If it is an open bracket /*, there must be a close bracket at next test
        // else: if there is a closed bracket there, it is already commented out.
        // if there is nothing there, add a closeBracket.
        Collections.sort(testIndexes);
        for (int x = 0; x < testIndexes.size(); x++) {
            int testIndex = testIndexes.get(x);
            if (closedTests.contains(testIndex) || testIndex >= (totalTests - 1)) {
                continue;
            }
            int lineNum = testLineMap.get(testIndexes.get(x));

            if (openComments.contains(testIndex)) {
                Logger.info("already commented");
                continue;
            }
            if (closeComments.contains(testIndex)) {
                instructionMap.put(lineNum, -2);
                closeComments.remove(testIndex);
            }
            else {
                instructionMap.put(lineNum, 1);
                openComments.add(testIndex);
            }
            //closing brackets placement
            int nextIndex = testIndexes.get(x) + 1;
            int nextLineNum = testLineMap.get(nextIndex);

            if (openComments.contains(nextIndex)) {
                Logger.info("already commented");
                instructionMap.put(nextLineNum, -1); //remove open comment so open comment will fall through
                openComments.remove(nextIndex);
            }
            else if (closeComments.contains(nextIndex)) {
                continue;
            }
            else {
                instructionMap.put(nextLineNum, 2);
                closeComments.add(nextIndex);
            }
            this.closedTests.add(testIndex);
            testsToSample--;
        }
        //Instruction Map populated, now edit file
        BufferedReader reader = null;
        BufferedWriter writer = null;
        try {
            reader = new BufferedReader(new FileReader(sampledTestFile));
            String content = "";
            String line = reader.readLine();
            Integer lineCount = 1;
            while (line != null) {
                if (!instructionMap.containsKey(lineCount)) {
                    content = content + line + System.lineSeparator();
                }
                else {
                    content = content + commentOperation(line, instructionMap.get(lineCount), lineCount) + System.lineSeparator();
                }
                lineCount++;
                line = reader.readLine();
            }
            writer = new BufferedWriter(new FileWriter(sampledTestFile));
            writer.write(content);

        }
        catch (IOException e){
            System.err.println(e.toString());
        }
        finally {
            try { if (reader != null) reader.close(); } catch(IOException e) {}
            try { if (writer != null) writer.close(); } catch(IOException e) {}

        }
    }

    public void commentOutNTests(File file, int numberofTests, int seed) {
        Random rng = new Random();
        rng.setSeed(seed);
        List<Integer> testIndexes = new ArrayList<>();
        int index;
        HashSet<Integer> outset = new HashSet<>();
        if (numberofTests >= testsToSample) {
            numberofTests = testsToSample - 1; // all tests except 1
        }
        Logger.info("Tests to sample " + testsToSample);
        Logger.info("Number of tests to remove " + numberofTests);
        for (int x = 0; x < numberofTests; x++) {
            index = rng.nextInt(this.totalTests - 1);
            Logger.info("Closed : " + closedTests.toString());
            Logger.info("Commented out already : " + outset.toString());
            Logger.info("trying to remove index " + index);
            while (closedTests.contains(index) || outset.contains(index)) {
                index = rng.nextInt(this.totalTests - 1);
            }
            testIndexes.add(index);
            outset.add(index);
        }
        Logger.info(testIndexes.toString());
        commentOutTests(testIndexes);

    }

    public void keepNTests(File file, int numberofTests, int seed) {
        Random rng = new Random();
        rng.setSeed(seed);
        List<Integer> testIndexes = new ArrayList<>();
        int index;
        HashSet<Integer> outset = new HashSet<>();
        if (numberofTests >= testsToSample) {
            numberofTests = testsToSample; // keep All Tests
        }
        if (numberofTests <= 0) {
            numberofTests = 1; // must keep at least 1 test
        }
        for (int x = 0; x < (testsToSample - numberofTests) ; x++) {
            index = rng.nextInt(this.totalTests - 2);
            while (closedTests.contains(index) || outset.contains(index)) {
                index = rng.nextInt(this.totalTests - 2);
            }
            testIndexes.add(index);
            outset.add(index);
        }
        Logger.info(testIndexes.toString());
        commentOutTests(testIndexes);

    }


    private String commentOperation(String input, Integer opcode, Integer lineNum) {
        String returnValue = "";
        switch (opcode) {
            case -1: // remove close comment sign
                returnValue = input.replace("/*", "");
                //openComments.remove(lineIndexMap.get(lineNum));
                break;

            case -2: // remove open comment sign
                returnValue = input.replace("*/", "");
                //closeComments.remove(lineIndexMap.get(lineNum));
                break;

            case 1:
                returnValue = "/*" + input;
                //openComments.add(lineIndexMap.get(lineNum));
                break;
            case 2:
                returnValue = "*/" + input;
                //closeComments.add(lineIndexMap.get(lineNum));
                break;
        }
        Logger.info(returnValue);
        return returnValue;
    }

    public static List<Integer> getLineNumbersForPattern(File fileToSearch, String regex) {
        List<String> linesWithString = Unix4j.grep(Grep.Options.n, regex, fileToSearch).toStringList();
        Logger.info(linesWithString.toString());
        Pattern lineNumberRegex = Pattern.compile(":\\d+:");
        Matcher lineNumberMatcher;
        List<Integer> lineNumbers = new ArrayList<Integer>();
        for (int x = 0; x < linesWithString.size();x++) {
            lineNumberMatcher = lineNumberRegex.matcher(linesWithString.get(x));
            if (lineNumberMatcher.find()) {
                String matched = lineNumberMatcher.group(0);
                matched = matched.replaceAll("\\D+","");
                lineNumbers.add(Integer.parseInt(matched));
            }


        }
        return lineNumbers;

    }

    private void generateTestLineMaps() {
        List<Integer> linenums = getLineNumbersForPattern(sampledTestFile, "@Test");
        HashMap<Integer, Integer> mapping = new HashMap<Integer, Integer>();
        HashMap<Integer, Integer> revMapping = new HashMap<Integer, Integer>();
        for (int x = 0; x < linenums.size(); x++) {
            mapping.put(x, linenums.get(x));
            revMapping.put(linenums.get(x), x);
        }
        this.testLineMap = mapping;
        this.lineIndexMap = revMapping;
    }



    public static void replaceLineAtNumber(File file, Integer lineNum, String replacement) {
        BufferedReader reader = null;
        BufferedWriter writer = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String content = "";
            String line = reader.readLine();
            Integer lineCount = 1;
            while (line != null) {
                if (lineCount != lineNum) {
                    content = content + line + System.lineSeparator();
                }
                else {
                    content = content + replacement + System.lineSeparator();
                }
                lineCount++;
                line = reader.readLine();
            }
            writer = new BufferedWriter(new FileWriter(file));
            writer.write(content);

        }
        catch (IOException e ){

        }
        finally {
            try { if (reader != null) reader.close(); } catch(IOException e) {}
            try { if (writer != null) writer.close(); } catch(IOException e) {}

        }


    }


    public File getTestFile() {
        return testFile;
    }

    public File getSampledTestFile() {
        return sampledTestFile;
    }

    public int getTotalTests() {
        return totalTests;
    }

    public int getTestsToSample() {
        return testsToSample;
    }
}
