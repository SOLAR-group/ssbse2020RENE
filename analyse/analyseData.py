#!/usr/bin/env python

import pandas as pd
import matplotlib.pyplot as plt
import sys
import os
import matplotlib
import statistics

sep = os.path.sep
resultsdir = sep.join([sys.argv[1]])
filelist = [resultsdir + sep + f for f in os.listdir(resultsdir) if f[-4:]=='.csv']
print(len(filelist))
iterations=int(sys.argv[2])

lines = {
'SortMerge'         :   52,
'Triangle'          :   40,
'SortQuick'         :   32,
'SortBubbleDouble'  :   24,
'SortRadix'         :   24,
'SortSelection'     :   19,
'SortBubbleLoops'   :   17,
'SortSelection2'    :   17,
'SortBubble'        :   15,
'SortInsertion'     :   14
}

criterionList = ["BRANCH", "LINE", "WEAKMUTATION", "CBRANCH", "BRANCH;LINE;WEAKMUTATION;CBRANCH", "MANUAL"]
full_intermediate_df = pd.concat([pd.read_csv(f) for f in filelist])
print("All data")
print(len(full_intermediate_df))
print("Remove empty patches")
full_intermediate_df=full_intermediate_df[full_intermediate_df['patch'] != '|']
print(len(full_intermediate_df))
full_intermediate_df.loc[(full_intermediate_df['criterion']=='MANUAL') & (full_intermediate_df['TARGET_CLASS']=='locogp.Triangle'),'Size'] = 4
full_intermediate_df['Size'].fillna(7, inplace=True)
print("Check same number")
print(len(full_intermediate_df))
print("All unique data")
full_intermediate_df = full_intermediate_df.drop_duplicates()
print(len(full_intermediate_df))
print("Invalid patches")
print(len(full_intermediate_df[full_intermediate_df['validpatch'] == False]))
print("All non-overfitting")
print(len(full_intermediate_df[full_intermediate_df['success']==True]))
print("All final results")
full_df = full_intermediate_df.loc[full_intermediate_df['intermediate']==False]
print(len(full_df))
print("All patches")
print(len(full_df)*100)
print("Only intermediate")
full_intermediate_df = full_intermediate_df.loc[full_intermediate_df['intermediate']==True]
print(len(full_intermediate_df))
print("Check all unique")
print(len(full_df)+len(full_intermediate_df))

result_df = pd.DataFrame(columns=['TARGET_CLASS','criterion', 'Size', 'test_suite_sample%','BRANCH_Coverage', 'LINE_Coverage', 'WEAKMUTATION_Coverage', 'CBRANCH_Coverage' , 'patch_found', 'non_overfitting', 'inter_patch_found', 'inter_non_overfitting', 'ratio_mean', 'ratio_median'])

result_run_df = pd.DataFrame(columns=['TARGET_CLASS','criterion', 'Size', 'test_suite_sample%','BRANCH_Coverage', 'LINE_Coverage', 'WEAKMUTATION_Coverage', 'CBRANCH_Coverage' , 'patch_found', 'non_overfitting', 'inter_patch_found', 'inter_non_overfitting', 'giRun', 'inter_non_overfit_ratio'])

