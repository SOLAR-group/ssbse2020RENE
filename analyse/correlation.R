library(tidyverse)
library(GGally)
library(MLmetrics)
library(data.table)
library(effsize)
library(pgirmess)
library(hms)
library(lubridate)
library(ggrepel)
library(tibble)
library(stringr)
library(reshape2)
library(ggcorrplot)
library("Hmisc")
library(xtable)

################ TREAT DATA ################ 

rawdata <- read_csv("summary.csv",
                    col_types = cols(
                      Index = col_double(),
                      TARGET_CLASS = col_character(),
                      criterion = col_character(),
                      Size = col_double(),
                      `test_suite_sample%` = col_double(),
                      BRANCH_Coverage = col_double(),
                      LINE_Coverage = col_double(),
                      WEAKMUTATION_Coverage = col_double(),
                      CBRANCH_Coverage = col_double(),
                      patch_found = col_double(),
                      non_overfitting = col_double(),
                      inter_patch_found = col_double(),
                      inter_non_overfitting = col_double(),
                      non_overfit_ratio = col_double(),
                      inter_non_overfit_ratio = col_double()
                    )) %>%
                      mutate(criterion = replace(criterion, criterion == "BRANCH", "Branch")) %>%
                      mutate(criterion = replace(criterion, criterion == "LINE", "Line")) %>%
                      mutate(criterion = replace(criterion, criterion == "MANUAL", "Manual")) %>%
                      mutate(criterion = replace(criterion, criterion == "WEAKMUTATION", "W. Mutation")) %>%
                      mutate(criterion = replace(criterion, criterion == "CBRANCH", "C-Branch")) %>%
                      mutate(criterion = replace(criterion, criterion == "BRANCH;LINE;WEAKMUTATION;CBRANCH", "All"))

colnames(rawdata) <- c("Index",                   
                       "TARGET_CLASS",            
                       "criterion",               
                       "Size",                    
                       "sample_size",             
                       "Branch Cov.",        
                       "Line Cov.",           
                       "Weak Mut. Cov.",   
                       "C-Branch Cov.",        
                       "patch_found",             
                       "non_overfitting",
                       "inter_patch_found",
                       "inter_non_overfitting",
                       "non_overfit_ratio",
                       "inter_non_overfit_ratio")

rawdata <- rawdata %>%
  select(TARGET_CLASS, 
         criterion, 
         "sample_size",
         "Size",
         "Branch Cov.",        
         "Line Cov.",           
         "Weak Mut. Cov.",   
         "C-Branch Cov.",
         "inter_patch_found",
         "inter_non_overfitting") %>%
  filter(criterion != "Manual") %>%
  mutate(ratio = (inter_non_overfitting / inter_patch_found))


################ SPEARMAN RHO ################

printSpearman <- function(x, y) {
  z <- x %>%
    select(Size, ends_with("Cov."))
  
  w <- x %>%
    select(ratio)
  
  cormat <- cor(x = as.matrix(w), y = as.matrix(z), method = "spearman")
  
  print(y)
  print(xtable(cormat))
}

print("Print Spearman's rho of whole data")
rawdata %>%
  group_walk(~ printSpearman(.x, "ALL"))

print("Print Spearman's rho grouped by program")
rawdata %>%
  group_by(TARGET_CLASS) %>%
  group_walk(~ printSpearman(.x, .y$TARGET_CLASS))

print("Print Spearman's rho grouped by sample size")
rawdata %>%
  select(-c(TARGET_CLASS)) %>%
  group_by(sample_size) %>%
  group_walk(~ printSpearman(.x, as.character(.y$sample_size)))

print("Print Spearman's rho grouped by criterion")
rawdata %>%
  select(-c(TARGET_CLASS)) %>%
  group_by(criterion) %>%
  group_walk(~ printSpearman(.x, as.character(.y$criterion)))


################ SPEARMAN P-VALUE ################

printCorrelationP <- function(x, y) {
  z <- x %>%
    select(Size, ends_with("Cov."))

  w <- x %>%
    select(ratio)

  cormat <- rcorr(x = as.matrix(w), y = as.matrix(z), type = "spearman")

  print(y)
  print(cormat$P)
}

print("Print Spearman's p-value of whole data")
rawdata %>%
  group_walk(~ printCorrelationP(.x, "ALL"))

print("Print Spearman's p-value grouped by subject program")
rawdata %>%
  group_by(TARGET_CLASS) %>%
  group_walk(~ printCorrelationP(.x, .y$TARGET_CLASS))

print("Print Spearman's p-value grouped by sample size")
rawdata %>%
  select(-c(TARGET_CLASS)) %>%
  group_by(sample_size) %>%
  group_walk(~ printCorrelationP(.x, as.character(.y$sample_size)))

print("Print Spearman's p-value grouped by criterion")
rawdata %>%
  select(-c(TARGET_CLASS)) %>%
  group_by(criterion) %>%
  group_walk(~ printCorrelationP(.x, as.character(.y$criterion)))