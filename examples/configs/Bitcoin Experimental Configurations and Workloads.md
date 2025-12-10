---
title: "Bitcoin experimental configurations and workloads"
format: html
editor: visual
---

# Index

|                                                          Configuration                                                           | Data | Description                                                                                                                                                                                                                                   |
| :------------------------------------------------------------------------------------------------------------------------------: | ---- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [`bitcoin.faithful.10node.30sim.XX-XX.properties`](./faithful/bitcoin.faithful.30node.30sim.1-5.properties) <br>Code: `faithful` |      | Realistic Bitcoin simulation with:<br>- 10 nodes<br>- Difficulty, power, arrival rate, tx size, parameters as of `2024-11-25`<br>- Observed block time that day: `8.521`<br>- Duration: 3 hour                                                |
|                            `bitcoin.lowrate.5node.100sim.properties`<br>Code: `lowrate.5node.100sim`                             |      | As above but with:<br><br>- $\lambda$ = 1 (instead of realistic)<br>    <br>- Mean tx size = `1461.15` to make up for the fewer transactions.<br>    <br>- This results for about 3600 arrivals per hour allowing for faster simulations.<br> |
|                        `bitcoin.lowrate.5node.100sim.properties`<br>Code: `lowrate.txvalue.5node.100sim`                         |      | As `lowrate.5node.100sim` but with:                                                                                                                                                                                                           |
|                        `bitcoin.lowrate.5node.100sim.properties`<br>Code: `lowrate.slownet.5node.100sim`                         |      | As `lowrate.5node.100sim` but with:                                                                                                                                                                                                           |


# Faithful Bitcoin Network
Configuration file: `bitcoin.faithful.30node.30sim.XX-XX.properties` (where `XX` is to be replaced with the Simulation ID)
Workload: `bitcoin.faithful-workload.csv`
## Overview
We simulate the Bitcoin network with properties as exhibited at a specific arbitrary date namely `2024-11-25`.

We assume 10 nodes. Each node is a stand-in for a greater number of nodes; e.g., a pool. The node has the power of the sum of the powers of the nodes it represents.

