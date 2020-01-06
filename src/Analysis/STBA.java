package Analysis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import Schedule.*;

public class STBA
{	
	private Data.Description desc;
	private Schedule schedule;

	private long[] WCRTs;
	private int targetGraphID;
	private boolean changed;

	public STBA(Data.Description desc) 
	{
		this.desc = desc;

		WCRTs = new long[schedule.getGraphCount()];
	}

	public boolean go()
	{
		boolean error = false;
		long startTime = System.currentTimeMillis();
		
		for(int targetGraphID = 0; targetGraphID < schedule.getGraphCount(); ++targetGraphID)
		{
			WCRTs[targetGraphID] = Long.MAX_VALUE;

			schedule = new Schedule(desc);
			
			long maxPeriod = 0;
			for (int i = 0; i < schedule.getGraphCount(); ++i)
				maxPeriod = Math.max(maxPeriod, schedule.getGraph(i).getPeriod());
			
			// graphs expansion : decide number of instances overlapping target graph's execution
			// initially target graph has one graph instance
			Graph targetGraph = schedule.getGraph(targetGraphID);
			targetGraph.setStartOffsetMin(maxPeriod);
			targetGraph.setStartOffsetMax(maxPeriod);
			
			for(int i = 0; i < schedule.getGraphCount(); ++i)		
			{
				if(i == targetGraphID)
					continue;
				
				Graph graph = schedule.getGraph(i);
				graph.setStartOffsetMin(maxPeriod - graph.getPeriod());
				graph.setStartOffsetMax(maxPeriod);
				
				int numInstances = (int)Math.ceil((double)targetGraph.getPeriod() / graph.getPeriod())+1;
				while (numInstances > graph.getInstanceCount())
					schedule.addGraphInstance(i);
			}			
			
			long WCRT = 0;
			
			boolean expanded = true;
			do
			{	
				expanded = false;

				error = Estimation();
				if (error)
					break;
				
				WCRT = schedule.getWCRT(targetGraphID);
				
				if (WCRT > targetGraph.getDeadline())
					break;
				
				int numInstances = (int)Math.ceil((double)WCRT / targetGraph.getPeriod());
				if (numInstances < targetGraph.getInstanceCount())
				{
					expanded = true;

					while (numInstances > targetGraph.getInstanceCount())
						schedule.addGraphInstance(targetGraphID);

					double targetLength = numInstances * targetGraph.getPeriod();
					for (int i = 0; i < schedule.getGraphCount(); ++i)
					{
						if (i == targetGraphID)
							continue;
						
						Graph graph = schedule.getGraph(i);
						
						numInstances = (int)Math.ceil(targetLength / graph.getPeriod())+1;
						while (numInstances > graph.getInstanceCount())
							schedule.addGraphInstance(i);
					}
				}
			}
			while(expanded);

			if (error)
				break;
			
			WCRTs[targetGraphID] = WCRT;
		}

		long endTime = System.currentTimeMillis();
		
		return !error;
	}
	
	private static int MAX_COUNT = 200000;
	
	private boolean Estimation()
	{
		Initialize();
		
		InitExclusion();
		
		int count = 0;
		do
		{
			changed = false;
			
			BoundScheduling();
			
			if (!CheckDeadlineViolation())
				return false;
			
			UpdateExclusion();
			
			//schedule.print();
			
			++count;
		}
		while(changed && count < MAX_COUNT);	
		
		//schedule.print();
		
		return count == MAX_COUNT || !checkError();
	}
	
	private void Initialize()
	{
		schedule.restructure();

//		for (int i = 0; i < desc.totalTaskList.size(); ++i)
//		{
//			Task task = desc.totalTaskList.get(i);
//			task.reset();
//			
//			if (task.isItSource())
//			{
//				TaskGroup parent = task.getParentTaskGroup();
//				TaskGroupSet parentSet = parent.getTaskGroupSet();
//				
//				double minRelease, maxRelease;
//
//				if (Param.TASK_GRAPH_EXPANSION_TYPE == Param.TaskGraphExpType.NonHyperPeriod)
//				{	/* dynamic offset */
//					minRelease = parentSet.getStartOffsetMin() + task.getOffset();
//					maxRelease = parentSet.getStartOffsetMax() + task.getOffset() + task.getJitter();
//				}
//				else
//				{
//					minRelease = parentSet.getStartOffset() + parent.getPeriod()*parent.getInstanceID();
//					maxRelease = parentSet.getStartOffset() + parent.getPeriod()*parent.getInstanceID() + task.getJitter();
//				}
//				
//				task.setInitialReleaseMin((int)minRelease);
//				task.setInitialReleaseMax((int)maxRelease);
//			}
//		}
	}
	
