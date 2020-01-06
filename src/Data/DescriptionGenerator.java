package Data;

import java.io.*;
import java.util.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "DescriptionParameters")
class DescriptionParams
{
    @XmlElement public int PECountMin;
    @XmlElement public int PECountMax;
    
    @XmlElement public int SRCountMin;
    @XmlElement public int SRCountMax;
    
    @XmlElement public int CoreCountMin;
    @XmlElement public int CoreCountMax;
    
    @XmlElement public int GraphCountMin;
    @XmlElement public int GraphCountMax;

    @XmlElement public int CriticalCountMin;
    @XmlElement public int CriticalCountMax;
    
    @XmlElement public int QoSMin;
    @XmlElement public int QoSMax;
    
    @XmlElement public int TaskCountMin;
    @XmlElement public int TaskCountMax;

    @XmlElement public int BCETMin;
    @XmlElement public int BCETMax;
    @XmlElement public int CompTimeVariation;

    @XmlElement public int OverheadMin;
    @XmlElement public int OverheadMax;
    
    @XmlElement public int StartOffsetMin;
    @XmlElement public int StartOffsetMax;

    @XmlElement public int DistanceMin;
    @XmlElement public int DistanceMax;

    @XmlElement public int JitterRatioMin;
    @XmlElement public int JitterRatioMax;

    @XmlElement public int PriorityAssignmentRule; // 0 : Application-level assignment, 1: task-level assignment
    
    @XmlElement public int PreemptiveMode;  // 0 : only preemptive, 1 : only non-preemptive, 2 : mixed
    
    @XmlElement public int HyperPeriodMax;

    @XmlElement public int DependencyMode;	// 0 : normal dependency, 1 : linear dependency, 2 : no dependency
    
    @XmlElement public int HardeningMode;	// 0 : only re-execution, 1 : only active replication, 2: only passive replication, 3: mixed
}

public class DescriptionGenerator
{	
    DescriptionParams input;
    Description output;

    int graphCount;
    int[] taskCount;
    int totalTaskCount;
    int PECount;
    int SRCount;

    Data.Task[] tasks;
    int[] mappedPE;
    boolean[] preemptable;
    int[] parentTaskID;
    
    boolean[][] dependencyMatrix;
	private BitSet[] SuccessorSet;

	public int periodRatio = 2;
	
