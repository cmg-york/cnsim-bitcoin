source("beliefAnalysis.R")



getFinality(net, horizon = "19:29.050")

getBeliefGraph(beliefData,threshold = 0.9, timeUnit = "min",xlim = c(0,50))



#
#
# LOW RATE 1
#
#

outputRelFolder = "../../examples/results/"
experiment = "lowrate.5node.30sim - 2025.11.13 20.06.40"
blockData = read_csv(paste0(outputRelFolder,experiment,"/BlockLog - ",experiment,".csv"))
eventData = read_csv(paste0(outputRelFolder,experiment,"/EventLog - ",experiment,".csv"))
beliefData = read_csv(paste0(outputRelFolder,experiment,"/BeliefLogShort - ",experiment,".csv")) %>% 
  rename(Simulation = SimID, Time = `Time (ms from start)`, Transaction = `Transaction ID`)
txVector = c(200,210,220,230,240,250,260,270.280,290)

getTransactionHistory(eventData,blockData,txVector = c(210))
lowrate1.plain = getBeliefGraph(beliefData, boot=FALSE, timeUnit = "min",threshold = 0.8,txVector = txVector)
lowrate1.band = getBeliefGraph(beliefData, boot=TRUE, R_Boot = 100, xlims = c(0,180))



#
#
# FAITHFUL 1
#
#



outputRelFolder = "../../examples/results/"
experiment_part1 = "faithful.10node.30sim - 2025.11.05 14.08.34"
experiment_part2 = "faithful.10node.30sim - 2025.11.05 14.09.34"
experiment_part3 = "faithful.10node.30sim - 2025.11.05 14.10.12"

beliefData <- 
  rbind(
    read_csv(paste0(outputRelFolder,experiment_part1,"/BeliefLogShort - ",experiment_part1,".csv")) %>% rename(Simulation = SimID, Transaction = `Transaction ID`,Time = `Time (ms from start)`),
    read_csv(paste0(outputRelFolder,experiment_part2,"/BeliefLogShort - ",experiment_part2,".csv")) %>% rename(Simulation = SimID, Transaction = `Transaction ID`,Time = `Time (ms from start)`),
    read_csv(paste0(outputRelFolder,experiment_part3,"/BeliefLogShort - ",experiment_part3,".csv")) %>% rename(Simulation = SimID, Transaction = `Transaction ID`,Time = `Time (ms from start)`)
  )


eventData <- 
  rbind(
    read_csv(paste0(outputRelFolder,experiment_part1,"/EventLog - ",experiment_part1,".csv")), 
    read_csv(paste0(outputRelFolder,experiment_part2,"/EventLog - ",experiment_part2,".csv")),
    read_csv(paste0(outputRelFolder,experiment_part3,"/EventLog - ",experiment_part3,".csv")) 
  )


txVector = c(200,210,220,230,240,250,260,270.280,290)

getTransactionHistory(eventData,txVector = c(210))



faithful1.plain = getBeliefGraph(beliefData, boot=FALSE, R_Boot = 10, timeUnit = "min",threshold = 0.8,txVector = txVector)


faithful1.plain$graph

faithful1.band = getBeliefGraph(beliefData, boot=TRUE, R_Boot = 100, xlims = c(0,180))


