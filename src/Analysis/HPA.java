package Analysis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import Data.Description;
import Data.DescriptionOptimizer;
import Schedule.Graph;
import Schedule.Schedule;
import Schedule.Task;

public class HPA
{	
	private Data.Description desc;
	
	private boolean success = false;
	private Schedule schedule;

	public int ITERATION_COUNT;
	private boolean changed;

	private long[] rPhase;
	private long[] sPhase;
	private long[][] fPhase;

	private long[][] pShift;
	
    public static void main(String args[])
    {
    	Description desc = Description.load(args[0]);
    	HPA hpa = new HPA(desc);
    	if (hpa.go())
    	{
    		for (Data.Graph graph : desc.GraphList)
    		{
    			System.out.println("WCRT[" + graph.ID + "] : " + hpa.getWCRT(graph.ID));
    		}
    	}
    	else
    	{
    		System.out.println("Unschedulable");
    	}
    }
    
	public HPA(Data.Description desc) 
	{
		DescriptionOptimizer descOptimizer = new DescriptionOptimizer(desc);
		this.desc = descOptimizer.get();
	}
	
	public HPA(String docString) 
	{
		Description desc = Description.loadfromInputString(docString);
		DescriptionOptimizer descOptimizer = new DescriptionOptimizer(desc);
		this.desc = descOptimizer.get();
	}

	public long getWCRT(int graphID)
	{
		if (success)
		{
			return schedule.getWCRT(graphID);
		}
		return Long.MAX_VALUE;
	}
	
	public boolean go()
	{
		success = false;
		schedule = new Schedule(desc);
		
		boolean expanded = false;
		
		long startTime = System.currentTimeMillis();
		do
		{
			success = Estimation();
			expanded = success && ExpandSchedule();
		}
		while (success && expanded);
		long endTime = System.currentTimeMillis();
		
		return success;
	}

	private static int MAX_COUNT = 200000;
	
	private boolean Estimation()
	{
		Initialize();
		
		InitPeriodShifting();
		InitExclusion();
		
		ITERATION_COUNT = 0;
		do
		{
			changed = false;
			
			BoundScheduling();
			
			if (!CheckDeadlineViolation())
				return false;
			
			UpdatePeriodShifting();
			UpdateExclusion();
			
			//schedule.print();
			
			++ITERATION_COUNT;
		}
		while(changed && ITERATION_COUNT < MAX_COUNT);	
		
		//schedule.print();
		
		return ITERATION_COUNT < MAX_COUNT && checkError();
	}
	
	private boolean ExpandSchedule()
	{
		boolean expanded = false;
		for (int graphID = 0; graphID < schedule.getGraphCount(); ++graphID)
		{
			long WCRT = schedule.getWCRT(graphID);
			long numInstances = (long)Math.ceil((double)WCRT / schedule.getGraph(graphID).getPeriod());
			numInstances = (numInstances- 1) * 2 + 1; 
			
			while (numInstances > schedule.getGraph(graphID).getInstanceCount())
			{
				expanded = true;
				schedule.addGraphInstance(graphID);
			}
		}
		return expanded;
	}
	
	private void Initialize()
	{
		schedule.restructure();
		
		rPhase = new long[schedule.getTaskCount()];
		sPhase = new long[schedule.getTaskCount()];
		fPhase = new long[schedule.getTotalTaskCount()][schedule.getTaskCount()];
		pShift = new long[schedule.getTotalTaskCount()][schedule.getTaskCount()];
	}
	
