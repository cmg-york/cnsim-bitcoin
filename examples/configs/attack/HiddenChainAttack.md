# Hidden Chain Attack Analysis
Anonymous

- [Overview](#overview)
- [Theoretical Benchmarks](#theoretical-benchmarks)
- [Experimental Set-up](#experimental-set-up)
- [Analysis](#analysis)
  - [Configuration](#configuration)
  - [Data Read and Preparation](#data-read-and-preparation)
  - [Experimental Runs](#experimental-runs)
  - [Output](#output)
  - [Latex Output](#latex-output)

# Overview

The following is an analysis of hidden-chain attack simulations for
various fractions of power $q$ enjoyed by the attacker vs. that enjoyed
by the remaining of the network $p = 1-q$. In the attack implementation
in CNSim, the attacking node, upon receiving a block containing the
target transaction, boosts its power to match fraction $q$, and
initiates the construction of the hidden chain, while behaving honestly
in all other ways. Once $z-1$ additional blocks are received from the
network, the attacker starts checking if its hidden chain is strictly
longer than the honest one and, if yes, releases the chain through
scheduling the corresponding sequence of block propagation events. For
simplicity and compatibility with the theoretical analysis below, the
attacker does not engage in early transaction censorship at the pool
synchronization level.

# Theoretical Benchmarks

An analysis of the probability of succeeding has been offered by
[Nakamoto themselves](https://bitcoin.org/bitcoin.pdf) and [corroborated
independently](https://arxiv.org/abs/1402.2009). First consider that
after $z$ confirmations, the expected number of blocks the attacker has
mined in that time follows a Poisson distribution with parameter
$\lambda = z \, \frac{q}{p}$. The probability of a successful attack
consists of the probability $p_{pre}$ that the attacker has already
mined more than $k > z + 1$ blocks during the confirmation period, plus
the probability $p_{post}$ that the attacker has actually mined
$k \leq z + 1$ blocks only at the $z$ confirmation and must close the
remaining deficit of $z - k + 2$, via a random walk with a step
probability ratio of $q/p$. The corresponding formulae are:

$p_{\mathrm{pre}} = 1 - \sum_{k=0}^{z+1} \frac{\lambda^k e^{-\lambda}}{k!} \hspace{10mm} p_{\mathrm{post}} = \sum_{k=0}^{z+1} \frac{\lambda^k e^{-\lambda}}{k!} \left(\frac{q}{p}\right)^{z-k+2}$

… which results to:

$p_{\mathrm{total}} = 1 - \sum_{k=0}^{z+1} \frac{\lambda^k e^{-\lambda}}{k!} \left(1 - \left(\frac{q}{p}\right)^{z-k+2}\right)$

The above formula is different than the one offered by Nakamoto to match
our simulation of the attack in which (a) the attack starts when a block
containing the target transaction has already been confirmed, hence the
attacker starts at a disadvantage of 1, (b) \`\`catching-up’’ with the
longest chain is not enough, as the attacker’s chain must be longer by
at least one for success.

# Experimental Set-up

A network of $30$ nodes is constructed with a mean honest hashpower
$2.86e+19$ H/S, connected with each other with extremely high speed
connections of $100Gb/s$ so as to prevent block propagation to be a
confound. Difficulty is set to $4.4 \cdot 10^{23}$ and transaction sizes
to mimicking the target block pace conditions of our earlier validation
(10 minutes). To allow for scalability we assume a transaction arrival
rate of $\lambda = 0.017$ which is interpreted into about 10
transactions per 10 minutes, hence expecting 10 transactions per block.
Average transaction size is set to $5Kb$, and block size to $5MB$ to
also preclude block saturation phenomena. We construct random workloads
of 10,000 transactions and set the transaction with ID $70$ to be the
target. We run a total of 1,000 simulations under different combinations
of $q$ (fraction of malicious power) and $z$ number of confirmations
before considering release of the hidden chain. As per our methodology,
each experiment was treated as 1,000 Bernoulli trials whereby the
attacker wins if the target transaction is not final within the entire
horizon of the experiment, and looses otherwise. For each experiment an
equivalence test was conducted to evaluate whether the observed
proportion of successes differed from the theoretical value as per above
formulae by more than a $\delta = 0.05$.

The CNSim configuration files can be found in:
https://github.com/cmg-york/cnsim-bitcoin/tree/main/examples/configs/attack

The files are of the form
`confirmation.attack.0.X.Y.FROM.TO.properties`, where `X` is
$q\cdot 10$, `Y` is $z$, and `FROM` and `TO` mark a range of 200
simulation IDs.

# Analysis

## Configuration

Variable `logAnalysisToolPath` below needs to point to a local clone of
repository
https://github.com/cmg-york/cnsim-tools/R-Tools/src/logAnalysis/ from
where `library.R` can be loaded for performing the analysis.

``` r
logAnalysisToolPath = "../../../../cnsim-tools/R-Tools/src/logAnalysis/"

#Library loading
source(paste0(logAnalysisToolPath,"library.R"))
```

Variable `outputFolder` below needs to point to a local clone of
repository
https://github.com/cmg-york/cnsim-bitcoin/tree/main/examples/results/attack
that contains subfolders of the form `confirmation.attack.1.X,Y - ALL`.
Remaining parameters stay as-is.

``` r
outputFolder = "../../results/attack/"

# Other Parameters
txVector = c(50,60,70,80,90)
targetTransaction = 70
```

## Data Read and Preparation

The function below collects and fuses experimental output and performs
statistical analysis for each experimental configuration (values of $q$
and $z$), according to $\delta$. Finality threshold is hardcoded to
$0.9$ (i.e. a transaction is believed if 90% of the nodes believe it).

The outcome is a list appended to outer list `df`. Please refer to
`library.R` for documentation of individual functions.

``` r
run_config <- function(q,z,delta,df) {
  
  # Theoretical Values
  P_satoshi = nakamoto_catchup_confirmations(q,z)
  
  # File naming for experimental output
  run = "ALL"
  code = paste0("0.",q*100,".",z)
  experiment = paste0("confirmation.attack.",code," - ", run)
  
  # Data Read
  df[[code]] <- setVars(outputFolder,experiment, txVector)
  
  ### Data Load and Transformation
  #df[[code]]$graphData = prepareGraphData(df[[code]]$beliefData,
  #                                        alignTimes = TRUE,
  #                                        VaR = TRUE,
  #                                        arrivalTimes = getTxArrivalTimes(df[[code]]$inputData,txVector))
  
  df[[code]]$finalityData = getFinality(df[[code]]$beliefData,
                                        alignTimes = TRUE, 
                                        threshold = 0.9,
                                        arrivalTimes = getTxArrivalTimes(df[[code]]$inputData,txVector), 
                                        horizon = "500:00:00.000") %>% 
    mutate(theoretical = (1- P_satoshi)) %>%
    select(Transaction, successes, trials, p_val = threstest_p_val, 
           point_estimate, ci_low, theoretical, ci_high)
  
  outcome = with(df[[code]]$finalityData %>% filter(Transaction == targetTransaction),
                 equivalence_vec(trials, successes, theoretical, delta = delta)) %>%
    mutate(q = q, z = z)
  
  df[[code]]$outcome <- outcome
  
  df[[code]]$shortoutcome <- cat("Config: ", code ,"\nT: ",round(1-outcome$theoretical,3), " [", round(1-outcome$upper_bound,3), ",", round(1-outcome$lower_bound,3), "]",
      "\nE: ",round(1-outcome$observed,3), " [",1-round(outcome$ci_high,3), ",", 1 - round(outcome$ci_low,3), "]",
      "\n",outcome$result, " (delta=", outcome$delta, ")\n\n", sep = ""
  )
  
  return (df)
}
```

## Experimental Runs

``` r
# Initialize results list
df<-list()

# Configurations
q_vals <- c(0.1, 0.1, 0.2, 0.2, 0.2, 0.2, 0.3, 0.3, 0.3, 0.3, 0.3, 0.3, 0.4, 0.4, 0.4, 0.4, 0.4, 0.4)
z_vals <- c(  1,   2,   1,   2,   3,   4,   1,   2,   3,   4,   5,   6,   1,   2,   3,   4,   5,   6)

# Analyze each configuration
for (i in seq_along(q_vals)) {
  df <- run_config(q_vals[i], z_vals[i], 0.05, df)
}
```

    Config: 0.10.1
    T: 0.003 [0,0.053]
    E: 0.01 [0.006,0.017]
    PASS (delta=0.05)

    Config: 0.10.2
    T: 0.001 [0,0.051]
    E: 0.003 [0.001,0.007]
    PASS (delta=0.05)

    Config: 0.20.1
    T: 0.033 [0,0.083]
    E: 0.055 [0.044,0.068]
    PASS (delta=0.05)

    Config: 0.20.2
    T: 0.017 [0,0.067]
    E: 0.04 [0.031,0.051]
    PASS (delta=0.05)

    Config: 0.20.3
    T: 0.009 [0,0.059]
    E: 0.022 [0.016,0.031]
    PASS (delta=0.05)

    Config: 0.20.4
    T: 0.004 [0,0.054]
    E: 0.015 [0.01,0.023]
    PASS (delta=0.05)

    Config: 0.30.1
    T: 0.138 [0.088,0.188]
    E: 0.16 [0.142,0.18]
    PASS (delta=0.05)

    Config: 0.30.2
    T: 0.102 [0.052,0.152]
    E: 0.129 [0.113,0.147]
    PASS (delta=0.05)

    Config: 0.30.3
    T: 0.076 [0.026,0.126]
    E: 0.101 [0.086,0.118]
    PASS (delta=0.05)

    Config: 0.30.4
    T: 0.056 [0.006,0.106]
    E: 0.083 [0.07,0.098]
    PASS (delta=0.05)

    Config: 0.30.5
    T: 0.042 [0,0.092]
    E: 0.06 [0.049,0.074]
    PASS (delta=0.05)

    Config: 0.30.6
    T: 0.031 [0,0.081]
    E: 0.049 [0.039,0.061]
    PASS (delta=0.05)

    Config: 0.40.1
    T: 0.411 [0.361,0.461]
    E: 0.395 [0.37,0.421]
    PASS (delta=0.05)

    Config: 0.40.2
    T: 0.376 [0.326,0.426]
    E: 0.362 [0.337,0.387]
    PASS (delta=0.05)

    Config: 0.40.3
    T: 0.344 [0.294,0.394]
    E: 0.334 [0.31,0.359]
    PASS (delta=0.05)

    Config: 0.40.4
    T: 0.316 [0.266,0.366]
    E: 0.308 [0.285,0.333]
    PASS (delta=0.05)

    Config: 0.40.5
    T: 0.289 [0.239,0.339]
    E: 0.286 [0.263,0.31]
    PASS (delta=0.05)

    Config: 0.40.6
    T: 0.266 [0.216,0.316]
    E: 0.263 [0.241,0.287]
    PASS (delta=0.05)

``` r
# Output precision
prec = 3

result <- do.call(rbind, lapply(names(df), \(code) df[[code]]$outcome)) %>% 
  mutate(observed = 1- observed, 
         low_bound = 1 - upper_bound,
         ci_l = 1 - ci_high,
         ci_h = 1 - ci_low,
         up_bound = 1 - lower_bound,
         theoretical = 1-theoretical) %>% 
  mutate(obs = paste0(round(observed,prec)," [",round(ci_l,prec),",",round(ci_h,prec),"]"),
         theo = paste0(round(theoretical,prec)," [",round(low_bound,prec),",",round(up_bound,prec),"]"),
         theo2 = paste0(round(theoretical,prec)," $\\pm \\delta$"),
         theo3 = paste0(round(theoretical,prec)),
         result = paste0(theo3," | ", obs)
         ) %>%
  select(result,q,z) %>%
  pivot_wider(names_from = z, names_prefix = "z=", values_from = result)
```

## Output

``` r
knitr::kable(result, format = "pipe")
```

| q | z=1 | z=2 | z=3 | z=4 | z=5 | z=6 |
|---:|:---|:---|:---|:---|:---|:---|
| 0.1 | 0.003 \| 0.01 \[0.006,0.017\] | 0.001 \| 0.003 \[0.001,0.007\] | NA | NA | NA | NA |
| 0.2 | 0.033 \| 0.055 \[0.044,0.068\] | 0.017 \| 0.04 \[0.031,0.051\] | 0.009 \| 0.022 \[0.016,0.031\] | 0.004 \| 0.015 \[0.01,0.023\] | NA | NA |
| 0.3 | 0.138 \| 0.16 \[0.142,0.18\] | 0.102 \| 0.129 \[0.113,0.147\] | 0.076 \| 0.101 \[0.086,0.118\] | 0.056 \| 0.083 \[0.07,0.098\] | 0.042 \| 0.06 \[0.049,0.074\] | 0.031 \| 0.049 \[0.039,0.061\] |
| 0.4 | 0.411 \| 0.395 \[0.37,0.421\] | 0.376 \| 0.362 \[0.337,0.387\] | 0.344 \| 0.334 \[0.31,0.359\] | 0.316 \| 0.308 \[0.285,0.333\] | 0.289 \| 0.286 \[0.263,0.31\] | 0.266 \| 0.263 \[0.241,0.287\] |

In all cases it is observed that the entire confidence interval was
contained within the equivalence bounds of the theoretical $\pm \delta$
with statistical significance threshold $\alpha = 0.05$.

## Latex Output

``` r
print(xtable(result, caption = "[Complete the caption in the paper]"), 
      include.rownames = FALSE,
      booktabs = TRUE)
```

    % latex table generated in R 4.4.1 by xtable 1.8-4 package
    % Mon Mar 23 13:00:10 2026
    \begin{table}[ht]
    \centering
    \begin{tabular}{rllllll}
      \toprule
    q & z=1 & z=2 & z=3 & z=4 & z=5 & z=6 \\ 
      \midrule
    0.10 & 0.003 $|$ 0.01 [0.006,0.017] & 0.001 $|$ 0.003 [0.001,0.007] &  &  &  &  \\ 
      0.20 & 0.033 $|$ 0.055 [0.044,0.068] & 0.017 $|$ 0.04 [0.031,0.051] & 0.009 $|$ 0.022 [0.016,0.031] & 0.004 $|$ 0.015 [0.01,0.023] &  &  \\ 
      0.30 & 0.138 $|$ 0.16 [0.142,0.18] & 0.102 $|$ 0.129 [0.113,0.147] & 0.076 $|$ 0.101 [0.086,0.118] & 0.056 $|$ 0.083 [0.07,0.098] & 0.042 $|$ 0.06 [0.049,0.074] & 0.031 $|$ 0.049 [0.039,0.061] \\ 
      0.40 & 0.411 $|$ 0.395 [0.37,0.421] & 0.376 $|$ 0.362 [0.337,0.387] & 0.344 $|$ 0.334 [0.31,0.359] & 0.316 $|$ 0.308 [0.285,0.333] & 0.289 $|$ 0.286 [0.263,0.31] & 0.266 $|$ 0.263 [0.241,0.287] \\ 
       \bottomrule
    \end{tabular}
    \caption{[Complete the caption in the paper]} 
    \end{table}
