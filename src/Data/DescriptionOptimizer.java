package Data;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.Queue;

public class DescriptionOptimizer {
	private class TaskInfo
	{
		public int graphID;
		public ArrayList<Task> predecessorList;
		public ArrayList<Task> successorList;
	}
	
	private Description desc;

	private int totalTaskCount;
	private Task[] taskList;
	private TaskInfo[] taskInfoList;

	private int[] groupIDs;
	private int groupCount;
	
	public DescriptionOptimizer(Description desc)
	{
		this.desc = desc.clone();
	}

    public Description get() 
	{
    	initialize();
    	
		groupTasks();
		orderTasksPerGroup();
		minimizeDependency();
		
		updateDescription();
		
		return desc;
	}
    
    private void initialize()
    {
		totalTaskCount = 0;
		for (Graph graph : desc.GraphList)
			totalTaskCount += graph.TaskList.size();
		
		taskList = new Task[totalTaskCount];
		taskInfoList = new TaskInfo[totalTaskCount];
		for (Graph graph : desc.GraphList)
		{
			for (Task task : graph.TaskList)
			{
				taskList[task.ID] = task;
				taskInfoList[task.ID] = new TaskInfo(); 
				taskInfoList[task.ID].graphID = graph.ID;
			}
		}		
		for (int i = 0; i < totalTaskCount; ++i)
		{
			taskInfoList[i].predecessorList = new ArrayList<Task>();
			taskInfoList[i].successorList = new ArrayList<Task>();
		}
		for (Graph graph : desc.GraphList)
		{
			for (Dependency dependency : graph.DependencyList)
			{
				Task source = taskList[dependency.SourceID];
				Task dest = taskList[dependency.DestinationID];
				
				taskInfoList[source.ID].successorList.add(dest);
				taskInfoList[dest.ID].predecessorList.add(source);
			}
		}
		
		groupIDs = new int[totalTaskCount];
    }
    
	private void groupTasks()
	{
		groupCount = 0;
		int nonGroupTaskCount = totalTaskCount;

		for (int i = 0; i < totalTaskCount; ++i)
			groupIDs[i] = -1;

		while (nonGroupTaskCount > 0)
		{
			Task target = findGroupSource();
			Graph graph = desc.GraphList.get(taskInfoList[target.ID].graphID);

			Queue<Task> visitQueue = new LinkedList<Task>();
			for (Task task : graph.TaskList)
			{
				if (groupIDs[task.ID] == -1 && task.MappedPE == target.MappedPE && 
					checkSamePredecessorList(task, target))
					visitQueue.offer(task);
			}
			
			int currentGroupID = groupCount;
			while (!visitQueue.isEmpty())
			{
				Task task = visitQueue.poll();
				groupIDs[task.ID] = currentGroupID;
				--nonGroupTaskCount;
				
				for (Task successor : taskInfoList[task.ID].successorList)
				{
					if (successor.MappedPE == task.MappedPE && 
						checkAllPredecessorsInGroup(successor, target, currentGroupID))
						visitQueue.offer(successor);
				}
			}
			++groupCount;
		}
	}
	
	private void orderTasksPerGroup()
	{
		boolean[] visited = new boolean[totalTaskCount];
		for (int i = 0; i < totalTaskCount; ++i)
			visited[i] = false;

		for (int groupID = 0; groupID < groupCount; ++groupID)
		{
			ArrayList<Task> groupTaskList = new ArrayList<Task>();			
			for (int taskID = 0; taskID < totalTaskCount; ++taskID)
			{
				if (groupIDs[taskID] == groupID)
					groupTaskList.add(taskList[taskID]);
			}

			ArrayList<Task> scheduleOrder = new ArrayList<Task>();
			while (scheduleOrder.size() < groupTaskList.size())
			{
				Task target = null;
				for (Task task : groupTaskList)
				{
					if (!visited[task.ID] && checkAllPredecessorsVisited(task, visited) &&
						(target == null || task.Priority < target.Priority))
							target = task;
				}
				visited[target.ID] = true;
				scheduleOrder.add(target);
			}
			for (int i = 1; i < scheduleOrder.size(); ++i)
			{
				Task source = scheduleOrder.get(i-1);
				Task dest = scheduleOrder.get(i);
				if (!taskInfoList[source.ID].successorList.contains(dest))
					taskInfoList[source.ID].successorList.add(dest);
				if (!taskInfoList[dest.ID].predecessorList.contains(source))
					taskInfoList[dest.ID].predecessorList.add(source);
			}
		}
	}
	
