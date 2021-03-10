import os
import shutil
import sys

mavenhome = "/usr/local/" #Add maven directory here

platform = sys.platform
if platform != "win32":
    platform = "others"
print("Current platform: " + platform)
sep = os.path.sep
#Create experiments file
try:
    os.mkdir("experiments")
except FileExistsError:
    existingfiles = [f for f in os.listdir("experiments") if f[-3:]==".sh" or f[-4:]==".bat"]
    print("Deleting existing experiment script files: " , existingfiles, "\n")
    for fn in existingfiles:
        fnpath = "experiments" + sep + fn
        os.remove(fnpath)




projectdir = sep.join(["..","examples","locoGP"])
projectname = "locogp"
evosuitecp = sep.join(["..","testgeneration","evosuite-1.0.6.jar"])
classnamesBASE = "locogp."
criterion="branch"
filepathBASE = projectdir + sep + sep.join(["src", "main", "java", "locogp"]) +sep
methodsignature = "\"sort(Integer[], Integer)\""
classpath= projectdir + sep + sep.join(["target", "classes"])
testclasspath= projectdir + sep + sep.join(["target", "test-classes"])
evosuitetestBASE = "locogp."
oracletestBASE = "locogp."
criterionlist="branch,line,weakmutation,cbranch"
iterations = "1"
evotestsourceBASE = projectdir + sep + sep.join(["src", "test", "java", "locogp"]) + sep
edittype = "STATEMENT"

ginpath = sep.join(["..", "build", "gin.jar"])

#sourcpath is path of source files relative to experiment directory
sourcepath = sep.join(["examples", "locoGP", "src", "main", "java", "locogp"])
javafiles = [f for f in os.listdir(sourcepath) if f[-5:]==".java"]
print("Files to generate experiments for: ",  javafiles, "\n")

for javafile in javafiles:
    classname = javafile[:-5]
    classnames = classnamesBASE + classname
    oracletest = oracletestBASE + classname + "Test"
    evosuitetest = evosuitetestBASE + classname + "_ESTest"
    evotestsource = evotestsourceBASE + classname + "_ESTest.java"
    filepath = filepathBASE + javafile

    expCommand = " ".join(["java", "-cp", ginpath + os.pathsep + evosuitecp, "gin.util.Experiment", \
                            "-d", projectdir, "-p" , projectname, "-mavenHome", mavenhome, "-evosuiteCP", evosuitecp, \
                            "-classNames", classnames, "-generateTests", "-criterion", criterion, "-f", filepath, \
                           "-m", methodsignature, "-cp", classpath + os.pathsep + testclasspath, "-t", evosuitetest, \
                           "-evoTestSource", evotestsource, "-oracle", oracletest, "-criterionList", criterionlist, \
                           "-iter", iterations, "-et", edittype, "-ginRuns", "20"])
    #print(expCommand)
    suffix = ".bat" if platform == "win32" else ".sh"
    scriptName = classname + "Exp" + suffix

    with open("experiments" + sep + scriptName, "w+") as f:
        if (platform == "win32"):
            f.write("TITLE: " + scriptName + "\n")
        f.write("#!/bin/bash\n")
        f.write("# " + scriptName + "\n\n")
        f.write(expCommand + "\n")

print("Successfully generated experiments")