	private void InitExclusion()
	{
		for (int i = 0; i < schedule.getTotalTaskCount(); ++i)
			schedule.getTask(i).initExclusion();
	}
	
	private void UpdateExclusion()
	{
		InheritPreemptionSet();
		
		int[] exclusionCount = new int[schedule.getTotalTaskCount()];		
		for (int i = 0; i < schedule.getTotalTaskCount(); ++i)
		{
			schedule.getTask(i).initExclusion();
			exclusionCount[i] = 0;
		}
		
		Object[] exclusionDependencies = new Object[schedule.getTotalTaskCount()];
		for (int i = 0; i < schedule.getTotalTaskCount(); ++i)
		{
			Task task_i = schedule.getTask(i);
			ArrayList<Task> exclusionDependency = new ArrayList<Task>();
			for (int j = 0; j < schedule.getTotalTaskCount(); ++j)
			{
				Task task_j = schedule.getTask(j);
				if (task_i.getBestPreemptorSet().get(task_j.getInstanceID()))
				{
					task_j.getExcludeSet().set(task_i.getInstanceID());
					exclusionDependency.add(task_j);
					++exclusionCount[task_j.getInstanceID()];
				}
			}
			exclusionDependencies[task_i.getInstanceID()] = exclusionDependency;
		}
		
		Queue<Task> visitQueue = new LinkedList<Task>();
		for (int i = 0; i < schedule.getTotalTaskCount(); ++i)
		{
			Task task = schedule.getTask(i);
			task.setVisited(false);
			
			if (exclusionCount[task.getInstanceID()] == 0)
				visitQueue.offer(task);
		}
		
		while (!visitQueue.isEmpty())
		{
			Task task = visitQueue.poll();
			task.setVisited(true);
			
			ArrayList<Task> exclusionDependency = 
					(ArrayList<Task>)exclusionDependencies[task.getInstanceID()];
			for (Task excludeInheritor : exclusionDependency)
			{
				excludeInheritor.getExcludeSet().or(task.getExcludeSet());
				
				if (!excludeInheritor.isVisited() &&
					--exclusionCount[excludeInheritor.getInstanceID()] == 0)
					visitQueue.add(excludeInheritor);
			}
		}
	}
	
	private void InheritPreemptionSet()
	{
		int[] visitableCount = new int[schedule.getTotalTaskCount()];
		Queue<Task> visitQueue = new LinkedList<Task>();
		for (int i = 0; i < schedule.getTotalTaskCount(); ++i)
		{
			Task task = schedule.getTask(i);
			visitableCount[task.getInstanceID()] = task.getParentCount();
			if (task.getParentCount() == 0)
				visitQueue.add(task);
		}
		
		while (!visitQueue.isEmpty())
		{
			Task task = visitQueue.poll();
			for (Iterator<Task> it = task.getChildIterator(); it.hasNext();)
			{
				Task child = it.next();
				child.getBestPreemptorSet().or(task.getBestPreemptorSet());
				child.getWorstPreemptorSet().or(task.getWorstPreemptorSet());
				
				if (--visitableCount[child.getInstanceID()] == 0)
					visitQueue.offer(child);
			}
		}
		
		for (int i = 0; i < schedule.getTotalTaskCount(); ++i)
		{
			Task task = schedule.getTask(i);
			task.getMappedPE().filterUnmappedTasks(task.getBestPreemptorSet());
			task.getMappedPE().filterUnmappedTasks(task.getWorstPreemptorSet());
		}
	}
	