We want to study bitcoin for 3 hours of simulated operation time.
## Difficulty and hashpowers
Difficulty is fetched from [CoinWarz](https://www.coinwarz.com/mining/bitcoin/difficulty-chart) and at the time of fetching it (`2024-11-25`) is `102.29T`. We need to convert this Bitcoin-specific difficulty to CNSim difficulty (search space/success space) using the `BitcoinDIfficulyUtility.java` routine `BTCToCNSIM(double BTCDiff)`. The result seen below is given as a parameter:
```         
pow.difficulty = 4.3933637821322E+23
```
The hashpower of the network estimated on `2024-11-25` is `859.34M TH/s`, according to [YCharts](https://ycharts.com/indicators/bitcoin_network_hash_rate). This is `8.5934e+20 H/s`. For simplicity we assume that this is shared among our 10 nodes, so each has an average power `8.5934e+19 H/S`. We further assume a `10%` coefficient of variation (CV) so the standard deviation will be `8.5934e+18 H/S`. Note that, with this way of splitting the hashpowers, summing up the individual hashpowers will likely deviate from the original total hashpower measurement. CNSim measures power in `GH/s`, so the corresponding exponents are `-10` and `-9`:
```         
pow.hashPowerMean = 8.5934001000e+10
pow.hashPowerSD = 8.5934001000e+9
```
## Workload design
The arrival rate obtained from [Blockchain.com](https://www.blockchain.com/explorer/charts/transactions-per-second) for the date in question is $\lambda$ = `6.494 tx/sec`. That will mean a total of `70,135` transactions included in the workload. According to the same source the average transaction fee is `$3.366 USD` with `1 BTC = $93,003.21 USD`, hence `3.366/93,003.21 = 0.00003619229 BTC = 3.619229E-5 BTC = 3,619.23 SATS`. We will again assume a CV of `10%` for fees.

In addition we learn from [Bitcoin Visuals](https://bitcoinvisuals.com/chain-tx-size) that the average transaction size is `460 bytes`. The web-site offers also the 90th, 50th, 10th percentiles to be `491`, `225`, and `181` bytes. Although the distribution appears to be a long-tail one, we make the simplifying assumption of normality around the median. We will then calculate the standard deviation as follows (script in `R`).
```{R}
mean <- 225    # Given mean (average)
p10 <- 181     # 10th percentile
p90 <- 491     # 90th percentile

# Calculate standard deviation
z10 <- qnorm(0.1)  # Z-score for 10th percentile
z90 <- qnorm(0.9)  # Z-score for 90th percentile
sigma <- (p90 - p10) / (z90 - z10)
```
Above `sigma` is the sought standard deviation.

Hence the following is the transaction configuration:
```         
workload.lambda = 6.494f
...
workload.txSizeMean = 225f
workload.txSizeSD = 120.9471f
workload.txFeeValueMean = 3,619.23f
workload.txFeeValueSD = 369.19f
```
## Network and Bandwidth
We finally assume an average `25MBps` end-to-end bandwidth between any two nodes with a CV of `10%`. Hence:

```         
net.throughputMean = 25000f
net.throughputSD = 2500f
```
## Sample Transactions
We use ten (10) transactions (200, 210, ..., 290) as the sample of transactions on which to perform sanity tests.
## Seed Management
All experiments use the same network and workload up to transaction `300`, after which the random sampler takes over, with different seed in each simulation:
```
net.sampler.seed = 123
net.sampler.seed.updateSeed = false

workload.sampler.file = ./examples/configs/faithful/bitcoin.faithful-workload.csv
workload.sampler.seed = 321
workload.sampler.seed.updateSeed = true
```
At node level, behavior is the same until time `49,090 ms`, where it is expected that transaction # 290 will arrive at the system.
```
node.sampler.seed = {444,222}
node.sampler.updateSeedFlags = {true,true}
node.sampler.seedUpdateTimes = {49,090}
```


# Lowrate Bitcoin (plain)

Configuration file: `bitcoin.lowrate.30node.100sim.XX-XX.properties` (where `XX` is to be replaced with the Simulation ID)
Workload: None / varies
## Overview
We use the same parameters as the faithful simulation except:
- We increase the number of nodes to 30 to allow variability in confidence.
- We restrict the arrival rate to 1 tx per second to reduce the total workload.
- We increase the transaction size to make up for there being fewer transactions.
- We perform 100 simulations to achieve statistical power.

## Difficulty and hashpowers
Please see the faithful section for difficulty and total hashpower. Assuming 30 rather than 10 nodes we now have to divide average power by 3.
```         
pow.hashPowerMean = 2.8644667+10
pow.hashPowerSD = 2.8644667e+9
```
## Workload design

- We now assume a  $\lambda$ = `1 tx/sec`. That will mean a total of `3`$\times$ `3600` = `10800` transactions expected in the workload. 
- *Transaction fees* are handled as per the faithful.
- Transaction sizes are multiplied by the reduction in arrival rate, so that the total amount of bytes continues to arrive at the network.

In addition we learn from [Bitcoin Visuals](https://bitcoinvisuals.com/chain-tx-size) that the average transaction size is `460 bytes`. The web-site offers also the 90th, 50th, 10th percentiles to be `491`, `225`, and `181` bytes. Although the distribution appears to be a long-tail one, we make the simplifying assumption of normality around the median. We will then calculate the standard deviation as follows (script in `R`).
```{R}
mean <- 225    # Given mean (average)
p10 <- 181     # 10th percentile
p90 <- 491     # 90th percentile

# Calculate standard deviation
z10 <- qnorm(0.1)  # Z-score for 10th percentile
z90 <- qnorm(0.9)  # Z-score for 90th percentile
sigma <- (p90 - p10) / (z90 - z10)
```
Above `sigma` is the sought standard deviation.

Hence the following is the transaction configuration:
```         
workload.lambda = 6.494f
...
workload.txSizeMean = 1461.15f
workload.txSizeSD = 785.774f
workload.txFeeValueMean = 3619.23f
workload.txFeeValueSD = 369.19f
```
## Network and Bandwidth
We assume the same network characteristics as in the `faithful` case
## Sample Transactions
As in the `faithful` test, we use ten (10) transactions (200, 210, ..., 290) as the sample of transactions on which to perform sanity tests.
## Seed Management
All experiments use the same network and initial workload (loaded from file). Workload changes with each experiment thereafter:
```
net.sampler.seed = 123
net.sampler.seed.updateSeed = false

workload.sampler.file = ./examples/configs/bitcoin.lowrate-workload.csv
workload.sampler.seed = 321
workload.sampler.seed.updateSeed = true
```

At node level, behavior is the same until time `327,000 ms` (about 5 minutes in), a bit after transaction # 300 arrives at the system.
```
node.sampler.seed = {444,222}
node.sampler.updateSeedFlags = {false,true}
node.sampler.seedUpdateTimes = {327000}
```


# Lowrate Bitcoin (Transaction Fee Variability)

TODO
# Lowrate Bitcoin (Slow Network)

TODO