- [Preamble](#preamble)
- [Overview](#overview)
- [Cases](#cases)
  - [Authoritative Pace and Bounds](#authoritative-pace-and-bounds)
  - [10 Node - Faithful](#node---faithful)
    - [Data Prep](#data-prep)
    - [Pace Mean and Standard
      Deviation](#pace-mean-and-standard-deviation)
    - [Equivalence Test](#equivalence-test)
    - [Execution Times](#execution-times)

# Preamble

The current document can be generated via

# Overview

# Cases

## Authoritative Pace and Bounds

``` r
auth = 8.521
low_eqbound = -1.5
high_eqbound = 1.5
```

## 10 Node - Faithful

### Data Prep

``` r
folder.10 = "../../results/faithful/"  

df_10Node_1 = "faithful.10node.30sim.1-5 - 2025.12.03 17.58.53"
producePaceData(folder.10, df_10Node_1)
df_10Node_2 = "faithful.10node.30sim.6-10 - 2025.12.03 18.03.06"
producePaceData(folder.10, df_10Node_2)
df_10Node_3 = "faithful.10node.30sim.11-15 - 2025.12.03 18.03.32"
producePaceData(folder.10, df_10Node_3)
df_10Node_4 = "faithful.10node.30sim.16-20 - 2025.12.03 18.03.56"
producePaceData(folder.10, df_10Node_4)
df_10Node_5 = "faithful.10node.30sim.21-25 - 2025.12.03 18.04.34"
producePaceData(folder.10, df_10Node_5)
df_10Node_6 = "faithful.10node.30sim.26-30 - 2025.12.03 18.12.24"
producePaceData(folder.10, df_10Node_6)
```

### Pace Mean and Standard Deviation

``` r
pacedata.10 = rbind(
  read_csv(paste0(folder.10,df_10Node_1,"/PaceData - ",df_10Node_1,".csv")),
  read_csv(paste0(folder.10,df_10Node_2,"/PaceData - ",df_10Node_2,".csv")),
  read_csv(paste0(folder.10,df_10Node_3,"/PaceData - ",df_10Node_3,".csv")),
  read_csv(paste0(folder.10,df_10Node_4,"/PaceData - ",df_10Node_4,".csv")),
  read_csv(paste0(folder.10,df_10Node_5,"/PaceData - ",df_10Node_5,".csv")),
  read_csv(paste0(folder.10,df_10Node_6,"/PaceData - ",df_10Node_6,".csv")))

pace.10 = pacedata.10 %>%
  arrange(SimID, SimDiff) %>%             # ensure ordered
  summarise(
    `Block Time (mins) - Mean....:` = mean(SimDiff)/60000,
    `Block Time (mins) - St. Dev.:`   = sd(SimDiff)/60000
  )
t(pace.10)
```

    ##                                   [,1]
    ## Block Time (mins) - Mean....: 7.677753
    ## Block Time (mins) - St. Dev.: 7.586978

### Equivalence Test

``` r
# Turn them to minutes
distances.10 = pacedata.10$SimDiff/60000


TOSTone.raw(
  m = mean(distances.10),
  sd = sd(distances.10),
  n = length(distances.10),
  mu = auth,
  low_eqbound = low_eqbound,
  high_eqbound = high_eqbound,
  alpha = 0.05)
```

![](BitcoinValidation_files/figure-gfm/unnamed-chunk-5-1.png)<!-- -->

    ## TOST results:
    ## t-value lower bound: 2.40    p-value lower bound: 0.008
    ## t-value upper bound: -8.56   p-value upper bound: 0.00000000000000003
    ## degrees of freedom : 767
    ## 
    ## Equivalence bounds (raw scores):
    ## low eqbound: -1.5 
    ## high eqbound: 1.5
    ## 
    ## TOST confidence interval:
    ## lower bound 90% CI: -1.294
    ## upper bound 90% CI:  -0.392
    ## 
    ## NHST confidence interval:
    ## lower bound 95% CI: -1.381
    ## upper bound 95% CI:  -0.306
    ## 
    ## Equivalence Test Result:
    ## The equivalence test was significant, t(767) = 2.399, p = 0.00834, given equivalence bounds of -1.500 and 1.500 (on a raw scale) and an alpha of 0.05.
    ## Null Hypothesis Test Result:
    ## The null hypothesis test was significant, t(767) = -3.080, p = 0.00214, given an alpha of 0.05.

### Execution Times

``` r
runTimes.10 <- rbind(
  getRunTime(folder.10, df_10Node_1),
  getRunTime(folder.10, df_10Node_2),
  getRunTime(folder.10, df_10Node_3),
  getRunTime(folder.10, df_10Node_4),
  getRunTime(folder.10, df_10Node_5),
  getRunTime(folder.10, df_10Node_6)
  )
runTimes.10
```

    ## # A tibble: 30 × 3
    ##    SimID sysTime sysTime_formated
    ##    <dbl>   <int> <chr>           
    ##  1     1  682244 00:11:22.244    
    ##  2     2 1197227 00:19:57.227    
    ##  3     3 1268232 00:21:08.232    
    ##  4     4  929666 00:15:29.666    
    ##  5     5 1434114 00:23:54.114    
    ##  6     6 2372053 00:39:32.053    
    ##  7     7 2108015 00:35:08.015    
    ##  8     8 2621955 00:43:41.955    
    ##  9     9  880873 00:14:40.873    
    ## 10    10  830856 00:13:50.856    
    ## # ℹ 20 more rows

``` r
runTimes.10 %>% summarise(`Run Time (mean)` = format_simtime(as.integer(mean(sysTime))), 
                       `Run Time (sd)` =  format_simtime(as.integer(sd(sysTime))))
```

    ## # A tibble: 1 × 2
    ##   `Run Time (mean)` `Run Time (sd)`
    ##   <chr>             <chr>          
    ## 1 00:24:08.365      00:10:37.082