	private void BoundScheduling()
	{		
		LinkedList<Task> waitList = new LinkedList<Task>();
		LinkedList<Task> iterateOrder = new LinkedList<Task>();
		
		do
		{
			waitList.clear();
			iterateOrder.clear();
			
			int waitCount = 0;
			int iterateCount = 0;
			for (Iterator<Task> it = schedule.getScheduleOrderIterator(); it.hasNext();)
			{
				iterateOrder.add(it.next());
				++iterateCount;
			}
			
			for (; iterateCount > 0; --iterateCount,
				waitCount = Math.max(0, waitCount - 1))
			{
				Task task = iterateOrder.removeFirst();
				
				if (task.isScheduled())
				{
					boolean violated = false;
					for (Iterator<Graph.Instance> it = 
						schedule.getGraph(targetGraphID).getInstanceIterator(); it.hasNext();)
					{
						Graph.Instance instance = it.next();
						if (instance.isDeadlineViolated())
						{
							violated = true;
							break;
						}
					}
					if (violated)
						continue;
				}
				
				if (!ComputeMin(task) || !ComputeMax(task))
				{
					waitList.addLast(task);
					continue;
				}
				
				task.setScheduled(true);
				
				if (waitCount == 0 && !waitList.isEmpty())
				{
					iterateCount = iterateCount + waitList.size();
					waitCount = waitList.size() + 1;
					
					iterateOrder.addAll(0, waitList);
					waitList.clear();
				}
			}
		}
		while (!waitList.isEmpty());
	}
	
	private boolean ComputeMin(Task target)
	{
		boolean _changed = false;
		Task previousPreemptor = null;
		
		target.getBestPreemptorSet().clear();
		
		// release time
		long releaseTime = target.getOwnerInstance().getReleaseMin();
		if (target.getParentCount() > 0)
		{	
			if (!target.isSource())
				releaseTime = -1;
		
			for (Iterator<Task> it = target.getParentIterator(); it.hasNext();)
			{
				Task parent = it.next();
				if (!parent.isScheduled())
					return false;
				
				if (releaseTime < parent.getFinishMin())
				{
					releaseTime = parent.getFinishMin();
					previousPreemptor = parent;
				}
			}
		}
		target.setReleaseMin(releaseTime);
		
		if (previousPreemptor == null)
			previousPreemptor = target;
		
		// start time
		long startTime = releaseTime;
		do
		{
			_changed = false;
			
			for (Iterator<Task> it = target.getMappedPE().getTaskIterator(); it.hasNext();)
			{
				Task preemptableTask = it.next();
			
				if (!preemptableTask.isScheduled())
					continue;
				if (target.getInstanceID() == preemptableTask.getInstanceID())
					continue;
				if (preemptableTask.getFinishMin() <= startTime)
					continue;
				if (target.getExcludeSet().get(preemptableTask.getInstanceID()))
					continue;
				if (target.getBestPreemptorSet().get(preemptableTask.getInstanceID()))
					continue;
				
				if (startTime < preemptableTask.getReleaseMax())
				{
					if (startTime != preemptableTask.getStartMin())
						continue;
					if (!preemptableTask.areAllParentsInSamePE())
						continue;
					
					if (preemptableTask.isSource())
					{
						if (preemptableTask.getParentCount() == 0)
							continue;
						Task previousSource = preemptableTask.getParentIterator().next(); 
						if (previousSource.getFinishMax() < preemptableTask.getReleaseMax())
							continue;
					}
					
					if (!preemptableTask.getBestPreemptorSet().get(previousPreemptor.getInstanceID()))
					{
						if (!previousPreemptor.isParentOf(preemptableTask))
							continue;
						if (previousPreemptor.getFinishMin() != preemptableTask.getReleaseMin())
							continue;
					}
				}
				
				if (target.getMappedPE().isPreemptable())
				{
					if (target.hasHigherPriorityThan(preemptableTask))
						continue;
				}
				else
				{
					if (target.hasHigherPriorityThan(preemptableTask) && 
						releaseTime <= preemptableTask.getStartMax())
						continue;
				}
				
				target.getBestPreemptorSet().set(preemptableTask.getInstanceID());
				previousPreemptor = preemptableTask;
				
				_changed = true;
				startTime = preemptableTask.getFinishMin();
				break;
			}
		}
		while (_changed);
		
		if (target.getStartMin() != startTime)
		{
			target.setStartMin     (startTime);
			changed = true;
		}
		
		// finish time
		long finishTime = target.getStartMin() + target.getBCET();
		if (target.getMappedPE().isPreemptable())
		{
			for (Iterator<Task> it = target.getMappedPE().getTaskIterator(); it.hasNext();)
				it.next().setVisited(false);
			do
			{
				_changed = false;
				
				for (Iterator<Task> it = target.getMappedPE().getTaskIterator(); it.hasNext();)
				{
					Task preemptableTask = it.next();
					
					if (!preemptableTask.isScheduled())
						continue;
					if (target.getInstanceID() == preemptableTask.getInstanceID())
						continue;
					if (target.hasHigherPriorityThan(preemptableTask))
						continue;
					if (preemptableTask.isVisited())
						continue;
					if (target.getExcludeSet().get(preemptableTask.getInstanceID()))
						continue;
					if (target.getBestPreemptorSet().get(preemptableTask.getInstanceID()))
						continue;
					
					if (preemptableTask.getFinishMin() <= startTime || finishTime <= preemptableTask.getReleaseMax())
						continue;

					long preemptionTime = preemptableTask.getBCET();
					if (preemptionTime > preemptableTask.getFinishMin() - startTime)
						preemptionTime = preemptableTask.getFinishMin() - startTime;
					
					preemptableTask.setVisited(true);
					target.getBestPreemptorSet().set(preemptableTask.getInstanceID());
					
					_changed = true;
					finishTime += preemptionTime;
					break;
				}
			}
			while (_changed);
		}
		if(target.getFinishMin() > finishTime)
		{
			target.setFinishMin    (finishTime);
			changed = true;
		}
			
		return true;
	}
	
