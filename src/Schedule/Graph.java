package Schedule;

import java.util.ArrayList;
import java.util.Iterator;

public class Graph {
	public class Instance {
		protected final Graph ownerGraph;
		
		private int instanceID;
		private Task[] ownedTaskList;

		private boolean deadlineViolated = false;
		
		public Graph getGraph()							{ return ownerGraph; }
		public int getInstanceID()						{ return instanceID; }
		
		public void violateDeadline() 					{ deadlineViolated = true; }
		public boolean isDeadlineViolated() 			{ return deadlineViolated; }
		
		public long getReleaseMin() { return getGraph().getStartOffsetMin() + instanceID*getGraph().getPeriod(); }
		public long getReleaseMax() { return getGraph().getStartOffsetMax() + instanceID*getGraph().getPeriod() + getGraph().getJitter(); }

		public int getTaskCount()						{ return ownedTaskList.length; }
		public Iterator<Task> getTaskIterator() {
			Iterator<Task> it = new Iterator<Task> () {
				private int index = 0;
				
				@Override public boolean hasNext() { return index < ownedTaskList.length; }
				@Override public Task next() { return ownedTaskList[index++]; }
				@Override public void remove() { throw new UnsupportedOperationException(); }
			};
			return it;
		}
		
		protected Instance(Graph graph, int instanceID, int taskInstanceIDBegin)
		{
			this.ownerGraph = graph;
			this.instanceID = instanceID;
			this.ownedTaskList = new Task[graph.data.TaskList.size()];
			
			for (int i = 0; i < graph.data.TaskList.size(); ++i)
			{
				Task task = new Task(graph.data.TaskList.get(i), taskInstanceIDBegin + i);
				task.ownerInstance = this;
				ownedTaskList[i] = task;
			}
			for (Data.Dependency dependency : graph.data.DependencyList)
			{
				Task source = null;
				Task destination = null;
				for (Task task : ownedTaskList)
				{
					if (task.getID() == dependency.SourceID)
						source = task;
					if (task.getID() == dependency.DestinationID)
						destination = task;
					if (source != null && destination != null)
						break;
				}
				if (source == null || destination == null)
					continue;
				
				source.childTasks.add(destination);
				destination.parentTasks.add(source);
			}
		}
		
		protected void reset(int totalTaskCount)
		{
			deadlineViolated = false;
			
			for (Task task : ownedTaskList)
				task.reset(totalTaskCount);
		}
	}
	
	protected final Data.Graph data;
	
	private ArrayList<Instance> instanceList;
	private int totalTaskCount;
	
	private long startOffsetMin;
	private long startOffsetMax;
	
	public int getID()								{ return data.ID; }
	public String getName()							{ return data.Name; }
	public long getPeriod() 						{ return data.Activation.Period; }
	public long getJitter()							{ return data.Activation.Jitter; }
	public long getDistance()						{ return data.Activation.Distance; }
	public long getDeadline() 						{ return data.Activation.Deadline; }

	public void setStartOffsetMin(long value)		{ startOffsetMin = value; }
	public void setStartOffsetMax(long value)		{ startOffsetMax = value; }
	public long getStartOffsetMin()					{ return startOffsetMin; }
	public long getStartOffsetMax()					{ return startOffsetMax; }
	
	public int getInstanceCount()					{ return instanceList.size(); }
	public Iterator<Instance> getInstanceIterator() { return instanceList.iterator(); }
	
	public int getTotalTaskCount()					{ return totalTaskCount; }
	public Iterator<Task> getTaskIterator() {
		Iterator<Task> it = new Iterator<Task>() {
			private int instanceIndex = 0;
			private int taskIndex = 0;
			
			@Override public boolean hasNext() { return instanceIndex < instanceList.size(); }
			@Override public Task next() {
				Task result = instanceList.get(instanceIndex).ownedTaskList[taskIndex++];
				if (taskIndex == data.TaskList.size()) { ++instanceIndex; taskIndex = 0; }
				return result;
				}
			@Override public void remove() { throw new UnsupportedOperationException(); }
		};
		return it;
	}
	
	protected Graph(Data.Graph data)
	{
		this.data = data;
		this.instanceList = new ArrayList<Instance>();
		this.totalTaskCount = 0;
		
		this.startOffsetMin = data.Activation.StartOffset;
		this.startOffsetMax = data.Activation.StartOffset;
	}
	
	protected void reset(int totalTaskCount)
	{
		for (Instance instance : instanceList)
			instance.reset(totalTaskCount);
	}
	
	protected Instance createNextInstance(int taskInstanceIDBegin)
	{
		Instance newInstance = new Instance(this, instanceList.size(), taskInstanceIDBegin);
		instanceList.add(newInstance);
		totalTaskCount += newInstance.getTaskCount();

		return newInstance;
	}
	
	protected long getWCRT()
	{
		long WCRT = 0;
		for (Graph.Instance instance : instanceList)
		{	
			long maxEnd = 0;
			for (Iterator<Task> it = instance.getTaskIterator(); it.hasNext();)
				maxEnd = Math.max(maxEnd, it.next().getFinishMax());
			
			if (WCRT < maxEnd - instance.getReleaseMin())
				WCRT = maxEnd - instance.getReleaseMin();
		}
		
		return WCRT;
	}
}
