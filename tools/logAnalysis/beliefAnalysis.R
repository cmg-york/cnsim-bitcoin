library(tidyverse)
library(nptest)

boot.lower <- function(x,y,Iter){
  npbs <- np.boot(x = x, statistic = y, R = Iter)
  return(ifelse(is.nan(npbs$bca[2,1]),0,npbs$bca[2,1]))
}

boot.upper <- function(x,y,Iter){
  npbs <- np.boot(x = x, statistic = y, R = Iter)
  return(ifelse(is.nan(npbs$bca[2,2]),0,npbs$bca[2,2]))
  return(npbs$bca[2,2])
}


format_simtime <- function(simtime_ms) {
  total_seconds <- simtime_ms / 1000
  hours   <- floor(total_seconds / 3600)
  minutes <- floor((total_seconds %% 3600) / 60)
  seconds <- floor(total_seconds %% 60)
  millis  <- round((total_seconds - floor(total_seconds)) * 1000)
  
  sprintf("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
}

auto_time_to_ms <- function(x) {
  
  # If it's already numeric → treat as milliseconds
  if (is.numeric(x)) {
    return(as.integer(x))
  }
  
  # Remove accidental spaces
  x <- trimws(x)
  
  # Is it a pure integer string?
  if (grepl("^[0-9]+$", x)) {
    return(as.integer(x))
  }
  
  # Otherwise parse as time string
  parts <- unlist(strsplit(x, ":"))
  
  # Case 1: SS.mmm
  if (length(parts) == 1) {
    sec <- as.numeric(parts[1])
    return(round(sec * 1000))
  }
  
  # Case 2: MM:SS.mmm
  if (length(parts) == 2) {
    min <- as.numeric(parts[1])
    sec <- as.numeric(parts[2])
    return(round((min * 60 + sec) * 1000))
  }
  
  # Case 3: HH:MM:SS.mmm
  if (length(parts) == 3) {
    hour <- as.numeric(parts[1])
    min  <- as.numeric(parts[2])
    sec  <- as.numeric(parts[3])
    return(round((hour * 3600 + min * 60 + sec) * 1000))
  }
  
  stop(paste("Unrecognized time format:", x))
}



getBeliefGraph <- function(data, 
                           boot = FALSE, R_Boot = 10000, 
                           threshold,
                           xlims,
                           timeUnit = "min",
                           txVector
) {
  
  
  # Parameter Processing and Validation
  
  if (!(timeUnit %in% c("min","sec","ms"))) {
    print(paste0("[",as.character(sys.call(0)[[1]]),"]: Error: timeUnit",timeUnit, " not recognized."))
    return(-1)
  }
  
  timeDivider <- switch(timeUnit, min = 60000, sec = 1000, ms = 1)
  
  if (missing(xlims)){
    xlim_min = 0
    xlim_max = ceiling(endTime/timeDivider)
    print(paste0("[",as.character(sys.call(0)[[1]]),"]: x axis bounds set to (",xlim_min,",",xlim_max,") ", timeUnit))
  } else {
    xlim_min = xlims[1]
    xlim_max = xlims[2]
  }
  
  if (!missing(txVector)) {
    data <- data %>% filter(Transaction %in% txVector)
  }
  
  if (nrow(data) == 0) {
    print(paste0("[",as.character(sys.call(0)[[1]]),"]: Error: data does not contain rows. Are txList transactions included in the set?"))
  }
  
  
  
  # Bounding dataset 
  
  print(paste0("[",as.character(sys.call(0)[[1]]),"]: Calculating time span."))
  endTime = min(data %>% group_by(Simulation) %>% summarise(maxTime = max(Time)) %>% select(maxTime))
  net = data %>% filter(Time<=endTime)
  
  if (boot) {
    
    print(paste0("[",as.character(sys.call(0)[[1]]),"]: Grouping by Simulation ID. Bootstraping for effor band."))
    confs = net %>% group_by(Time,Transaction) %>% 
      summarise(avgConf = mean(Belief), sd = sd(Belief), medConf = median(Belief)) %>%
      mutate(
        lwr = boot.lower(avgConf,mean,R_Boot),upr = boot.upper(avgConf,mean,R_Boot),
        VaR = quantile(avgConf,0.05))
    
    print(paste0("[",as.character(sys.call(0)[[1]]),"]: Preparing dataset."))
    confs2plot <- confs %>% mutate(Transaction = as.factor(Transaction)) %>%
      mutate(Time = Time / timeDivider)
    print(paste0("[",as.character(sys.call(0)[[1]]),"]: Producing graph."))
    p1 <- ggplot(data = confs2plot, aes(x = Time, y=avgConf, label = avgConf)) + 
      geom_line(aes(color = Transaction)) + 
      #geom_ribbon(aes(ymin=lwr,ymax=upr,fill = Transaction),alpha=0.1) + 
      ylab("Confidence") + xlab(paste0("Time (",timeUnit,")")) +
      xlim(xlim_min,xlim_max) +
      ggtitle(label = "Confidence in f over time")
    
    if (!missing(threshold)) {
      p1 = p1 + geom_hline(yintercept = threshold)
    }
    
    p2 <- ggplot(data = confs2plot, aes(x = Time, y=avgConf, label = avgConf)) + 
      geom_line(aes(y = VaR, color = "VaR")) + 
      geom_ribbon(aes(ymin=lwr,ymax=upr,fill = "Confidence Interval"),alpha=0.1) + 
      ylab("Confidence") + xlab(paste0("Time (",timeUnit,")")) +
      xlim(xlim_min,xlim_max) +
      ggtitle(label = "Confidence in f over time") +
      scale_color_manual(name = "Legend", values = c("VaR" = "red")) +
      scale_fill_manual(name = "Legend", values = c("Confidence interval" = "lightblue"))
    
    
    
    p2 <- ggplot(data = confs2plot, aes(x = Time)) +
      # Ribbon for the confidence interval
      geom_ribbon(
        aes(ymin = lwr, ymax = upr, fill = "Confidence interval"),
        alpha = 0.2
      ) +
      
      # Line for VaR
      geom_line(
        aes(y = VaR, color = "VaR"),
        linewidth = 1
      ) +
      
      # Axis labels and title
      ylab("Confidence") +
      xlab(paste0("Time (", timeUnit, ")")) +
      xlim(xlim_min, xlim_max) +
      ggtitle("Confidence in f over time") +
      
      # Manual color and fill scales — *names must match* the strings in aes()
      scale_color_manual(
        name   = "Legend",
        values = c("VaR" = "red")
      ) +
      scale_fill_manual(
        name   = "Legend",
        values = c("Confidence interval" = "blue")
      )
    
    
    if (!missing(threshold)) {
      p2 = p2 + geom_hline(yintercept = threshold)
    }
    
    return(list(txGraph = p1, VaRGraph = p2, data = confs2plot)) 
    
  } else {
    
    print(paste0("[",as.character(sys.call(0)[[1]]),"]: Grouping by Simulation ID"))
    confs = net %>% group_by(Time,Transaction) %>%
      summarise(avgConf = mean(Belief), sd = sd(Belief), medConf = median(Belief)) 
    
    print(paste0("[",as.character(sys.call(0)[[1]]),"]: Preparing dataset."))
    confs2plot <- confs %>% mutate(Transaction = as.factor(Transaction)) %>%
      mutate(Time = Time / timeDivider)
    
    print(paste0("[",as.character(sys.call(0)[[1]]),"]: Producing graph."))
    p <- ggplot(data = confs2plot, aes(x = Time, y=avgConf, label = avgConf)) + 
      geom_line(aes(color = Transaction)) + 
      ylab("Confidence") + xlab(paste0("Time (",timeUnit,")")) +
      ggtitle(label = "Confidence in f over time") + 
      xlim(xlim_min,xlim_max)
    
    if (!missing(threshold)) {
      p = p + geom_hline(yintercept = threshold)
    }
    return(list(graph = p, data = confs2plot)) 
  }
}


getTransactionHistory <- function(
    eventData,
    blockData,
    txVector) {
  
  
  # Function to map EventType to text
  event_text <- function(type) {
    if(type == "Event_NewTransactionArrival") {
      "(*arrival*) arrives at node"
    } else if(type == "Event_TransactionPropagation") {
      "propagates to node"
    } else if(type == "Node Completes Validation") {
      "(*validation*) appears in a block that was just validated by Node "
    } else if(type == "Node Receives Propagated Block") {
      "is received in a validated block by Node "
    } else if(type == "Appended On Chain (w/ parent)") {
      "(*belief*) is appended on local chain by receiving Node "
    } else if(type == "Appended On Chain (parentless)") {
      "(*belief*) is appended on local chain by validating Node "
    } else {
      type  # fallback
    }
  }
  
  # History according to EventLog
  ed_out <- tibble()
  if (!missing(eventData)) {
    
    eD = eventData %>% filter(Object %in% txVector) %>% arrange(SimTime)
    
    eD_out <- eD %>%
      arrange(SimID, SimTime, EventID) %>%
      rowwise() %>%
      mutate(
        Time = SimTime,
        Transaction = Object,
        Event = paste0(event_text(EventType), " ", Node)
      ) %>%
      ungroup() %>%
      select(SimID, Time, Transaction, Event)  # keep only the columns you want
  }
  
  # History according to BlockLog
  bD_out <- tibble() 
  if (!missing(blockData)) {
    
    for (tx in txVector) {
      bD <- blockData %>%
        rowwise() %>%
        filter(length(intersect(as.integer(str_split(str_remove_all(BlockContent, "[{}]"), ";")[[1]]), tx)) > 0) %>%
        ungroup()
      
      bD_out <- bD_out %>%
        bind_rows(
          bD %>%
            arrange(SimID, SimTime) %>%
            rowwise() %>%
            mutate(
              Time = SimTime,
              Transaction = tx,
              Event = paste0(event_text(EvtType)," ", NodeID)
            ) %>%
            ungroup() %>%
            select(SimID, Time, Transaction, Event)  # keep only the columns you want
        )
    }
    
  }
  
  all_out <- bind_rows(bD_out, eD_out)

  for (sim in unique(all_out$SimID)) {
    
    cat("Simulation", sim, ":\n")
    cat(paste0("    HH:MM:SS.mmm\n"))
    all_out %>%
      filter(SimID == sim) %>%
      arrange(Time, Transaction) %>%  # optional: order events
      rowwise() %>%
      mutate(
        line = paste0("    ", format_simtime(Time), ": Transaction ", Transaction, " ", Event)
      ) %>%
      pull(line) %>%
      paste(collapse = "\n") %>%
      cat("\n")
    
    cat("\n")  # extra line between simulations
  }
}


getFinality <- function(
    beliefData,
    horizon,
    threshold = 0.9,
    txVector) {

  
  
  h = max(beliefData$Time)
  if (missing(horizon) || (auto_time_to_ms(horizon) > h)) {
    horizon = h
  } else {
    horizon = auto_time_to_ms(horizon)
  }
  
  # result = beliefData %>% filter(Time <= horizon) %>% group_by(Time,Transaction) %>% 
  #   summarise(
  #     Horizon = max(Time,na.rm = TRUE),
  #     MeanBelief = mean(Belief,na.rm = TRUE),
  #     Final = (MeanBelief>threshold),
  #     .groups = "drop") %>%
  #   mutate(Time = format_simtime(Horizon)) %>% 
  #   select(Transaction, Time, MeanBelief, Final, Horizon)
  
  result <- beliefData %>%
    # Step 1: keep only times <= horizon
    filter(Time <= horizon) %>%
    
    # Step 2: for each Transaction, find the maximum Time
    group_by(Transaction) %>%
    filter(Time == max(Time)) %>%
    
    # Step 3: calculate mean Belief over simulations for that Transaction × Time
    summarise(
      ms = first(Time),                    # the max time
      MeanBelief = mean(Belief, na.rm = TRUE),
      .groups = "drop"
    ) %>% 
  
    mutate(Time = format_simtime(ms), Final = (MeanBelief >= threshold)) %>% 
    select(Transaction, Time, MeanBelief, Final, ms)
  
  if (missing(txVector)) {
    return(result)
  } else {
    return(result %>% filter(Transaction %in% txVector))
  }

}


#
# A D V A N C E D
#


#Aggregate over simulations
# confs = net %>% group_by(Time,Transaction) %>% 
#   summarise(avgConf = mean(conf), sd = sd(conf), medConf = median(conf), 
#             lwr = boot.lower(conf,mean,Bootstrap_R),upr = boot.upper(conf,mean,Bootstrap_R),
#             VaR = quantile(conf,0.05))
# 
# confs2plot <- confs %>% mutate(Condition = as.factor(Transaction))
# 
# 
# ggplot(data = confs2plot, aes(x = Time, y=avgConf)) + 
#   geom_line(aes(color = Condition)) + 
#   geom_ribbon(aes(ymin=lwr,ymax=upr,fill = Condition),alpha=0.1) + 
#   ylab("Confidence") +
#   ggtitle(label = "Confidence in f over time (mean and 95% CI)") + 
#   geom_hline(yintercept = 0.8)
# 
# 
# View(confs2plot)
