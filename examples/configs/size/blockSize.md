# CNSim: Finality analysis under limited block size.
Conceptual Modeling Group @ York University

- [Overview](#overview)
- [Analysis](#analysis)
  - [Analysis Routines](#analysis-routines)
  - [Finalities](#finalities)
    - [Gather Data](#gather-data)
    - [Graph per Block Size](#graph-per-block-size)
    - [Individual Graphs](#individual-graphs)
  - [Time to Finality](#time-to-finality)
    - [Gather Data](#gather-data-1)
    - [Graph per block size](#graph-per-block-size-1)

# Overview

We investigate the role of block size to the finality and
time-to-finality of individual transactions. We specifically consider
the flow of incoming transactions and it terms of size in bytes (inflow)
to the flow of block validations again in terms of size in bytes
(outflow). In general, if the former is greater than the latter,
competition for placement in blocks ensues, making finality and
time-to-finality contingent on the transaction fees. We hence expect
that, as we reduce block size, the point at which inflow equals outflow
constitutes an inflection point, below which all transactions will enjoy
a nominal finality rate, but beyond which, if we further reduce block
size, low-fee transactions will experience increasingly longer
time-to-finality scores. We hence devise an experimental set-up to
investigate this qualitatively via graphing the corresponding data.

We construct a network of $30$ nodes, and difficulty and node power at
$4.4\cdot 10^{23}$ and $5.7\cdot 10^{10} GH$ which implies an expected
block pace of about 4:15 minutes (mm:ss). We set end-to-end network
connections to $25MB$ which allows broadcasting within seconds and
prevents synchronization from becoming a nuisance factor. The rate of
incoming transactions is set to $\lambda = 0.025$, i.e. $1.5$
transactions per minute and their average size is set to $10kB$. From
these configurations it follows that inflow
$\lambda * 10kB * 60 = 15kB/min$. The block size needed for serving this
inflow needs to be such that inflow = outflow, hence,
$\mathrm{(block size)}/\mathrm{(pace)} = 15kB$, which is satisfied for
block size of about $64kB$. We run $100$ experiments for different block
sizes around this point, starting from $90kB$ and reducing it to $30kB$.
We use the same 100-arrivals workload with sample transactions 10, 20,
30, 40, 50, which are respectively the 10th, 20th, etc. transaction to
arrive to the network out of the 100 that arrive. We set the size of
these transactions specifically to be $10kB$ (others are normally
distributed around that mean and a standard deviation of $1kB$) and set
the fee associated with them to be respectively, the 10th, 25th, 50th,
75th, and 90th percentile of a normal distribution of fee levels with
mean $3619.23$ and standard deviation $369.19$.

Below are the corresponding values that can also be found in
`size-factor.csv`

``` r
mean_val = 3619.23
sd_val = 3619.23/10

qnorm(c(0.1,0.25,0.5,0.75,0.95), mean = mean_val, sd = sd_val)
```

    [1] 3155.407 3375.117 3619.230 3863.343 4214.540

# Analysis

Variable `logAnalysisToolPath` must point to the
`cnsim-tools/R-Tools/src/logAnalysis/` directory of `cnsim-tools`.

## Analysis Routines

Variable `outputFolder` must point to the directory where the output of
the form `size-factor - XXX - YYYYY` is. Not included in this repo due
to large size. Please refer to publication for pointers to data.
Alternatively reproduce the data with CNSim and rename files and
directories as above, where `XXX` is the workload size (100 and 300),
and `YYYYY` is the block size. Block sizes attempted are:

    [1] 35000 45000 30000 40000 50000 60000 70000 80000 90000

## Finalities

Below we analyze 100 experimental runs for a number of different block
sizes ranging from $30Kb$ to $90Kb$. Workload is a total of 300
transactions. Finality is evaluated with horizon of 2 hours and 40
minutes, which is approximately the expected interval between the
arrival of the last sample transaction (50) and the last transaction of
the workload , past which there are unnaturally no more arrivals and
nodes validate old left-over transactions.

### Gather Data

``` r
#finalityCrossF %>% distinct(Run,BlockSize) %>% arrange(desc(Run))
#Fruns = unique(finalityCrossF$Run)
#Bsizes = unique(finalityCrossF$BlockSize)

block_sizes = c(35000,45000,seq(30000,90000,by = 10000))

finalityRuns = paste0("size-factor - 300 - ",block_sizes)

initializeStores()

for (i in 1:length(finalityRuns)) {
  run = finalityRuns[i]
  block_size = block_sizes[i]
  
  x = analyzeExperiment(run,block_size)

  paces = bind_rows(paces,x$pace %>% mutate(experiment = run,
                                            bsize =block_size))

  finalities = bind_rows(finalities,x$finality.result %>%
                           select(Transaction,point_estimate) %>%
                           mutate(experiment = run,
                                  bsize =block_size))
  
  graph.data.result = bind_rows(graph.data.result,
                                x$graph.data %>%
                           mutate(experiment = run,
                                  bsize =block_size))

  time.to.finalities = bind_rows(time.to.finalities, x$time.to.finality %>%
                           mutate(experiment = run,
                                  bsize =block_size))
}
```

    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."

### Graph per Block Size

In the graph below $63kB$ is the block size at which the incoming flow
in transaction bytes/sec, calculated by average transaction size and
arrival rate, equals to outgoing flow (bytes validated per second),
calculated based on block size and validation pace.

``` r
finalityGraph.1 <- ggplot(data = 
         finalities %>% mutate(Transaction = as.factor(Transaction)),
       aes(x = bsize, y=point_estimate, color = Transaction)) + 
  geom_line() + 
  scale_x_reverse(expand = c(0.01,0),labels = function(x) paste0(x / 1000, " kB")) +
      geom_vline(xintercept = 63906, linetype = "dashed") +
    annotate("text", y = 0, x = +40000, label = "in rate > out rate\n(bytes/sec)    ",hjust = 1.1, vjust = -0.5) + 
    annotate("rect",
           xmin = 63906, xmax = -Inf,
           ymin = -Inf, ymax = Inf, fill = "yellow",
           alpha = 0.2) +
      labs(title = "Finality over Block Size",
           y = "Finality within h = 2:40:00",
           x = "Block Size (kilobytes)") +
  theme(
    axis.title.x = element_text(size = 14),
    axis.title.y = element_text(size = 14),
    axis.text.x = element_text(size = 12),
    axis.text.y = element_text(size = 12),
    plot.title = element_text(size = 15, face = "bold")
    #legend.position = "bottom"
  )
finalityGraph.1 
```

![](blockSize_files/figure-commonmark/unnamed-chunk-7-1.png)

### Individual Graphs

In the graph below the progression of belief in the two extreme block
sizes tried is seen.

``` r
threshold = 0.9

b30 <- getBeliefGraph(graph.data.result %>% filter(bsize == 30000), xlim = c("0:00","1:00:00"),titleSuffix = ": 30kB Block") +
  theme(
    axis.title.x = element_text(size = 14),
    axis.title.y = element_text(size = 14),
    axis.text.x = element_text(size = 12),
    axis.text.y = element_text(size = 12),
    legend.position = "bottom") +
  geom_hline(yintercept = threshold, linetype = "dashed") + 
        annotate(
      "text",
      x = 0,
      y = threshold,
      label = "0.9",
      vjust = -0.5,
      hjust = 0,                  # flush left
      color = "darkred"
    )
```

    [1] "[getBeliefGraph]: Producing graph."

``` r
b90 <- getBeliefGraph(graph.data.result %>% filter(bsize == 90000), xlim = c("0:00","1:00:00"),titleSuffix = ": 90kB Block") +
  theme(
    axis.title.x = element_text(size = 14),
    axis.title.y = element_text(size = 14),
    axis.text.x = element_text(size = 12),
    axis.text.y = element_text(size = 12),
    legend.position = "bottom") +
  geom_hline(yintercept = threshold, linetype = "dashed") + 
        annotate(
      "text",
      x = 0,
      y = threshold,
      label = "0.9",
      vjust = -0.5,
      hjust = 0,                  # flush left
      color = "darkred"
    ) 
```

    [1] "[getBeliefGraph]: Producing graph."

``` r
grid.arrange(b30,b90,nrow = 1)
```

![](blockSize_files/figure-commonmark/unnamed-chunk-8-1.png)

## Time to Finality

We now run the same experiment for 100 incoming transactions (instead of
300) and set the horizon to be the end of the simulation, such that all
transactions are validated. The time to finality is then measured.

### Gather Data

``` r
#Fruns = unique(finalityCross$Run)
#Bsizes = unique(finalityCross$BlockSize)
#finalityCross %>% distinct(Run,BlockSize) %>% arrange(desc(Run))

block_sizes = c(35000,45000,55000,seq(30000,90000,by = 10000))

finalityRuns = paste0("size-factor - 100 - ",block_sizes)

initializeStores()

for (i in 1:length(finalityRuns)) {
  run = finalityRuns[i]
  block_size = block_sizes[i]
  
  x = analyzeExperiment(run,block_size)

  paces = bind_rows(paces,x$pace %>% mutate(experiment = run,
                                            bsize =block_size))

  finalities = bind_rows(finalities,x$finality.result %>%
                           select(Transaction,point_estimate) %>%
                           mutate(experiment = run,
                                  bsize =block_size))
  
  graph.data.result = bind_rows(graph.data.result,
                                x$graph.data %>%
                           mutate(experiment = run,
                                  bsize =block_size))

  time.to.finalities = bind_rows(time.to.finalities, x$time.to.finality %>%
                           mutate(experiment = run,
                                  bsize =block_size))
}
```

    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."
    [1] "[prepareGraphData]: Calculating time span."
    [1] "[prepareGraphData]: Grouping by Simulation ID."
    [1] "[prepareGraphData]: Preparing dataset."

### Graph per block size

``` r
# Function to convert minutes -> hh:mm:ss
minutes_to_hms <- function(mins) {
  h <- floor(mins / 60)
  m <- floor(mins %% 60)
  s <- round((mins - floor(mins)) * 60)
  sprintf("%02d:%02d:%02d", h, m, s)
}


timeToFinalityGraph <- ggplot(data = time.to.finalities, aes(x = bsize, y=TimeFinalized/60000, color = Transaction)) + 
      geom_line() + scale_x_reverse(expand = c(0.01,0),
                                     labels = function(x) paste0(x / 1000, " kB")) +
      geom_vline(xintercept = 63906, linetype = "dashed") +
      geom_hline(yintercept = 4217832/60000, linetype = "dashed") +
    annotate("text", y = 40, x = +73000, label = "workload ends\n(01:10:18)",hjust = 1, vjust = -0.5) + 
    annotate("text", y = 300, x = +40000, label = "in rate > out rate\n(bytes/sec)    ",hjust = 1, vjust = -0.5) + 
    annotate("rect",
           xmin = 63906, xmax = -Inf,
           ymin = -Inf, ymax = Inf, fill = "yellow",
           alpha = 0.2) +
  scale_y_continuous(labels = minutes_to_hms) +
      labs(title = "Time to Finality over Block Size",
           y = "Time (hh:mm:ss)",
           x = "Block Size (kilobytes)") +
  theme(
    axis.title.x = element_text(size = 14),
    axis.title.y = element_text(size = 14),
    axis.text.x = element_text(size = 12),
    axis.text.y = element_text(size = 12),
    plot.title = element_text(size = 15, face = "bold")
    #legend.position = "bottom"
  )
timeToFinalityGraph
```

![](blockSize_files/figure-commonmark/unnamed-chunk-10-1.png)