	private boolean ComputeMax(Task target)
	{	
		boolean _changed = false;

		target.getWorstPreemptorSet().clear();
		
		// release time
		long releaseTime = target.getOwnerInstance().getReleaseMax();
		if (target.getParentCount() > 0)
		{
			if (!target.isSource())
				releaseTime = -1;
			
			for (Iterator<Task> it = target.getParentIterator(); it.hasNext();)
			{
				Task parent = it.next();
				
				if (!parent.isScheduled())
					return false;
				
				if (releaseTime < parent.getFinishMax())
					releaseTime = parent.getFinishMax();
			}
		}
		target.setReleaseMax(releaseTime);
		
		// start time
		long blockingTime = 0;
		boolean blockingPossible = !target.getMappedPE().isPreemptable() &&
				(target.isSource() || !target.areAllParentsInSamePE());
		
		if (blockingPossible)
		{
			Task blockingTask = null;
			for (Iterator<Task> it = target.getMappedPE().getTaskIterator(); it.hasNext();)
			{
				Task preemptableTask = it.next();
				
				if (!preemptableTask.isScheduled())
					continue;
				if (target.getInstanceID() == preemptableTask.getInstanceID())
					continue;
				if (preemptableTask.getFinishMax() <= releaseTime || releaseTime <= preemptableTask.getStartMin())
					continue;
				if (preemptableTask.hasHigherPriorityThan(target))
					continue;
				if (target.getExcludeSet().get(preemptableTask.getInstanceID()))
					continue;
				if (target.getWorstPreemptorSet().get(preemptableTask.getInstanceID()))
					continue;
				if (preemptableTask.isAncestorOf(target))
					continue;
				
				long preemptionTime = preemptableTask.getWCET();
				if (preemptionTime > preemptableTask.getFinishMax() - releaseTime)
					preemptionTime = preemptableTask.getFinishMax() - releaseTime;
				
				if (blockingTime < preemptionTime)
				{
					blockingTime = preemptionTime;
					blockingTask = preemptableTask;
				}
			}
			if (blockingTask != null)
				target.getWorstPreemptorSet().set(blockingTask.getInstanceID());
		}
		
		long startTime = releaseTime + blockingTime;
		do
		{
			_changed = false;
			
			for (Iterator<Task> it = target.getMappedPE().getTaskIterator(); it.hasNext();)
			{
				Task preemptableTask = it.next();
				
				if (!preemptableTask.isScheduled())
					continue;
				if (target.getInstanceID() == preemptableTask.getInstanceID())
					continue;
				if (target.hasHigherPriorityThan(preemptableTask))
					continue;
				if (preemptableTask.getFinishMax() <= releaseTime || startTime < preemptableTask.getStartMin()) 
					continue;
				if (target.getExcludeSet().get(preemptableTask.getInstanceID()))
					continue;
				if (target.getWorstPreemptorSet().get(preemptableTask.getInstanceID()))
					continue;
				if (preemptableTask.isAncestorOf(target))
					continue;
				
				long preemptionTime = preemptableTask.getWCET();
				if (preemptionTime > preemptableTask.getFinishMax() - releaseTime)
					preemptionTime = preemptableTask.getFinishMax() - releaseTime;
				
				target.getWorstPreemptorSet().set(preemptableTask.getInstanceID());
				
				startTime = startTime + preemptionTime;
				_changed = true;
			}
			
			if (startTime - target.getOwnerInstance().getReleaseMin() > 
					target.getOwnerInstance().getGraph().getDeadline())
				break;
		}
		while (_changed);

		if (target.getStartMax() != startTime)
			target.setStartMax     (startTime);
		
		// finish time
		long finishTime = startTime + target.getWCET();
		if (target.getMappedPE().isPreemptable())
		{
			for (Iterator<Task> it = target.getMappedPE().getTaskIterator(); it.hasNext();)
				it.next().setVisited(false);
			do
			{
				_changed = false;
				
				for (Iterator<Task> it = target.getMappedPE().getTaskIterator(); it.hasNext();)
				{
					Task preemptableTask = it.next();
					
					if (!preemptableTask.isScheduled())
						continue;
					if (target.getInstanceID() == preemptableTask.getInstanceID())
						continue;
					if (target.hasHigherPriorityThan(preemptableTask))
						continue;
					if (preemptableTask.isVisited())
						continue;
					if (target.getExcludeSet().get(preemptableTask.getInstanceID()))
						continue;
					if (target.getWorstPreemptorSet().get(preemptableTask.getInstanceID()))
						continue;
					
					if (!(startTime <= preemptableTask.getStartMin() && preemptableTask.getStartMin() < finishTime) &&
						!(startTime <= preemptableTask.getStartMax() && preemptableTask.getStartMax() < finishTime))
						continue;
					
					long preemptionTime = preemptableTask.getWCET();
					
					preemptableTask.setVisited(true);
					target.getWorstPreemptorSet().set(preemptableTask.getInstanceID());

					_changed = true;
					finishTime += preemptionTime;
				}
				
				if (finishTime - target.getOwnerInstance().getReleaseMin() > 
						target.getOwnerInstance().getGraph().getDeadline())
					break;
			}
			while (_changed);
		}
		
		finishTime = Math.max(target.getFinishMin(), finishTime);
		if (target.getFinishMax() != finishTime)
		{
			target.setFinishMax     (finishTime);
			changed = true;
		}
		
		if (target.getFinishMax() - target.getOwnerInstance().getReleaseMin() > 
				target.getOwnerInstance().getGraph().getDeadline())
			target.getOwnerInstance().violateDeadline();
		
		return true;
	}

