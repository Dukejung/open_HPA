HPA (Hybrid Performance Analysis)

We present an open source version of HPA, a fast and accurate schedulability analysis technique. It estimates the Worst Case Response Times (WCRTs) of the task graphs run on the distributed multi-core embedded system. It supports both preepmtive and non-preemptive scheduling policy. HPA is developed by Codesign And Parallel processing Laboratory (CAPLab), Seoul National University.
We also present the jar executable of the optimized HPA as well as the source codes of the primitive HPA.

The sources and the executable of HPA are under the BSD license and the license texts are in LICENSE document.

Usage:
 The sources of HPA are implemented using JAVA and the main class is in AnalysisRunner.java. For running HPA, you need to give the program an input system description file as an program argument. For example,

  java AnalysisRunner example/example0.xml
  
or you can use the presented jar executable as

  java -jar open_HPA_executable.jar example/example0.xml

We also enclose the example description files in example folder. It defines
  1) Task graphs : xml element "task". it has timing properties such as period, deadline, and jitter.
  2) Tasks in the graph : xml element "subtask". It has priority and computation time bound.
  3) Processing Elements : xml element " PE". the attribute "preemptable" means the PE uses preemptive scheduling policy or not. the elements "subtask" list the mapped tasks.
  4) Task dependency : xml element "dependency". It lists all edges in graphs by xml elements "edge".


References:
Junchul Choi, Hyunok Oh, and Soonhoi Ha, "A Hybrid Performance Analysis Technique for Distributed Real-Time Embedded Systems," https://arxiv.org/abs/1604.04951
