## A study on the impact of automatically generated tests on the genetic improvement process using Gin

This repository is a fork of the original version of Gin: https://github.com/gintool/gin

It contains an additional class for conducting a large number of experiments on Non-functional genetic improvement on a program, using automated tests as input to the GI Process, as well as some additional utilities to facilitate the experiment.

This is a replication package for the paper accepted to the SSBSE 2020 Replications and Negative results track, entitled ["Impact of Test Suite Coverage on Overfitting in Genetic Improvement of Software"](https://link.springer.com/chapter/10.1007%2F978-3-030-59762-7_14) by [Mingyi Lim](https://github.com/mingyi850), Giovani Guizzo and Justyna Petke.

## Running the experiments
To run the experiments included in this repository and obtain datafiles with the results, follow the steps below

1. Build Gin with gradle (see https://github.com/gintool/gin for details).

2. Create class file for the sort programs (simply run mvn test in the examples/locoGP directory).

2. Run the python3 script ExperimentScriptGenerator.py (make sure the mavenhome patch is correctly set first). This will create an /experiments folder and generate scripts based on your machine (Linux, MacOs or Windows).

3. Go to the /experiments folder and run the script files. Each file will start a single experiment. 

4. Due to the high computational expense required for Genetic improvement algorithms, each experiment can take anywhere from 1 - 8hrs to run. 

5. Running these experiments will create a .csv file in the /experiments/experiment-results folder. These experiments will be named after the date and time they were completed.

On a machine with MacOS the following should work out-of-the-box:

```
git clone https://github.com/ssbseRENEsubmission/ssbseRENEsubmission.git
cd ssbseRENEsubmission
./gradlew build
cd examples/locoGP
mvn test 
cd ../../
python3 ExperimentScriptGenerator.py 
cd experiments
chmod +x SortBubleExp.sh
./SortBubbleExp.sh
```

## Analysis of experimental data

Raw output of the experiemnts is provided in the newData folder.

The analyse/summary.csv file holds a data summary, from which all tables and figures were derived for the submission. The analyse folder holds the scripts that produced the summary.csv file (to regenerate, run python3 analyseData.py ../newData 1 from within the analyse directory) and R scripts for figures and tables that use the summary.csv file as input.