	private void minimizeDependency()
	{
		BitSet[] successorSet = createSuccessorSet();
		for (Task task : taskList)
		{
			ArrayList<Task> successorList = new ArrayList<Task>();
			for (Task successor : taskInfoList[task.ID].successorList)
				successorList.add(successor);
			
			for (Task successor : successorList)
			{
				if (checkAnotherPathToSuccessor(task, successor, successorSet))
				{
					taskInfoList[task.ID].successorList.remove(successor);
					taskInfoList[successor.ID].predecessorList.remove(task);
				}
			}
		}
	}
	
	private void updateDescription()
	{
		for (Graph graph : desc.GraphList)
			graph.DependencyList.clear();
		
		for (int taskID = 0; taskID < totalTaskCount; ++taskID)
		{
			TaskInfo sourceTaskInfo = taskInfoList[taskID];
			
			Task source = taskList[taskID];
			Graph graph = desc.GraphList.get(sourceTaskInfo.graphID);
			for (Task dest : sourceTaskInfo.successorList)
			{
				Dependency dependency = new Dependency();
				dependency.Source = source.Name;
				dependency.Destination = dest.Name;
				dependency.SourceID = source.ID; 
				dependency.DestinationID = dest.ID;
				
				graph.DependencyList.add(dependency);
			}
		}
	}
	
	private Task findGroupSource()
	{
		for (int taskID = 0; taskID < totalTaskCount; ++taskID)
		{
			if (groupIDs[taskID] >= 0)
				continue;
			
			Task task = taskList[taskID];
			if (checkAllPredecessorsGrouped(task))
			{
				return task;
			}
		}
		return null;		
	}
	
	private BitSet[] createSuccessorSet()
	{
		BitSet[] SuccessorSet = new BitSet[totalTaskCount];
		
		int[] readyCount = new int[totalTaskCount];
		Queue<Task> visitQueue = new LinkedList<Task>();
		for (int taskID = 0; taskID < totalTaskCount; ++taskID)
		{
			SuccessorSet[taskID] = new BitSet(totalTaskCount);
			readyCount[taskID] = taskInfoList[taskID].successorList.size();
			
			if (readyCount[taskID] == 0)
				visitQueue.offer(taskList[taskID]);
		}
		
		while (!visitQueue.isEmpty())
		{
			Task task = visitQueue.poll();
			for (Task predecessor : taskInfoList[task.ID].predecessorList)
			{
				SuccessorSet[predecessor.ID].or(SuccessorSet[task.ID]);
				SuccessorSet[predecessor.ID].set(task.ID);
				
				--readyCount[predecessor.ID];
				if (readyCount[predecessor.ID] == 0)
					visitQueue.offer(predecessor);
			}
		}
		return SuccessorSet;
	}
	
	private boolean checkAllPredecessorsGrouped(Task task)
	{
		for (Task predecessor : taskInfoList[task.ID].predecessorList)
		{
			if (groupIDs[predecessor.ID] < 0)
				return false;
		}
		return true;
	}
	
	private boolean checkAllPredecessorsInGroup(Task task, Task target, int groupID)
	{
		for (Task predecessor : taskInfoList[task.ID].predecessorList)
		{
			if (groupIDs[predecessor.ID] != groupID && 
				!taskInfoList[target.ID].predecessorList.contains(predecessor))
				return false;
		}
		return true;
	}
	
	private boolean checkSamePredecessorList(Task lhs, Task rhs)
	{
		if (taskInfoList[lhs.ID].predecessorList.size() != taskInfoList[rhs.ID].predecessorList.size())
			return false;
		
		for (Task predecessor : taskInfoList[lhs.ID].predecessorList)
		{
			if (!taskInfoList[rhs.ID].predecessorList.contains(predecessor))
				return false;
		}
		return true;
	}

	private boolean checkAllPredecessorsVisited(Task task, boolean[] visited)
	{
		for (Task predecessor : taskInfoList[task.ID].predecessorList)
		{
			if (!visited[predecessor.ID])
				return false;
		}
		return true;
	}
		
	private boolean checkAnotherPathToSuccessor(Task task, Task successor, BitSet[] successorSet)
	{
		for (Task otherSuccessor : taskInfoList[task.ID].successorList)
		{
			if (successor != otherSuccessor &&
				successorSet[otherSuccessor.ID].get(successor.ID))
			{
				return true;
			}
		}
		return false;
	}
}
