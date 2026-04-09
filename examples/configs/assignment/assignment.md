# Exploring Nakamoto consensus configurations using cnsim-tools
Conceptual Modeling Group @ York University

- [Overview](#overview)
- [What to do](#what-to-do)
  - [1. Configuration Exploration and Analysis (entire team, work
    separatelly)](#1-configuration-exploration-and-analysis-entire-team-work-separatelly)
    - [Number of nodes and experimental
      repetitions](#number-of-nodes-and-experimental-repetitions)
    - [Workload characteristics](#workload-characteristics)
    - [Network characteristics](#network-characteristics)
    - [Node characteristics](#node-characteristics)
    - [Block vs. transaction size](#block-vs-transaction-size)
  - [2. Run and analyse the output (entire team, work
    separatelly)](#2-run-and-analyse-the-output-entire-team-work-separatelly)
    - [Run](#run)
    - [Explore cnsim-tools](#explore-cnsim-tools)
  - [3. Exploration Activities (individual
    assignments)](#3-exploration-activities-individual-assignments)
    - [Activity 1: (Edison, Shafaat, Nina working separatelly without
      LLMs)](#activity-1-edison-shafaat-nina-working-separatelly-without-llms)
    - [Activity 2: (Anthony, Ghazal, Kasra working separatelly with as
      little LLMs as
      possible)](#activity-2-anthony-ghazal-kasra-working-separatelly-with-as-little-llms-as-possible)
    - [Comprehend and Critique inflow/outflow
      Caluclations](#comprehend-and-critique-inflowoutflow-caluclations)
    - [Create an Shiny app](#create-an-shiny-app)

# Overview

In this assignment you will focus on performing experiments with CNSim
and analysing results using cnsim-tools R scripts.

The specific goal is to understand the role of **maximum block size** to
the time it takes for transactions to “finalize”. Specifically if the
block size is limited compared to the rate of incoming transactions,
then nodes become selective as to which transactions to admit to blocks,
making certain transactions (ones with lower fees for the node) having
to wait.

Think of it as a bus station. If arrival rate of travelers exceeds the
rate at which they can be served by buses (given their limited capacity
and departure intervals) many passangers (e.g. latecomers) are bound to
wait longer. Now in a civilized bus station you would expect a
first-come-first-serve priority queue. But imagine if passengers could
jump the line by bribing. Poor/honest passengers would stay in the
station forever. In bitcoin there is no queue at all: if a transaction
fee is attractive to the nodes, the nodes are more likely to include the
transaction in the block (why?). Besides it is hard to implement fair
first-come-first-serve in a distributed system (think about it).

# What to do

To solve this assignment perform the following steps:

## 1. Configuration Exploration and Analysis (entire team, work separatelly)

Go to `examples/configs/assignment/blocksize.properties`, to explore the
configuration. Then answer the following questions but filling in the
markdown.

### Number of nodes and experimental repetitions

\[complete the description\]

### Workload characteristics

Answer here the following: - Where are input transactions coming from?
Are they created by the simulator or read from a file? - What is their
average size? Their average value (fee)? - What is their arrival rate?
How many transactions per second? per minute? - Do transactions have
conflicts? Dependencies? - Transaction values (fees) follow a normal
distribution – what are the mean and standard deviation? - Given the
above mean and standard deviation what are the 10th,25th,50th,75th,90th
quantiles?

``` r
mean_val = 3619.23
sd_val = 3619.23/10

qnorm(c(0.1,0.25,0.5,0.75,0.95), mean = mean_val, sd = sd_val)
```

### Network characteristics

- What is the average end-to-end bandwidth between pairs of nodes?
- What is the block size? How long would it take for a block to
  propagate to the network once validated? NOTE that broadcasting a
  block means sending it to all $N-1$ other nodes *sequentially*, one
  after the other. I cannot use the same channel for $N-1$ different
  transmissions *at the same time*.

### Node characteristics

Remember that PoW networks are characterized by a *difficulty* parameter
defined *in CNSim* as:

$\mathrm{difficulty} = \mathrm{(size of search space)}/\mathrm{(size of success space)}$

where size of search spaces is the number of all possible hashes and
size of success space is the number of all hashes that have the
appropriate leading zeros. So the greater the number the smaller the
needle in the haystack. Note that the real-world bitcoin notion of
difficulty is somewhat different.

Likewise, the probability of success at each trial is
$p = 1/\mathrm{difficulty}$.

How many trials do I need in order to find a good hash? This number is a
random variable (in each competition it is a different number) following
the geometric distribution with expected value $1/p = d$ and s.d.
$\sqrt{1-p}/p$, i.e., .

**Questions:** - What is the difficulty in the configuration in
question? - What is the average hashpower of the nodes? What is, hence,
the total mean hashpower in the network given the number of nodes? Also:
what is the unit of measurement of hashpower in cnsim? Should be in the
comments of cnsim-engine as well as a file called units.md. - So you
have the total hashpower and the difficulty. How often do you think
there will be a new block validation event?

Also: - Compare the above time with the block broadcasting time. Is the
latter close to the former? If yes, increase the network speed so that
broadcasting can happen in a fraction of the validation time. This way
synchronization delays due to slow propagation do not interfere with
consensus in your experiments.

### Block vs. transaction size

- What is again the block size? What is the average transaction size?
  Hence how many transactions per block on average? (i.e., how many
  passengers fit in a bus?)
- Consider the block validation interval you calculated above (i.e., how
  often buses depart).
- What is the maximum transaction arrival rate (i.e. number of
  passengers arriving per second), such that there are no transactions
  left stranded in the transaction pool (i.e., passengers stranded in
  the station)?
- Look at the current transaction rate in the `.properties` file. Is it
  above or below the above benchmark?

## 2. Run and analyse the output (entire team, work separatelly)

### Run

Using `assignmentRun.sh` at the root, run the configuration and produce
output. Verify the latter is there.

### Explore cnsim-tools

#### Load the library

In `https://github.com/cmg-york/cnsim-tools/` there are a bunch of R
scripts for analyzing CNSim logs. Of specific interest are the R script
in `src/logAnalysis/library.R`.

Clone the repo to be a sibling of (same directory as) your cnsim-bitcoin
repo. If all is done well the following code will work.

``` r
# Helper Function to convert minutes -> hh:mm:ss
minutes_to_hms <- function(mins) {
  h <- floor(mins / 60)
  m <- floor(mins %% 60)
  s <- round((mins - floor(mins)) * 60)
  sprintf("%02d:%02d:%02d", h, m, s)
}


# Main Parameters
logAnalysisToolPath = "../../../../cnsim-tools/R-Tools/src/logAnalysis/"
repo = "cmg-york"
toolsRepo = paste0("https://github.com/", repo,"/cnsim-tools")
bitcoinRepo = paste0("https://github.com/", repo,"/cnsim-bitcoin")

toBitRepo <- function(x) {
  return(paste0(bitcoinRepo,x))
}

#Libraries
source(paste0(logAnalysisToolPath,"library.R"))
```

#### Load the output

Make sure below that run points to the experimental run you want to
analyze.

``` r
  # File naming for experimental output
  run = "2026.04.08 19.09.49"
  experiment = paste0("blocksize - ", run)

  
  #Output folder
  outputFolder = "../../results/"

  # Other Parameters
  txVector = c(10,20,30,40,50)

  # Data Read
  df <- setVars(outputFolder,experiment,txVector)
```

#### Perform Pace Analysis

Below is an analysis of block pace based on the block validation data
produced.

``` r
producePaceData(outputFolder, experiment)

pacedata = read_csv(paste0(outputFolder,experiment,"/PaceData - ",experiment,".csv"))

pace = pacedata %>%
  arrange(SimID, SimDiff) %>%             # ensure ordered
  summarise(
    `Block Time (mins) - Mean....:` = mean(SimDiff)/60000,
    `Block Time (mins) - St. Dev.:`   = sd(SimDiff)/60000
  )
t(pace)
```

Question: does the above result agree with your theoretical?

Task: just skim trough the producePaceData function in library.R just to
get an idea of what it does.

#### Produce and Comment on Finality Data

``` r
df$finalityData = getFinality(df$beliefData,
                                alignTimes = TRUE, 
                                threshold = 0.9,
                                arrivalTimes = getTxArrivalTimes(df$inputData,txVector), 
                                horizon = "3:20:00.000")

finalityResult = df$finalityData %>% select(Transaction,trials,successes,starts_with("threstest"),point_estimate,ci_low,ci_high)

kable(finalityResult)
```

The above tell you (for each sample transaction) in how many of the
total simulation runs the transaction was final at the horizon (4 hours
in). It also runs two statistical tests. - The probability that each
sample transaction (each row) will be believed above a specific
threshold (`0.9` in our case) - `threstest_X` - A point estimate and
confidence interval for the actual belief value.

What is *belief*? It is the proportion of nodes that “believe” the
transaction, i.e. the transaction exists in their longest chain.

#### Produce the Belief Graph

We can produce the graph that shows how belief in a transaction evolve
over time. Run the following:

``` r
sizeFactor.graph.data = prepareGraphData(df$beliefData,
                                alignTimes = TRUE,
                                VaR = FALSE,
                                arrivalTimes = getTxArrivalTimes(df$inputData,txVector))

getBeliefGraph(sizeFactor.graph.data, xlim = c("0:00","1:30:00"),threshold = 0.9)
```

Tighten the far end of `xlim` to “zoom in”. About how long does it take
for the transactions to be believed?

#### Identify time to finality

Explore then at what point each sample transaction became final using
the following.

``` r
getTimeToFinality(sizeFactor.graph.data,0.9)
```

Keep in mind that the graph takes into account when the transaction
arrives at the system. So even if they all end up in the same block, the
ones that arrived earlier, took longer.

#### Investigate transaction history

You can investigate the history of a transaction for a sample of the
simulations performed.

``` r
getTransactionHistory(df$eventData, df$inputData, 20, simVector = c(10,50)) 
```

## 3. Exploration Activities (individual assignments)

### Activity 1: (Edison, Shafaat, Nina working separatelly without LLMs)

#### Set-up, execution, and interpretation

Let’s make two changes now to the parameters. Specifically: - Start now
decreasing block size from `90kB` to `30kB`, `10kB` at a time (maybe
`5kB` after `50kB`. For each of the experiments keep the following data
for each of the sample transactions:

- The time to finality for each transaction.
- The point estimate (% of successes in the total sims ran).
- The belief graph for the first ,say, 30 minutes.

What do you observe?

#### Set-up, execution, and interpretation

Produce two graphs:

- First graph plots the finality of each transaction (Y axis - color
  coded transactions) for each block size.
- Second graph plots the finality point estimate for each block size as
  above.

### Activity 2: (Anthony, Ghazal, Kasra working separatelly with as little LLMs as possible)

### Comprehend and Critique inflow/outflow Caluclations

There is a spreadsheet called `Calculations.xlsx`. The spreadsheet
encodes the logic of the performance analysis offered earlier. Study the
formulae in the spreadsheet and present that logic and mathematical
formulae (use latex format) in your response markdown.

### Create an Shiny app

- Create an R [Shiny](https://shiny.posit.co/r/gallery/) app that
  reproduces the logic of the above spreadsheet. The app should accept
  inputs for the input configurations and display in simple text or
  kables the inferred parameters. Please make it presentable (rounded,
  times are in hh:mm:ss format etc.)

- Revise the app as follows. Pick two or more inputs for which you don’t
  give a value but an interval (min and max size) and a sampling step.
  Then display an equal number of two-lines graph showing how inflow and
  outflow rates, color-coded, change along those intervals.