	private boolean CheckDeadlineViolation()
	{
		for (int i = 0; i < schedule.getGraphCount(); ++i)
		{
			for (Iterator<Graph.Instance> it = schedule.getGraph(i).getInstanceIterator(); it.hasNext();)
			{
				Graph.Instance instance = it.next();
				if (instance.isDeadlineViolated())
					return false;
			}
		}
		return true;
	}
	
	private boolean checkError()
	{
		for (int i= 0 ; i< schedule.getTotalTaskCount(); ++i)
		{
			Task target = schedule.getTask(i);
			
			double mr = target.getReleaseMin();
			double mR = target.getReleaseMax();
			double ms = target.getStartMin();
			double mS = target.getStartMax();
			double mf = target.getFinishMin();
			double mF = target.getFinishMax();
			double bcet = target.getBCET();
			double wcet = target.getWCET();

			if(mr>mR)
			{
				System.err.println("ERR01 mr>mR");
				return false;
			}
			if(ms > mS)
			{
				System.err.println("ERR02 ms > mS");	
				return false;
			}
			if(mf > mF)
			{
				System.err.println("ERR03 mf > mF");
				return false;
			}
			if((ms + bcet) > mf)
			{
				System.err.println("ERR04 (ms + bcet) > mf");
				return false;
			}
			if((mS + wcet) > mF)
			{
				System.err.println("ERR05 (mS + wcet) > mF");
				return false;
			}
		}
		
		return true;
	}
}