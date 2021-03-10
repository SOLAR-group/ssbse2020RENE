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
library(xtable)

################ TREAT DATA ################ 

rawdata <- read_csv("summary.csv", col_types = cols(
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

treatedData <- rawdata %>%
  group_by(TARGET_CLASS, criterion) %>%
  summarise(inter_patch_found = sum(inter_patch_found),
            inter_non_overfitting = sum(inter_non_overfitting),
            inter_overfitting = sum(inter_patch_found) - sum(inter_non_overfitting),
            ratio = (inter_non_overfitting / inter_patch_found))

programs <- treatedData %>%
  select(TARGET_CLASS) %>%
  distinct() %>%
  pull(TARGET_CLASS)

criteria <- c("Manual", "Branch", "C-Branch", "W. Mutation", "Line", "All")

pairsToTest <- as_tibble(combn(criteria, 2))

################ FUNCTION TO TEST PAIRS WITH FISHER'S EXACT TEST ################ 

exact <- programs %>%
  map_dfr(function(subprogram){
    map_dfr(pairsToTest, function(pairToTest){
      vectorx = treatedData %>%
        filter(TARGET_CLASS == subprogram, criterion == pairToTest[1]) %>%
        pull(inter_overfitting)
      vectory = treatedData %>%
        filter(TARGET_CLASS == subprogram, criterion == pairToTest[1]) %>%
        pull(inter_non_overfitting)
      Avector = c(vectorx, vectory)

      vectorx = treatedData %>%
        filter(TARGET_CLASS == subprogram, criterion == pairToTest[2]) %>%
        pull(inter_overfitting)
      vectory = treatedData %>%
        filter(TARGET_CLASS == subprogram, criterion == pairToTest[2]) %>%
        pull(inter_non_overfitting)
      Bvector = c(vectorx, vectory)

      contigencyTable = as.table(cbind(Avector, Bvector))
      row.names(contigencyTable)=c('overfitting','non_overfitting')
      tibble(program = subprogram,
             groupA = pairToTest[1],
             groupB = pairToTest[2],
             pvalue = fisher.test(contigencyTable)$p.value)
    })
  })

significant <- exact %>%
  filter(pvalue < 0.05) %>%
  mutate_at("pvalue", format, digits = 2)

nonsignificant <- exact %>%
  filter(pvalue >= 0.05) %>%
  mutate_at("pvalue", format, digits = 2)

print("All Fisher's exact test p-values")
print(exact %>% mutate_at("pvalue", format, digits = 2), n = Inf)
print("Significant Fisher's exact test p-values")
print(significant, n = Inf)
print("Nonsignificant Fisher's exact test p-values")
print(nonsignificant, n = Inf)

################ FUNCTION TO COMPUTE STATISTICAL TIES ################ 

statisticalRanking <- function(x, criteria, subProgram){
  tempSignificant <- nonsignificant %>%
    filter(program %in% subProgram)

  firstRanks = rank(x, ties.method = "average")
  statisticalRanks = c()
  for (i in seq_along(criteria)) {
    criterion1 = criteria[i]
    rank1 = firstRanks[i]
    count = 1
    for(j in seq_along(criteria)){
      if(i!=j){
        criterion2 = criteria[j]
        rank2 = firstRanks[j]

        nrows <- tempSignificant %>%
          summarise(across(starts_with("group"), list(A = ~.x == criterion1, B = ~.x == criterion2))) %>%
          filter((groupA_A & groupB_B)|(groupA_B & groupB_A)) %>%
          nrow()
        if(nrows > 0) {
          rank1 <- rank1 + rank2
          count <- count + 1
        }
      }
    }
    statisticalRanks[i] <- rank1 / count
  }
  statisticalRanks
}

ranks <- treatedData %>%
  group_by(TARGET_CLASS) %>%
  mutate(statRank = statisticalRanking(-ratio, criterion, TARGET_CLASS))

print("Statistical Ranks")
print(ranks, n = Inf)

################ BOXPLOT FOR RANKS ################ 

rankPlot <- ranks %>%
  ggplot(aes(x = criterion, y = statRank)) +
  labs(x = "Criterion", y = "Ranks") +
  geom_boxplot() +
  theme_minimal() +
  scale_x_discrete(limits = criteria)

ggsave(rankPlot,
       filename = "plots/rank-plot.png",
       width = 11,
       height = 6,
       units = "in",
       scale = 0.5)

################ BOXPLOT FOR RATIOS ################ 

dataPlot <- treatedData %>%
  ggplot(aes(x = criterion, y = ratio)) +
  labs(x = "Criterion", y = "Non-Overfitting Ratio") +
  geom_boxplot() +
  theme_minimal() +
  scale_x_discrete(limits = criteria)

ggsave(dataPlot,
       filename = "plots/ratio-plot.png",
       width = 11,
       height = 6,
       units = "in",
       scale = 0.5)

################ AVERAGE RANKS ################ 

ranks <- ranks %>%
 group_by(criterion) %>%
 summarise(mean_rank = mean(statRank), median_rank = median(statRank)) %>%
 arrange(mean_rank)

print("Average Statistical Ranks")
print(ranks, n = Inf)