	private void InitPeriodShifting()
	{
		for (int i = 0; i < schedule.getTotalTaskCount(); ++i)
		for (int j = 0; j < schedule.getTaskCount(); ++j)
			pShift[i][j] = schedule.getTask(j).getOwnerInstance().getGraph().getJitter();
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
				
				if (task.isScheduled() && task.getOwnerInstance().isDeadlineViolated())
					continue;
				
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
		if (target.getReleaseMin() > releaseTime)
		{
			target.setReleaseMin(releaseTime);
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
				
				if (target.getOwnerInstance().getGraph() != 
						preemptableTask.getOwnerInstance().getGraph())
					continue;
				
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
		
		if (target.getStartMin() > startTime)
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
					
					if (target.getOwnerInstance().getGraph() != 
							preemptableTask.getOwnerInstance().getGraph())
						continue;
					
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

		// request phase computation
		ComputeRequestPhase(target);
		
		long savedReleaseTime = releaseTime;
		releaseTime = releaseTime - Math.max(0, RemoveCP(target));
		
		long blockingTime = 0;
		boolean blockingPossible = !target.getMappedPE().isPreemptable() &&
				(target.isSource() || !target.areAllParentsInSamePE());
		
		if (blockingPossible)
		{
			Task blockingTask = null;
			for (Iterator<Task> it = target.getMappedPE().getTaskIterator(); it.hasNext();)
			{
				Task preemptableTask = it.next();
				
				if (target.getOwnerInstance().getGraph() !=
						preemptableTask.getOwnerInstance().getGraph())
					continue;
				
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
			for (int i = 0; i < schedule.getTaskCount(); ++i)
			{
				Task preemptableTask = schedule.getTasksWithID(i).get(0);
				
				if (target.getOwnerInstance().getGraph() ==
						preemptableTask.getOwnerInstance().getGraph())
					continue;
				if (target.getMappedPE() != preemptableTask.getMappedPE())
					continue;
				if (preemptableTask.hasHigherPriorityThan(target))
					continue;

				if (blockingTime < preemptableTask.getClusteredTasksMaxExecTime())
				{
					blockingTime = preemptableTask.getClusteredTasksMaxExecTime();
					blockingTask = preemptableTask;
				}
			}
			if (blockingTask != null)
			{
				if (blockingTask.getOwnerInstance().getGraph() == 
						target.getOwnerInstance().getGraph())
					target.getWorstPreemptorSet().set(blockingTask.getInstanceID());
			}
		}

		long STBAPreemptionTime = 0;
		long RTAPreemptionTime = 0;
		long startTime = releaseTime + blockingTime;
		do
		{
			_changed = false;
			
			for (Iterator<Task> it = target.getMappedPE().getTaskIterator(); it.hasNext();)
			{
				Task preemptableTask = it.next();
				
				if (target.getOwnerInstance().getGraph() != 
						preemptableTask.getOwnerInstance().getGraph())
					continue;
				
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
				
				STBAPreemptionTime = STBAPreemptionTime + preemptionTime;
				startTime = startTime + preemptionTime;
				_changed = true;
			}
			
			RTAPreemptionTime = 0;
			for (int i = 0; i < schedule.getTaskCount(); ++i)
			{
				Task preemptableTask = schedule.getTasksWithID(i).get(0);
				
				if (target.getOwnerInstance().getGraph() ==
						preemptableTask.getOwnerInstance().getGraph())
					continue;

				if (target.getMappedPE() != preemptableTask.getMappedPE())
					continue;
				if (target.hasHigherPriorityThan(preemptableTask))
					continue;
				
				int preemptableTaskId = preemptableTask.getID();
				long period = preemptableTask.getOwnerInstance().getGraph().getPeriod();
				
				double timeWindow = (double)Math.max(0, Math.max(0, startTime - savedReleaseTime) + 1 -rPhase[preemptableTaskId]);
				int preemptionCount = (int)Math.ceil(timeWindow / period);
				
				RTAPreemptionTime += preemptableTask.getWCET() * preemptionCount;
			}
			
			if (startTime < releaseTime + blockingTime + STBAPreemptionTime + RTAPreemptionTime)
			{
				startTime = releaseTime + blockingTime + STBAPreemptionTime + RTAPreemptionTime;
				_changed = true;
			}
			
			if (startTime - target.getOwnerInstance().getReleaseMin()
					> target.getOwnerInstance().getGraph().getDeadline())
				break;
		}
		while (_changed);

		if (startTime < savedReleaseTime)
			startTime = savedReleaseTime;
		
		if (startTime - target.getOwnerInstance().getReleaseMin()
				> target.getOwnerInstance().getGraph().getDeadline())
			target.getOwnerInstance().violateDeadline();
		
		if (target.getStartMax() < startTime)
		{
			target.setStartMax     (startTime);
			changed = true;
		}
		
		// start phase computation
		ComputeStartPhase(target);
		
		// finish time
		STBAPreemptionTime = 0;
		RTAPreemptionTime = 0;
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
					
					if (target.getOwnerInstance().getGraph() != 
							preemptableTask.getOwnerInstance().getGraph())
						continue;
					
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
					STBAPreemptionTime += preemptionTime;
					finishTime += preemptionTime;
				}
				
				RTAPreemptionTime = 0;
				for (int i = 0; i < schedule.getTaskCount(); ++i)
				{
					Task preemptableTask = schedule.getTasksWithID(i).get(0);
					
					if (target.getOwnerInstance().getGraph() == 
							preemptableTask.getOwnerInstance().getGraph())
						continue;

					if (target.getMappedPE() != preemptableTask.getMappedPE())
						continue;
					if (target.hasHigherPriorityThan(preemptableTask))
						continue;
					
					int preemptableTaskId = preemptableTask.getID();
					long period = preemptableTask.getOwnerInstance().getGraph().getPeriod();
					
					double timeWindow = (double)Math.max(0, finishTime - startTime -sPhase[preemptableTaskId]);
					int preemptionCount = (int)Math.ceil(timeWindow / period);
					
					RTAPreemptionTime += preemptableTask.getWCET() * preemptionCount;
				}
				
				if (finishTime < startTime + target.getWCET() + STBAPreemptionTime + RTAPreemptionTime)
				{
					finishTime = startTime + target.getWCET() + STBAPreemptionTime + RTAPreemptionTime;
					_changed = true;
				}
				
				if (finishTime - target.getOwnerInstance().getReleaseMin() > 
					target.getOwnerInstance().getGraph().getDeadline())
					break;
			}
			while (_changed);
		}
		
