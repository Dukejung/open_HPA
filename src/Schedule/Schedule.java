package Schedule;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

public class Schedule {
	private final Data.Description data;
	
	private int taskCount;
	private int totalTaskCount;
	private Graph[] graphList;
	private ProcessingElement[] PEList;
	private Task[] totalTaskList;
	private ArrayList<ArrayList<Task>> taskInstanceLists;
	
	private ArrayList<Task> scheduleOrder;
	
	public int getPECount()								{ return PEList.length; }
	public ProcessingElement getPE(int index)			{ return PEList[index]; }
	
	public int getGraphCount()							{ return graphList.length; }
	public Graph getGraph(int index)					{ return graphList[index]; }
	
	public int getTaskCount()							{ return taskCount; }
	public int getTotalTaskCount()						{ return totalTaskCount; }
	
	public Task getTask(int instanceID)					{ return totalTaskList[instanceID]; }
	public ArrayList<Task> getTasksWithID(int taskID)	{ return taskInstanceLists.get(taskID); }
	
	public int getSharedResourceCount()					{ return data.SRcount; }
	
	public Iterator<Task> getScheduleOrderIterator()	{ return scheduleOrder.iterator(); }
	
	public long getWCRT(int graphID)					{ return graphList[graphID].getWCRT(); }
	
	public Schedule(Data.Description data)
	{
		this.data = data;
		this.taskCount = 0;
		this.totalTaskCount = 0;
		this.graphList = new Graph[data.GraphList.size()];
		this.PEList = new ProcessingElement[data.PEList.size()];
		
		this.scheduleOrder = new ArrayList<Task>();
		
		for (int i = 0; i < data.PEList.size(); ++i)
			PEList[i] = new ProcessingElement(data.PEList.get(i));
		for (int i = 0; i < data.GraphList.size(); ++i)
		{
			graphList[i] = new Graph(data.GraphList.get(i));
			addGraphInstance(i);
		}
		taskCount = totalTaskCount;
		
		taskInstanceLists = new ArrayList<ArrayList<Task>>(taskCount);
		for (int i = 0; i < taskCount; ++i)
		{
			taskInstanceLists.add(new ArrayList<Task>());
		}
	}
	
	public void addGraphInstance(int graphID)
	{
		Graph.Instance instance = graphList[graphID].createNextInstance(totalTaskCount);
		totalTaskCount += instance.getTaskCount();
		
		mapTasksToPE(instance);
	}
	
	public void restructure()
	{
		for (ProcessingElement pe : PEList)
			pe.reset(totalTaskCount);
		
		for (Graph graph : graphList)
		{
			Iterator<Graph.Instance> it = graph.getInstanceIterator();
			while (it.hasNext()) it.next().reset(totalTaskCount);
		}
		
		if (totalTaskList == null || totalTaskList.length != totalTaskCount)
			totalTaskList = new Task[totalTaskCount];
		for (int i = 0; i < taskCount; ++i)
		{
			taskInstanceLists.get(i).clear();
		}
		
		for (Graph graph : graphList)
		{
			for (Iterator<Task> it = graph.getTaskIterator(); it.hasNext();)
			{
				Task task = it.next();
				totalTaskList[task.getInstanceID()] = task;
				taskInstanceLists.get(task.getID()).add(task);
			}
		}
		
		updateSuccessorSet();
		updateScanOrder();
	}
	
	public void print()
	{
		System.out.println("Proc size : " + this.getPECount());
		
		for (ProcessingElement pe : PEList)
		{
			System.out.println("Proc[" + pe.getID() + "] ");
		
			for (Iterator<Task> it = pe.getTaskIterator(); it.hasNext();)
			{	
				Task s = it.next();
				
				System.out.println(
						"Graph(" + s.getOwnerInstance().getGraph().getID() + ",\t" + s.getOwnerInstance().getInstanceID() + ")\t" +
						"Task(" + s.getID() + ",\t" + s.getInstanceID() + ")\t" +
						"PR[" + s.getPriority() + "]\t" +
						"RB[" + s.getReleaseMin() +",\t" + s.getReleaseMax() + "]\t" +
						"SB[" + s.getStartMin() + ",\t" + s.getStartMax() + "]\t" +				
						"FB[" + s.getFinishMin()+",\t" + s.getFinishMax() + "]\t" +
						"C[" + s.getBCET() + ",\t" + s.getWCET() + "]\t");
			}
		}
	}
	
	private void mapTasksToPE(Graph.Instance instance)
	{
		Iterator<Task> it = instance.getTaskIterator();
		while (it.hasNext())
		{
			Task task = it.next();
			task.mappedPE = PEList[task.data.MappedPE];
			PEList[task.data.MappedPE].mappedTaskList.add(task);
		}
	}
	
	private void updateSuccessorSet()
	{
		int[] visitableCount = new int[totalTaskCount];
		Queue<Task> visitQueue = new LinkedList<Task>();
		
		for (Graph graph : graphList)
		{
			for (Iterator<Task> it = graph.getTaskIterator(); it.hasNext();)
			{
				Task task = it.next();
				visitableCount[task.getInstanceID()] = task.getChildCount();
				if (task.getChildCount() == 0) visitQueue.offer(task);
			}
		}

		while (!visitQueue.isEmpty())
		{
			Task child = visitQueue.poll();
			for (Iterator<Task> it = child.getParentIterator(); it.hasNext();)
			{
				Task parent = it.next();
				parent.successorSet.or(child.successorSet);
				parent.successorSet.set(child.getInstanceID());
				
				if (--visitableCount[parent.getInstanceID()] == 0)
					visitQueue.offer(parent);
			}
		}
	}
	
	private void updateScanOrder()
	{
		if (scheduleOrder == null || scheduleOrder.size() != totalTaskCount)
			scheduleOrder = new ArrayList<Task>();
		
		int[] visitableCount = new int[totalTaskCount];
		PriorityQueue<Task> visitQueue = new PriorityQueue<Task>(1, new java.util.Comparator<Task>() {
			@Override public int compare(Task lhs, Task rhs) { return lhs.hasHigherPriorityThan(rhs) ? -1 : 1; }
		});
		
		for (Graph graph : graphList)
		{
			for (Iterator<Task> it = graph.getTaskIterator(); it.hasNext();)
			{
				Task task = it.next();
				visitableCount[task.getInstanceID()] = task.getParentCount();
				if (task.getParentCount() == 0) visitQueue.offer(task);
			}
		}
		
		while (!visitQueue.isEmpty())
		{
			Task task = visitQueue.poll();
			scheduleOrder.add(task);
			
			for (Iterator<Task> it = task.getChildIterator(); it.hasNext();)
			{
				Task child = it.next();
				if (--visitableCount[child.getInstanceID()] == 0)
					visitQueue.offer(child);
			}
		}
	}
}
