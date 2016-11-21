HPA (Hybrid Performance Analysis)

We present an open source version of HPA, a fast and accurate schedulability analysis technique. It estimates the Worst Case Response Times (WCRTs) of the task graphs run on the distributed multi-core embedded system. It supports both preepmtive and non-preemptive scheduling policy. HPA is developed by Codesign And Parallel processing Laboratory (CAPLab), Seoul National University.

The sources of HPA is under the BSD license and the license texts are in LICENSE document.

Usage:
 The sources of HPA is implemented using JAVA and the main class is in AnalysisRunner.java. For running HPA, you need to give the program an input system description file as an program argument. For example,

  java AnalysisRunner example/example0.xml

We also enclose the example description files in example folder. It defines
  1) Task graphs : xml element "task". it has timing properties such as period, deadline, and jitter.
  2) Tasks in the graph : xml element "subtask". It has priority and computation time bound.
  3) Processing Elements : xml element " PE". the attribute "preemptable" means the PE uses preemptive scheduling policy or not. the elements "subtask" list the mapped tasks.
  4) Task dependency : xml element "dependency". It lists all edges in graphs by xml elements "edge".