		finishTime = Math.max(target.getFinishMin(), finishTime);
		if (target.getFinishMax() < finishTime)
		{
			target.setFinishMax     (finishTime);
			changed = true;
		}
		
		if (target.getFinishMax() - target.getOwnerInstance().getReleaseMin() >
			target.getOwnerInstance().getGraph().getDeadline())
			target.getOwnerInstance().violateDeadline();
		
		// finish phase computation
		ComputeFinishPhase(target);
		
		return true;
	}
	
	private void ComputeRequestPhase(Task target)
	{
		if (target.isSource())
		{
			for (int i = 0; i < schedule.getTaskCount(); ++i)
				rPhase[i] = -pShift[target.getInstanceID()][i];
			return;
		}
		
		for (int i = 0; i < schedule.getTaskCount(); ++i)
			rPhase[i] = Integer.MAX_VALUE;
		
		for (Iterator<Task> it = target.getParentIterator(); it.hasNext();)
		{
			Task parent = it.next();
			for (int i = 0; i < schedule.getTaskCount(); ++i)
				rPhase[i] = Math.min(rPhase[i], fPhase[parent.getInstanceID()][i]+parent.getFinishMax());
		}
		
		for (int i = 0; i < schedule.getTaskCount(); ++i)
			rPhase[i] = rPhase[i] - target.getReleaseMax();
		
		for (int i = 0; i < schedule.getTaskCount(); ++i)
		{
			Task task = schedule.getTasksWithID(i).get(0);
			
			if (target.getMappedPE() != task.getMappedPE())
				rPhase[i] = -pShift[target.getInstanceID()][i];
			else
				rPhase[i] = Math.max(-pShift[target.getInstanceID()][i], rPhase[i]);
		}
	}
	
	private void ComputeStartPhase(Task target)
	{
		for (int i = 0; i < schedule.getTaskCount(); ++i)
		{
			Task task = schedule.getTasksWithID(i).get(0);
			
			if (target.getOwnerInstance().getGraph() == 
					task.getOwnerInstance().getGraph())
				continue;

			int taskId = task.getID();
			long period = task.getOwnerInstance().getGraph().getPeriod();
			
			sPhase[taskId] = rPhase[taskId] - (target.getStartMax() - target.getReleaseMax());
			
			if (task.getMappedPE() == target.getMappedPE() && task.hasHigherPriorityThan(target))
			{
				if (sPhase[taskId] < 0)
					sPhase[taskId] += period * (-sPhase[taskId] / period + 1);
				
				sPhase[taskId] = sPhase[taskId] % period;
			}
		}
	}
	
	private void ComputeFinishPhase(Task target)
	{
		for (int i = 0; i < schedule.getTaskCount(); ++i)
		{
			Task task = schedule.getTasksWithID(i).get(0);
			
			if (target.getOwnerInstance().getGraph() == 
					task.getOwnerInstance().getGraph())
				continue;

			int taskId = task.getID();
			long period = task.getOwnerInstance().getGraph().getPeriod();
		
			long phase = sPhase[taskId] - (target.getFinishMax() - target.getStartMax());
			
			if (target.getMappedPE().isPreemptable() && 
				task.getMappedPE() == target.getMappedPE() && task.hasHigherPriorityThan(target))
			{
				if (phase < 0)
					phase += period * (-phase / period + 1);
				
				phase = phase % period;
			}
			
			if (fPhase[target.getInstanceID()][taskId] != phase)
			{
				changed = true;
				fPhase[target.getInstanceID()][taskId] = phase;
			}
		}
	}
	
	private void UpdatePeriodShifting()
	{
		for (int i = 0; i < schedule.getTotalTaskCount(); ++i)
		{
			Task task_i = schedule.getTask(i);
			
			for (int j = 0; j < schedule.getTaskCount(); ++j)
			{
				if (task_i.getID() == j)
					continue;
				
				ArrayList<Task> taskList_j = schedule.getTasksWithID(j);
				
				long newPShift = 0;
				long PShiftUpperBound = 0;
				for (Task task_j : taskList_j)
				{
					newPShift = Math.max(newPShift, 
							task_j.getReleaseMax() - task_j.getReleaseMin());
					PShiftUpperBound = Math.max(PShiftUpperBound,
							task_j.getStartMax() - task_j.getReleaseMin());
				}

				// Key idea : find task executions that may before task_i.releaseMax and delay
				//            task_j's release. below is the rough illustration for pshift computation
				//
				//  |<-------------------conservative pshift-------------------->| 
				//  |<-task_j release diff->|<-task_j blocked in this interval-->|
				//                          | task executions we want to compute |
				//                                                               |
				//                                                             task_i release
				for (Iterator<Task> it = task_i.getMappedPE().getTaskIterator(); it.hasNext();)
				{
					Task task_k = it.next();
					
					if (task_i.getInstanceID() == task_k.getInstanceID() || task_k.getID() == j)
						continue;
					if (task_i.getOwnerInstance().getGraph() !=
						task_k.getOwnerInstance().getGraph())
						continue;
					
					if (task_i.getMappedPE().isPreemptable() &&
						taskList_j.get(0).hasHigherPriorityThan(task_k))
						continue;
					if (task_k.getStartMin() >= task_i.getReleaseMax()) 
						continue;
					
					newPShift += Math.min(task_k.getWCET(), task_i.getReleaseMax() - task_k.getStartMin());
				}
				if (newPShift > PShiftUpperBound)
					newPShift = PShiftUpperBound;
				
				if (pShift[i][j] != newPShift)
				{
					changed = true;
					pShift[i][j] = newPShift;
				}
			}
		}
	}
	
	private long RemoveCP(Task target)
	{
		long releaseDiff = 0;
		long newReleaseDiff = 0;
		ArrayList<Task> uniqueCommonPreemptors = new ArrayList<Task>();
		do
		{
			releaseDiff = newReleaseDiff;
			uniqueCommonPreemptors.clear();
			
			newReleaseDiff = RecursiveRemoveCP(target, target, uniqueCommonPreemptors, releaseDiff);
		}
		while (releaseDiff != newReleaseDiff);
		return releaseDiff;
	}

	private long RecursiveRemoveCP(Task currentTask, Task target,  
			ArrayList<Task> uniqueCommonPreemptors,
			long releaseDiff)
	{	
		long worseReleaseTime = 0;
		Task criticalPathTask = null;
		for (Iterator<Task> it = currentTask.getParentIterator(); it.hasNext();)
		{
			Task parent = it.next();
			
			if (parent.getFinishMax() == currentTask.getReleaseMax())
			{
				criticalPathTask = parent;
				continue;
			}
			if (worseReleaseTime < parent.getFinishMax())
				worseReleaseTime = parent.getFinishMax(); 
		}
		
		long commonPreemptionTime = 0;
		if (currentTask != target)
			commonPreemptionTime = FindCommonPreemptions(currentTask, target,
					uniqueCommonPreemptors, releaseDiff);

		if (currentTask.isSource() || criticalPathTask == null)
			return commonPreemptionTime;
		
		long availReleaseDiff = RecursiveRemoveCP(criticalPathTask, target, 
				uniqueCommonPreemptors, releaseDiff);
		if (currentTask != target)
			availReleaseDiff = AdjustReleaseDiff(currentTask, availReleaseDiff);
		
		availReleaseDiff = Math.min(availReleaseDiff, criticalPathTask.getFinishMax() - worseReleaseTime) + commonPreemptionTime;

		return availReleaseDiff;
	}
	
	private long FindCommonPreemptions(Task currentTask, Task target,
			ArrayList<Task> uniqueCommonPreemptors,
			long releaseDiff)
	{
		if (currentTask.getMappedPE() != target.getMappedPE())
			return 0;
		
		long commonPreemptionTime = 0;
		for (int i = currentTask.getWorstPreemptorSet().nextSetBit(0); i >= 0;
				 i = currentTask.getWorstPreemptorSet().nextSetBit(i+1))
		{
			Task preemptableTask = schedule.getTask(i);;
			
			if (target.getOwnerInstance().getGraph() != preemptableTask.getOwnerInstance().getGraph())
				continue;
			
			if (target.getInstanceID() == preemptableTask.getInstanceID())
				continue;
			if (preemptableTask.getFinishMax() <= target.getReleaseMax() - releaseDiff)
				continue;
			if (target.hasHigherPriorityThan(preemptableTask) || currentTask.hasHigherPriorityThan(preemptableTask))
				continue;
			if (target.getExcludeSet().get(preemptableTask.getInstanceID()))
				continue;
			if (uniqueCommonPreemptors.contains(preemptableTask))
				continue;
			
			uniqueCommonPreemptors.add(preemptableTask);
			commonPreemptionTime = commonPreemptionTime + preemptableTask.getWCET();
		}
		
		return commonPreemptionTime;
	}
	
	private long AdjustReleaseDiff(Task currentTask, long releaseDiff)
	{
		if (releaseDiff == 0)
			return 0;
		
		long newReleaseTime = currentTask.getReleaseMax() - releaseDiff;
		
		long lowerPriorityDiff = 0;
		
		for (Iterator<Task> it = currentTask.getMappedPE().getTaskIterator(); it.hasNext();)
		{
			Task preemptableTask = it.next();
			
			if (currentTask.getOwnerInstance().getGraph() != 
					preemptableTask.getOwnerInstance().getGraph())
				continue;
			
			if (!preemptableTask.isScheduled())
				continue;
			if (currentTask.getInstanceID() == preemptableTask.getInstanceID())
				continue;
			if (currentTask.getMappedPE().isPreemptable() && currentTask.hasHigherPriorityThan(preemptableTask))
				continue;
			if (preemptableTask.getFinishMax() <= newReleaseTime || currentTask.getReleaseMax() < preemptableTask.getStartMin())
				continue;
			if (currentTask.getWorstPreemptorSet().get(preemptableTask.getInstanceID()))
				continue;
			if (currentTask.getExcludeSet().get(preemptableTask.getInstanceID()))
				continue;
			if (preemptableTask.isAncestorOf(currentTask))
				continue;
		
			long subtractDiff = Math.min(preemptableTask.getWCET(), preemptableTask.getFinishMax() - newReleaseTime);
			if (currentTask.getMappedPE().isPreemptable() || currentTask.hasHigherPriorityThan(preemptableTask))
				releaseDiff -= subtractDiff; 
			else if (lowerPriorityDiff < subtractDiff)
				lowerPriorityDiff = subtractDiff;
			
			if (releaseDiff <= 0)
				return 0;
		}
		
		return Math.max(0, releaseDiff - lowerPriorityDiff);
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