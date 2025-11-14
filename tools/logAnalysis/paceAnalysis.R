library(tidyverse)
library(knitr)
library(TOSTER)

producePaceData <- function(dataFolder,experiment){
  pacedata <- read_csv(paste0(dataFolder,experiment,"/EventLog - ",experiment,".csv")) %>% 
    filter(EventType == "Event_ContainerValidation") %>%
    arrange(SimID, SimTime) %>%
    group_by(SimID) %>%
    mutate(SimDiff = SimTime - lag(SimTime, default = 0)) %>%  # use 0 for first lag
    ungroup() %>%
    select(SimID, SimDiff)
  
  write_csv(pacedata,paste0(dataFolder,experiment,"/PaceData - ",experiment,".csv"))
}

#
# Produce Pace Data
#

folder = "../../examples/results/"  

# 5 Node
df_5node = "faithful.5node.30sim - 2025.11.04 12.07.10"
producePaceData(folder, df_5node)
Sys.sleep(1)

# 10 Node
df_10Node_1 = "faithful.10node.30sim - 2025.11.05 14.08.34"
df_10Node_2 = "faithful.10node.30sim - 2025.11.05 14.09.34"
df_10Node_3 = "faithful.10node.30sim - 2025.11.05 14.10.12"
producePaceData(folder, df_10Node_1)
Sys.sleep(1)
producePaceData(folder, df_10Node_2)
Sys.sleep(1)
producePaceData(folder, df_10Node_3)
Sys.sleep(1)

#
# Compare with authoritative
#

auth = 8.521
low_eqbound = -1
high_eqbound = 1

# 5 Node

pacedata.5 = read_csv(paste0(folder,df_5node,"/PaceData - ",df_5node,".csv"))

pace.5 = pacedata.5 %>%
  arrange(SimID, SimDiff) %>%             # ensure ordered
  summarise(
    `Block Time (mins) - Mean....:` = mean(SimDiff)/60000,
    `Block Time (mins) - St. Dev.:`   = sd(SimDiff)/60000
  )
t(pace.5)


distances.5 = pacedata.5$SimDiff/60000


TOSTone.raw(
        m = mean(distances.5),
        sd = sd(distances.5),
        n = length(distances.5),
        mu = auth,
        low_eqbound = low_eqbound,
        high_eqbound = high_eqbound,
        alpha = 0.05)


# 10 Node

pacedata.10 = rbind(read_csv(paste0(folder,df_10Node_1,"/PaceData - ",df_10Node_1,".csv")),
read_csv(paste0(folder,df_10Node_2,"/PaceData - ",df_10Node_2,".csv")),
read_csv(paste0(folder,df_10Node_3,"/PaceData - ",df_10Node_3,".csv")))

pace.10 = pacedata.10 %>%
  arrange(SimID, SimDiff) %>%             # ensure ordered
  summarise(
    `Block Time (mins) - Mean....:` = mean(SimDiff)/60000,
    `Block Time (mins) - St. Dev.:`   = sd(SimDiff)/60000
  )
t(pace.10)



distances.10 = pacedata.10$SimDiff/60000


TOSTone.raw(
  m = mean(distances.10),
  sd = sd(distances.10),
  n = length(distances.10),
  mu = auth,
  low_eqbound = low_eqbound,
  high_eqbound = high_eqbound,
  alpha = 0.05)


