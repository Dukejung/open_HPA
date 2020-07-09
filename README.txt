HPA (Hybrid Performance Analysis)

We present an open-source version of HPA, a fast and accurate schedulability analysis technique. It estimates the Worst-Case Response Times (WCRTs) of the task graphs run on the distributed multi-core embedded system. It supports both preemptive and non-preemptive scheduling policy. HPA is developed by Codesign And Parallel processing Laboratory (CAPLab), Seoul National University.

The sources are under the BSD license and the license texts are in the LICENSE document.

Usage:
 For running HPA, you need to make a runnable jar file(ex:HPA.jar) including the main method in HPA.java. To run the program, give an input system description file as a program argument. For example,

  java HPA.jar example/example.xml
  
We also enclose an example description file in the example folder. It defines
  1) Task Graphs : xml element "Graph". it has timing properties inside xml element "Activation" such as period, deadline, and jitter.
  2) Tasks in the graph : xml element "Task". It has priority and computation time-bound with BCET(Best-Case Execution Time) and WCRT(Worst-Case Execution Time).
  4) Task dependency : xml element "Dependency". It has names of the source and destination task.
  3) Processing Elements : xml element " ProcessingElement". the attribute "Preemptable" means the processing element uses preemptive scheduling policy or not. The elements "Mapping" list the mapped tasks.

References:
Junchul Choi, Hyunok Oh, and Soonhoi Ha, "A Hybrid Performance Analysis Technique for Distributed Real-Time Embedded Systems," https://link.springer.com/article/10.1007/s11241-018-9307-x