i = 1
j = 1
for ct in full_df['TARGET_CLASS'].unique():
  for cr in criterionList:
    group_df = full_df.loc[(full_df['TARGET_CLASS']==ct) & (full_df['criterion']==cr)]

    g100 = group_df[0:20]
    size = g100.iloc[0]['Size']
    bcov = g100.iloc[0]['BRANCH_Coverage']
    lcov = g100.iloc[0]['LINE_Coverage']
    mcov = g100.iloc[0]['WEAKMUTATION_Coverage']
    ccov = g100.iloc[0]['CBRANCH_Coverage']
    g100 = g100.loc[(g100['validpatch']==True)]
    g100_df = g100.loc[(g100['success']==True)]

    all_inter = 0
    ok_inter = 0
    ratios = []
    for idx in g100['Index']:
        inter_df = full_intermediate_df.loc[(full_intermediate_df['Index']==idx) & (full_intermediate_df['TARGET_CLASS']==ct) & (full_intermediate_df['criterion']==cr)]
        tmp1 = len(inter_df)
        tmp2 = len(inter_df.loc[inter_df['success']==True])
        all_inter += tmp1
        ok_inter += tmp2
        ratios.append((tmp2/tmp1) if tmp1 else 0)
        result_run_df.loc[j]=[ct, cr, size, 100, bcov, lcov, mcov, ccov, len(g100), len(g100_df), tmp1, tmp2, idx, (tmp2/tmp1) if tmp1 else 0]
        j += 1

    result_df.loc[i]=[ct, cr, size, 100, bcov, lcov, mcov, ccov, len(g100), len(g100_df), all_inter, ok_inter, round(statistics.mean(ratios),2), round(statistics.median(ratios),2)]
    i+=1
    if (cr!="MANUAL"):
      g75 = group_df[20:40]
      all_inter = 0
      ok_inter = 0
      ratios = []
      size = g75.iloc[0]['Size']
      bcov = g75.iloc[0]['BRANCH_Coverage']
      lcov = g75.iloc[0]['LINE_Coverage']
      mcov = g75.iloc[0]['WEAKMUTATION_Coverage']
      ccov = g75.iloc[0]['CBRANCH_Coverage']
      g75 = g75.loc[(g75['validpatch']==True)]
      g75_df = g75.loc[(g75['success']==True)]
      for idx in g75['Index']:
          inter_df = full_intermediate_df.loc[(full_intermediate_df['Index']==idx) & (full_intermediate_df['TARGET_CLASS']==ct) & (full_intermediate_df['criterion']==cr)]
          tmp1 = len(inter_df)
          tmp2 = len(inter_df.loc[inter_df['success']==True])
          all_inter += tmp1
          ok_inter += tmp2
          ratios.append((tmp2/tmp1) if tmp1 else 0)
          result_run_df.loc[j]=[ct, cr, size, 75, bcov, lcov, mcov, ccov, len(g100), len(g100_df), tmp1, tmp2, idx, (tmp2/tmp1) if tmp1 else 0]
          j += 1

      result_df.loc[i]=[ct, cr, size, 75, bcov, lcov, mcov, ccov, len(g75), len(g75_df), all_inter, ok_inter, round(statistics.mean(ratios),2), round(statistics.median(ratios),2)]
      i+=1

      g50 = group_df[40:60]
      all_inter = 0
      ok_inter = 0
      ratios = []
      size = g50.iloc[0]['Size']
      bcov = g50.iloc[0]['BRANCH_Coverage']
      lcov = g50.iloc[0]['LINE_Coverage']
      mcov = g50.iloc[0]['WEAKMUTATION_Coverage']
      ccov = g50.iloc[0]['CBRANCH_Coverage']
      g50 = g50.loc[(g50['validpatch']==True)]
      g50_df = g50.loc[(g50['success']==True)]
      for idx in g50['Index']:
          inter_df = full_intermediate_df.loc[(full_intermediate_df['Index']==idx) & (full_intermediate_df['TARGET_CLASS']==ct) & (full_intermediate_df['criterion']==cr)]
          tmp1 = len(inter_df)
          tmp2 = len(inter_df.loc[inter_df['success']==True])
          all_inter += tmp1
          ok_inter += tmp2
          ratios.append((tmp2/tmp1) if tmp1 else 0)
          result_run_df.loc[j]=[ct, cr, size, 50, bcov, lcov, mcov, ccov, len(g100), len(g100_df), tmp1, tmp2, idx, (tmp2/tmp1) if tmp1 else 0]
          j += 1

      result_df.loc[i]=[ct, cr, size, 50, bcov, lcov, mcov, ccov, len(g50), len(g50_df), all_inter, ok_inter, round(statistics.mean(ratios),2), round(statistics.median(ratios),2)]
      i+=1

      g25 = group_df[60:80]
      all_inter = 0
      ok_inter = 0
      ratios = []
      size = g25.iloc[0]['Size']
      bcov = g25.iloc[0]['BRANCH_Coverage']
      lcov = g25.iloc[0]['LINE_Coverage']
      mcov = g25.iloc[0]['WEAKMUTATION_Coverage']
      ccov = g25.iloc[0]['CBRANCH_Coverage']
      g25 = g25.loc[(g25['validpatch']==True)]
      g25_df = g25.loc[(g25['success']==True)]
      for idx in g25['Index']:
          inter_df = full_intermediate_df.loc[(full_intermediate_df['Index']==idx) & (full_intermediate_df['TARGET_CLASS']==ct) & (full_intermediate_df['criterion']==cr)]
          tmp1 = len(inter_df)
          tmp2 = len(inter_df.loc[inter_df['success']==True])
          all_inter += tmp1
          ok_inter += tmp2
          ratios.append((tmp2/tmp1) if tmp1 else 0)
          result_run_df.loc[j]=[ct, cr, size, 25, bcov, lcov, mcov, ccov, len(g100), len(g100_df), tmp1, tmp2, idx, (tmp2/tmp1) if tmp1 else 0]
          j += 1

      result_df.loc[i]=[ct, cr, size, 25, bcov, lcov, mcov, ccov, len(g25), len(g25_df), all_inter, ok_inter, round(statistics.mean(ratios),2), round(statistics.median(ratios),2)]
      i+=1

