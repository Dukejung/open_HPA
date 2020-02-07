package Schedule;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;

import Data.Description;

public class Task {
	public enum HardeningType {REEXECUTION, ACTIVEREPLICA, PASSIVEREPLICA, NONE};
	
	protected final Data.Task data;

	private final int instanceID;
	
	protected Graph.Instance ownerInstance;
	protected ProcessingElement mappedPE;

	protected ArrayList<Task> parentTasks;
	protected ArrayList<Task> childTasks;
	protected BitSet successorSet;
	
	private long releaseMin;
	private long releaseMax;
	private long startMin;
	private long startMax;
	private long finishMin;
	private long finishMax;

	private BitSet bestPreemptorSet;
	private BitSet worstPreemptorSet;
	private BitSet excludeSet;
	
	private boolean isScheduled;
	private boolean isVisited;

	public Graph.Instance getOwnerInstance()			{ return ownerInstance; }
	public ProcessingElement getMappedPE() 				{ return mappedPE; }

	public int getInstanceID()							{ return instanceID; }
	
	public int getID() 									{ return data.ID; }
	public String getName()								{ return data.Name; }
	public int getPriority() 							{ return data.Priority; }
	public long getBCET() 								{ return data.BCET; }
	public long getWCET() 								{ return data.WCET; }

	public long getClusteredTasksMaxExecTime() { return data.ClusteredTasksMaxExecTime; }


	public boolean isSource() 							{ return parentTasks.isEmpty() ||
																 (parentTasks.size() == 1 &&
																  parentTasks.get(0).getID() == getID()); }
	public boolean isSink() 							{ return childTasks.isEmpty() ||
																 (childTasks.size() == 1 &&
																  childTasks.get(0).getID() == getID()); }
	
	public int getParentCount()							{ return parentTasks.size(); }
	public int getChildCount()							{ return childTasks.size(); }
	public Iterator<Task> getParentIterator() 			{ return parentTasks.iterator(); }
	public Iterator<Task> getChildIterator() 			{ return childTasks.iterator(); }
	
	public long getReleaseMin() 						{ return releaseMin; }
	public long getReleaseMax() 						{ return releaseMax; }
	public long getStartMin() 							{ return startMin; }
	public long getStartMax() 							{ return startMax; }
	public long getFinishMin() 							{ return finishMin; }
	public long getFinishMax() 							{ return finishMax; }
	
	public void setReleaseMin(long releaseMin) 			{ this.releaseMin = releaseMin; }
	public void setReleaseMax(long releaseMax) 			{ this.releaseMax = releaseMax; }
	public void setStartMin(long startMin) 				{ this.startMin = startMin; }
	public void setStartMax(long startMax) 				{ this.startMax = startMax; }
	public void setFinishMin(long finishMin) 			{ this.finishMin = finishMin; }
	public void setFinishMax(long finishMax) 			{ this.finishMax = finishMax; }
	
	public BitSet getBestPreemptorSet()					{ return this.bestPreemptorSet; }
	public BitSet getWorstPreemptorSet()				{ return this.worstPreemptorSet; }
	public BitSet getExcludeSet()						{ return this.excludeSet; }
	
	public boolean isScheduled()						{ return isScheduled; }
	public void setScheduled(boolean scheduled)			{ this.isScheduled = scheduled; }

	public boolean isVisited()							{ return isVisited; }
	public void setVisited(boolean visited)				{ this.isVisited = visited;	}
		
	public boolean isChildOf(Task task)
	{
		for (Task parent : parentTasks)
			if (task == parent)
				return true;
		return false;
	}
	
	public boolean isParentOf(Task task)
	{
		for (Task child : childTasks)
			if (task == child)
				return true;
		return false;
	}
	
	public boolean isAncestorOf(Task task)
	{
		return this.successorSet.get(task.getInstanceID());
	}
	
	public boolean isDescendentOf(Task task)
	{
		return task.successorSet.get(this.getInstanceID());
	}
	
	public boolean hasHigherPriorityThan(Task task)
	{
		return getPriority() < task.getPriority() ||
			(getID() == task.getID() &&
			ownerInstance.getInstanceID() < task.getOwnerInstance().getInstanceID());
	}
	
	public boolean areAllParentsInSamePE()
	{
		for (Task parent : parentTasks)
			if (parent.getMappedPE() != getMappedPE())
				return false;
		return true;
	}
	
	public Data.ResourceAccessPattern getResourceAccessPattern(int resourceID)
	{
		for (int i = 0; i < data.SRAccessPatternList.size(); ++i)
		{
			Data.ResourceAccessPattern pattern = data.SRAccessPatternList.get(i);
			if (pattern.ID == resourceID)
				return pattern;
		}
		return null;
	}
	
	public void initExclusion()
	{
		excludeSet.clear();
		excludeSet.or(successorSet);
		getMappedPE().filterUnmappedTasks(excludeSet);
	}
	
	protected Task(Data.Task data, int instanceID)
	{
		this.data = data;
		this.instanceID = instanceID;
		
		parentTasks = new ArrayList<Task>();
		childTasks = new ArrayList<Task>();
	}
	
	protected void reset(int totalTaskCount)
	{
		releaseMin = Long.MAX_VALUE;
		releaseMax = 0;
		startMin = Long.MAX_VALUE;
		startMax = 0;
		finishMin = Long.MAX_VALUE;
		finishMax = 0;

		isScheduled = false;
		isVisited = false;
		
		if (bestPreemptorSet == null || bestPreemptorSet.length() != totalTaskCount)
			bestPreemptorSet = new BitSet(totalTaskCount);
		if (worstPreemptorSet == null || worstPreemptorSet.length() != totalTaskCount)
			worstPreemptorSet = new BitSet(totalTaskCount);
		if (successorSet == null || successorSet.length() != totalTaskCount)
			successorSet = new BitSet(totalTaskCount);
		if (excludeSet == null || excludeSet.length() != totalTaskCount)
			excludeSet = new BitSet(totalTaskCount);
		
		bestPreemptorSet.clear();
		worstPreemptorSet.clear();
		successorSet.clear();
		excludeSet.clear();
	}
}