    public DescriptionGenerator(String path)
    { 
    	try {
    		InputStream xmlInput = new FileInputStream(path);
    		JAXBContext jaxbContext = JAXBContext.newInstance(DescriptionParams.class);
    		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
    		input = (DescriptionParams) unmarshaller.unmarshal(xmlInput);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }

    public void Generate(String filePath)
    {
        output = new Description();
        
        SRCount = 0;
        graphCount = 0;
        totalTaskCount = 0;
        PECount = 0;
        tasks = null;
        mappedPE = null;
        preemptable = null;
        parentTaskID = null;
        dependencyMatrix = null;

        DetermineVariables();
        CreateTaskList();
        CreatePEList();
        CreateDependency();
        SetPeriods();
        SetJitters();

        //ChangeSubtaskID2Priority();

        output.save(filePath);
    }
    
    public static void main(String args[])
    {
    	run("E:\\Work");
    }
    
    static void run(String path)
    {
    	DescriptionGenerator tg = new DescriptionGenerator(path+"\\DescriptionParams.xml");
    	for(int i = 0 ; i < 1; i++)
    	{
    		tg.Generate(path+"\\output"+i+".xml");
    		System.out.println("DONE "+i); 
    	}
    	System.out.println("DONE");
    }

    void DetermineVariables()
    {
        Random rand = new Random();
        
        SRCount = input.SRCountMin + rand.nextInt(input.SRCountMax - input.SRCountMin + 1);
        output.SRcount = SRCount;

       	PECount = input.PECountMin + rand.nextInt(input.PECountMax - input.PECountMin + 1);
       	
       	graphCount = input.GraphCountMin + rand.nextInt(input.GraphCountMax - input.GraphCountMin + 1);
       	taskCount = new int[graphCount];
        for (int i = 0; i < graphCount; ++i)
        {
        	taskCount[i] = input.TaskCountMin + rand.nextInt(input.TaskCountMax - input.TaskCountMin + 1);
        	totalTaskCount += taskCount[i];
        }
    }

    void CreateTaskList()
    {
        Random rand = new Random();
        
        int[] priorities = new int[totalTaskCount];

        for (int i = 0; i < totalTaskCount; ++i)
            priorities[i] = i;
        for (int i = 0; i < totalTaskCount; ++i)
        {
            int shuffleIndex = rand.nextInt(totalTaskCount);

            int temp = priorities[i];
            priorities[i] = priorities[shuffleIndex];
            priorities[shuffleIndex] = temp;
        }
        
        if (input.PriorityAssignmentRule == 0)
        {	// application-level priority assignment
            int[] app_priorities = new int[graphCount];
            for (int i = 0; i < graphCount; ++i)
            	app_priorities[i] = i;
            for (int i = 0; i < graphCount; ++i)
            {
            	int shuffleIndex = rand.nextInt(graphCount);
            	
            	int temp = app_priorities[i];
            	app_priorities[i] = app_priorities[shuffleIndex];
            	app_priorities[shuffleIndex] = temp;
            }
            for (int i = 0, accum = 0; i < graphCount; ++i)
            {
            	for (int j = 0; j < taskCount[i]; ++j)
            		priorities[accum + j] += totalTaskCount*app_priorities[i];
            	accum += taskCount[i];
            }
        }
        
        // criticality
        boolean[] criticalities = new boolean[graphCount];
        int criticalCount = input.CriticalCountMin + rand.nextInt(input.CriticalCountMax - input.CriticalCountMin + 1);
        for (int i = 0; i < graphCount; ++i)
        {
        	if (i < criticalCount)	criticalities[i] = true;
        	else criticalities[i] = false;
        }
        for (int i = 0; i < graphCount; ++i)
        {
        	int shuffleIndex = rand.nextInt(graphCount);
        	
        	boolean temp = criticalities[i];
        	criticalities[i] = criticalities[shuffleIndex];
        	criticalities[shuffleIndex] = temp;
        }
        
        tasks = new Data.Task[totalTaskCount];
        mappedPE = new int[totalTaskCount];
        preemptable = new boolean[totalTaskCount];
        parentTaskID = new int[totalTaskCount];

        int taskIndex = 0;
        output.GraphList = new ArrayList<Data.Graph>();
        for (int i = 0; i < graphCount; ++i)
        {
        	Data.Graph graph = new Data.Graph();
        	graph.ID = i;
        	graph.Name = Integer.toString(graph.ID);
        	
        	graph.Activation = new Data.Activation();
        	graph.Activation.StartOffset = 0;
        	graph.Activation.Distance = input.DistanceMin + rand.nextInt(input.DistanceMax - input.DistanceMin + 1);
        	
        	graph.TaskList = new ArrayList<Data.Task>();
        	for (int j = 0; j < taskCount[i]; ++j)
        	{
        		Data.Task task = new Data.Task();
        		parentTaskID[taskIndex + j] = i;
        		tasks[taskIndex + j] = task;
        		
                task.ID = taskIndex + j;
                task.Name = Integer.toString(task.ID);
                task.Priority = priorities[task.ID];
                task.BCET = input.BCETMin + rand.nextInt(input.BCETMax - input.BCETMin + 1);
                task.BCET -= task.BCET%10;
                task.WCET = task.BCET + rand.nextInt((int)task.BCET*input.CompTimeVariation/100 + 1);
                task.WCET -= task.WCET%10;
        		                
        		graph.TaskList.add(task);
        	}
        	taskIndex += taskCount[i];
        	
        	output.GraphList.add(graph);
        }
    }

    void CreatePEList()
    {
        Random rand = new Random();

        // guarantee at least 1 mapped task on each PE
        int[] mappingCount = new int[PECount];
        for (int i = 0; i < PECount; ++i)
            mappingCount[i] = 1;

        int remainCount = totalTaskCount - PECount;
        
        // balanced task mapping
        if (remainCount > 0)
        {
        	int divide = remainCount / PECount;
        	int remain = remainCount % PECount;
        	for (int i = 0; i < PECount; ++i)
        	{
        		mappingCount[i] += divide;
        		if (i < remain) mappingCount[i]++;
        	}
        }

        // assign tasks to PEs in increasing task index order
        int mapPE = 0;
        int[] mapping = new int[totalTaskCount];
        for (int i = 0; i < totalTaskCount; ++i)
        {
            mapping[i] = mapPE;
            if (--mappingCount[mapPE] == 0)
                ++mapPE;
        }
        // shuffle task mapping
        for (int i = 0; i < totalTaskCount; ++i)
        {
            int shuffleIndex = rand.nextInt(totalTaskCount);

            int temp = mapping[i];
            mapping[i] = mapping[shuffleIndex];
            mapping[shuffleIndex] = temp;
        }

        output.PEList = new ArrayList<Data.ProcessingElement>();
        for (int i = 0; i < PECount; ++i)
        {
        	Data.ProcessingElement pe = new Data.ProcessingElement();
        	pe.ID = i;
        	pe.Name = Integer.toString(pe.ID);
        	
        	if (input.PreemptiveMode == 0)
                pe.Preemptable = true;
            else if (input.PreemptiveMode == 1)
            	pe.Preemptable = false;
            else
            	pe.Preemptable = rand.nextInt(2) == 0;
        	
        	pe.CoreCount = input.CoreCountMin + rand.nextInt(input.CoreCountMax - input.CoreCountMin + 1);
        	
        	pe.MappingList = new ArrayList<Data.Mapping>();
        	
        	output.PEList.add(pe);
        }

        // remove the case that all PEs are same mode
        if (input.PreemptiveMode == 2)
        {
            boolean allSame = true;
            boolean beforeMode = output.PEList.get(0).Preemptable;
            for (int i = 1; i < PECount; ++i)
            {
                if (beforeMode != output.PEList.get(i).Preemptable)
                {
                    allSame = false;
                    break;
                }
            }

            if (allSame && PECount > 1)
            {
                int count = 1 + rand.nextInt(PECount - 1);
                for (int i = 0; i < count; ++i)
                {
                    int target = rand.nextInt(PECount);
                    output.PEList.get(target).Preemptable = !beforeMode;
                }
            }
        }

        for (int i = 0; i < totalTaskCount; ++i)
        {
            mappedPE[i] = mapping[i];
            preemptable[i] = output.PEList.get(mapping[i]).Preemptable;

            Data.ProcessingElement pe = output.PEList.get(mapping[i]);
            
            Data.Mapping map = new Data.Mapping();
            map.Task = Integer.toString(i);
            map.Replica = 0;
            pe.MappingList.add(map);
        }
    }

    void CreateDependency()
    {
        Random rand = new Random();
        dependencyMatrix = new boolean[totalTaskCount][totalTaskCount];
		SuccessorSet = new BitSet[totalTaskCount];
		for (int i = 0; i < totalTaskCount; ++i)
			SuccessorSet[i] = new BitSet(totalTaskCount);
		
        boolean[] connected = new boolean[totalTaskCount];
        for (int i = 0; i < totalTaskCount; ++i)
        {
            for (int j = 0; j < totalTaskCount; ++j)
                dependencyMatrix[i][j] = false;
            connected[i] = false;
        }
        
    	if (input.DependencyMode == 2)
    		return;
    	
        for (int i = 0; i < graphCount; ++i)
        {
        	Data.Graph graph = output.GraphList.get(i);
            int beginIndex = graph.TaskList.get(0).ID;
            int count = graph.TaskList.size();

            for (int j = 0; j < count - 1; ++j)
            {
            	if (input.DependencyMode == 0)
            	{
	                int edgeCount = Math.max(rand.nextInt(Math.max(1, (count - 1 - j))),
	                    connected[beginIndex + j] ? 0 : 1);
	                for (int k = 0; k < edgeCount; ++k)
	                {
                		int to = rand.nextInt(count - j - 1) + j + 1;
                		dependencyMatrix[beginIndex + j][beginIndex + to] = true;
                		connected[beginIndex + to] = true;
	                }
            	}
            	else
            	{
	                int to = j+1;
	                dependencyMatrix[beginIndex + j][beginIndex + to] = true;
	        		connected[beginIndex + to] = true;
            	}
            }

            if (count > 1 && !connected[beginIndex + count - 1])
            {
                int from = rand.nextInt(count - 1);
                dependencyMatrix[beginIndex + from][beginIndex + count - 1] = true;
                connected[beginIndex + count - 1] = true;
            }

            for (int j = beginIndex + count - 1; j >= beginIndex; --j)
            {
            	for (int k = beginIndex; k < beginIndex+count; ++k)
            	{
            		if (dependencyMatrix[j][k])
            		{
            			SuccessorSet[j].set(k);
            			SuccessorSet[j].or(SuccessorSet[k]);
            		}
            	}
            }

            for (int j = 0; j < count - 1; ++j)
            {
                for (int k = 0; k < totalTaskCount; ++k)
                {
                    if (!dependencyMatrix[beginIndex + j][k])
                        continue;

                    int to = k;

                    boolean removable = false;
                    for (int l = 0; l < totalTaskCount; ++l)
                    {
                        if (k == l)
                            continue;
                        if (!dependencyMatrix[beginIndex + j][l])
                            continue;

                        int from = l;
                        if (SuccessorSet[from].get(to))
                        {
                            removable = true;
                            break;
                        }
                    }

                    if (removable)
                        dependencyMatrix[beginIndex + j][k] = false;
                }
            }
            
            graph.DependencyList = new ArrayList<Data.Dependency>();
            for (int j = 0; j < count - 1; ++j)
            {
            	for (int k = 0; k < totalTaskCount; ++k)
            	{
            		if (dependencyMatrix[beginIndex + j][k])
            		{
            			Data.Dependency dep = new Data.Dependency();
            			dep.SourceID = beginIndex+j;
            			dep.DestinationID = k;
            			dep.Source = Integer.toString(beginIndex + j);
            			dep.Destination = Integer.toString(k);
            			
            			graph.DependencyList.add(dep);
            		}
            	}
            }
        }
    }

    ArrayList<ArrayList<Data.Task>> topologicalOrders;
    long[] startTimes;
    long[] wcrts;

    void SetPeriods()
    {
        TopologicalSorting();

        for (int i = 0; i < output.GraphList.size(); ++i)
        {
            Data.Graph graph = output.GraphList.get(i);
            graph.Activation.Period = WCRT(i, true);
            wcrts[graph.ID] = graph.Activation.Period;
        }

        RevicePeriods();

        for (int i = 0; i < graphCount; ++i)
        {
        	output.GraphList.get(i).Activation.Deadline = output.GraphList.get(i).Activation.Period;
        }
    }

    void SetJitters()
    {
        Random rand = new Random();
        for (int i = 0; i < output.GraphList.size(); ++i)
        {
            Data.Graph graph = output.GraphList.get(i);
            graph.Activation.Jitter = (int)(graph.Activation.Period * (double)(input.JitterRatioMin + rand.nextInt(input.JitterRatioMax - input.JitterRatioMin + 1)) / 1000);
        }
    }

    void RevicePeriods()
    {
        for (int i = 0; i < graphCount; ++i)
        {
            long period = output.GraphList.get(i).Activation.Period;

            int count = 0;
            boolean remain = false;
            while (period >= 100)
            {
                if (!remain &&
                    period % 10 != 0)
                    remain = true;

                period /= 10;
                ++count;
            }

            if (remain)
                period += 1;

            while (count > 0)
            {
                period *= 10;
                --count;
            }
            
            period*=periodRatio;

            output.GraphList.get(i).Activation.Period = period;
        }

        long[] periods = new long[graphCount];
        for (int i = 0; i < graphCount; ++i)
            periods[i] = output.GraphList.get(i).Activation.Period;

        long LCM = Misc.Math.LCM(periods);
        for (int i = 0; i < graphCount; ++i)
        {
            long period = output.GraphList.get(i).Activation.Period;

            long divide = LCM / period;
            while (divide > input.HyperPeriodMax)
            {
                int minMul = 2;
                while (divide % minMul != 0)
                    ++minMul;

                period *= minMul;
                divide = LCM / period;
            }

            output.GraphList.get(i).Activation.Period = period;
        }
    }

    void TopologicalSorting()
    {
        topologicalOrders = new ArrayList<ArrayList<Data.Task>>();
        startTimes = new long[totalTaskCount];
        wcrts = new long[graphCount];

        boolean[] scheduled = new boolean[totalTaskCount];
        for (int i = 0; i < totalTaskCount; ++i)
            scheduled[i] = false;

        Queue<Data.Task> queue = new LinkedList<Data.Task>();

        for (int i = 0; i < graphCount; ++i)
        {
        	ArrayList<Data.Task> topologicalOrder = 
        			new ArrayList<Data.Task>();
            topologicalOrders.add(topologicalOrder);

            queue.clear();
            Data.Graph graph = output.GraphList.get(i);
            for (int s1 = 0; s1 < graph.TaskList.size(); ++s1)
            {
            	Data.Task task = graph.TaskList.get(s1);
                boolean sourceNode = true;
                for (int j = 0; j < totalTaskCount; ++j)
                {
                    if (dependencyMatrix[j][task.ID])
                    {
                        sourceNode = false;
                        break;
                    }
                }

                if (sourceNode)
                {
                    scheduled[task.ID] = true;
                    queue.offer(task);
                }
            }

            while (queue.size() != 0)
            {
                Data.Task task = queue.poll();
                topologicalOrder.add(task);

                for (int j = 0; j < totalTaskCount; ++j)
                {
                    if (!dependencyMatrix[task.ID][j])
                        continue;

                    if (!scheduled[j])
                    {
                        Boolean schedulable = true;
                        for (int k = 0; k < totalTaskCount; ++k)
                        {
                            if (!dependencyMatrix[k][j])
                                continue;

                            if (!scheduled[k])
                            {
                                schedulable = false;
                                break;
                            }
                        }

                        if (schedulable)
                        {
                            scheduled[j] = true;
					        queue.offer(tasks[j]);
				        }
                    }
                }
	        }
        }
    }
    
    long WCRT(int graphID, boolean critical)
    {
        for (int i = 0; i < totalTaskCount; ++i)
            startTimes[i] = 0;

        ArrayList<Data.Task> topologicalOrder = topologicalOrders.get(graphID);
        long max = 0;
        for (int i = 0; i < topologicalOrder.size(); ++i)
        {
            Data.Task task = topologicalOrder.get(i);
            long r = WCET(task, critical);
            if (r < 0)
                return r;

            long endTime = startTimes[task.ID] + r;

            if (max < endTime)
                max = endTime;

            for (int j = 0; j < totalTaskCount; ++j)
            {
                if (!dependencyMatrix[task.ID][j])
                    continue;

                if (startTimes[j] < endTime)
                    startTimes[j] = endTime;
            }
        }

        return max;
    }

    long WCET(Data.Task task, boolean critical)
    {
        long x = task.WCET;
        
        if (critical)
            return x;

	    long gx = x;
        do
        {
            x = gx;
		
            long gsum = 0;
            for (int i = 0; i < totalTaskCount; ++i)
            {
                if (i == task.ID)
                    continue;

                if (mappedPE[i] != mappedPE[task.ID])
                    continue;

                if (SuccessorSet[i].get(task.ID) || SuccessorSet[task.ID].get(i))
                    continue;

                if (preemptable[task.ID])
                {
                    if (tasks[i].Priority > task.Priority)
                        continue;

                    long period = output.GraphList.get(parentTaskID[i]).Activation.Period;
                    long rDiff = period - tasks[i].WCET;
                    gsum += tasks[i].WCET *
                            (int)Math.ceil((double)(x + rDiff) / period);
                }
                else
                {
                    gsum += tasks[i].WCET;
                }
            }

		    gx = task.WCET + gsum;
	    }
	    while (x < gx);

	    return x;
    }
}