result_df['non_overfit_ratio']=result_df['non_overfitting']/result_df['patch_found']
result_df['inter_non_overfit_ratio']=result_df['inter_non_overfitting']/result_df['inter_patch_found']
print("Patches found")
print(sum(result_df['patch_found']))
print("Non-overfitting found")
print(sum(result_df['non_overfitting']))
print("Intermediate Patches found")
print(sum(result_df['inter_patch_found']))
print("Intermediate Non-overfitting found")
print(sum(result_df['inter_non_overfitting']))
result_df.to_csv('summary.csv')

# RQ1
result_df = pd.read_csv('out.csv')
rq1_df = pd.DataFrame(columns=['Program','LoC', 'Test Suite Size', 'Test Suites', 'Patch Found','Non-Overfitting'])
rq1_inter_df = pd.DataFrame(columns=['Program', 'Intermediate Patches Found','Intermediate Non-Overfitting', 'Non-Overfitting Ratio'])
i=0
for ct in full_df['TARGET_CLASS'].unique():
  #print('\n',ct)
  #print('Test Suite Size', end=' ')
  ts = round(max(full_intermediate_df.loc[full_intermediate_df['TARGET_CLASS']==ct]['Size']))
  #print(ts)
  rq1 = result_df.loc[result_df['TARGET_CLASS']==ct]
  #print('Found (at least once in 20 runs)', end=' ')
  pf = 21-len(rq1.loc[rq1['patch_found']==0])
  #print(pf)
  #print('Non-overfitting (at least once in 20 runs)', end=' ')
  no = 21-len(rq1.loc[rq1['non_overfitting']==0])
  #print(no)
  i += 1
  rq1_df.loc[i]=[ct[7:], lines[ct[7:]], '1--'+str(ts), 21, pf, no]
  #print('Intermediate patches found', end=' ')
  s1 = sum(rq1['inter_patch_found'])
  #print(s1)
  #print('Non-overfitting Intermediate patches found', end=' ')
  s2 = sum(rq1['inter_non_overfitting'])
  #print(s2)
  #print('Non-overfit ratio for intermediate')
  #print(s2/s1)
  rq1_inter_df.loc[i]=[ct[7:], s1, s2, round(s2/s1,2)]
rq1_df.sort_values(by='Program',inplace=True)
rq1_inter_df.sort_values(by='Program',inplace=True)
print(rq1_df.to_latex(index=False))
print(rq1_inter_df.to_latex(index=False))

# RQ2
rq2_inter_df = pd.DataFrame(columns=['Criterion', 'Test Suite Sample%', 'Intermediate Patches Found','Intermediate Non-Overfitting', 'Non-Overfitting Ratio'])
i=0
for ct in full_df['criterion'].unique():
  for sp in [100,75,50,25]:
    if ct=='MANUAL' and sp<100:
      continue
    else:
      rq2 = result_df.loc[(result_df['criterion']==ct) & (result_df['test_suite_sample%']==sp)]
      s1 = sum(rq2['inter_patch_found'])
      s2 = sum(rq2['inter_non_overfitting'])
      i += 1
      rq2_inter_df.loc[i]=[ct, sp, s1, s2, round(s2/s1,2)]
print(rq2_inter_df.to_latex(index=False))

