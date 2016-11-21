/* Copyright (c) 2016, Codesign And Parallel processing Laboratory (CAPLab)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the Codesign And Parallel processing Laboratory (CAPLab) 
 * nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package HPA;

import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

public class HPA
{	
	private Debug.Stream debug;
	private Description desc;

	private TaskList scheduleOrder = new TaskList();
	
	public int ITERATION_COUNT;
	private boolean changed;
	
	private BitSet[] procTaskSet;
	private BitSet[] BestPreemptorSet;
	private BitSet[] WorstPreemptorSet;
	private BitSet[] SuccessorSet;
	private BitSet[] ExcludeSet;

	private long[] rPhase;
	private long[] sPhase;
	private long[][] fPhase;

	private long[][] pShift;
	
	private int[] maxPreemptCountTemp;	
	private int[][] maxPreemptCount;

	long additionalStartTime;
	
	public HPA(Description desc) 
	{
		this.desc = desc;
	
		procTaskSet = new BitSet[desc.totalProcessorList.size()];
		BestPreemptorSet = new BitSet[desc.totalTaskList.size()];
		WorstPreemptorSet = new BitSet[desc.totalTaskList.size()];
		SuccessorSet = new BitSet[desc.totalTaskList.size()];
		ExcludeSet = new BitSet[desc.totalTaskList.size()];
		
		for (int i = 0; i < desc.totalProcessorList.size(); ++i)
		{
			procTaskSet[i] = new BitSet(desc.totalTaskList.size());
			for (Task task : desc.totalProcessorList.get(i).getMappedSubtasks())
				procTaskSet[i].set(task.id);
		}
		
		for (int i = 0; i < desc.totalTaskList.size(); ++i)
		{
			BestPreemptorSet[i] = new BitSet(desc.totalTaskList.size());
			WorstPreemptorSet[i] = new BitSet(desc.totalTaskList.size());
			SuccessorSet[i] = new BitSet(desc.totalTaskList.size());
			ExcludeSet[i] = new BitSet(desc.totalTaskList.size());
		}
		
		rPhase = new long[desc.totalTaskList.size()];
		sPhase = new long[desc.totalTaskList.size()];
		fPhase = new long[desc.totalTaskList.size()][desc.totalTaskList.size()];

		pShift = new long[desc.totalTaskList.size()][desc.totalTaskList.size()];

		maxPreemptCountTemp = new int[desc.totalTaskList.size()];
		maxPreemptCount = new int[desc.totalTaskList.size()][desc.totalTaskList.size()];
		
		debug = Debug.ESTI;	
	}

	public boolean go()
	{
		boolean error = false;
		
		debug.println("--- STBA with response time analysis !!START!! ---");
		long startTime = System.currentTimeMillis();
		{
			error = Estimation();
		}
		long endTime = System.currentTimeMillis();
		debug.println("Analyzing time: " + (endTime-startTime)+" ms");

		for (int i = 0 ; i < desc.totalTaskList.size() ; ++i)
		{
			Task s = desc.totalTaskList.get(i);
			s.setEndMax(s.getFinalFin());
		}
		
		return error;
	}

	private static int MAX_COUNT = 200000;
	
	private boolean Estimation()
	{
		Initialize();
		InitScheduleOrder();
		
		InitPeriodShifting();
		InitExclusion();
		
		ITERATION_COUNT = 0;
		do
		{
			changed = false;
			
			BoundScheduling();
			
			printSchedule();
			
			++ITERATION_COUNT;
		}
		while(changed && ITERATION_COUNT < MAX_COUNT);	
		
		printSchedule();
		
		return ITERATION_COUNT == MAX_COUNT || !checkError();
	}
	
	private void Initialize()
	{
		for (int i = 0; i < desc.totalTaskList.size(); ++i)
		{
			Task task = desc.totalTaskList.get(i);
			task.reset();
			
			if (task.isItSource())
			{
				TaskGroup parent = task.getParentTaskGroup();
				TaskGroupSet parentSet = parent.getTaskGroupSet();
				
				double minRelease, maxRelease;

				minRelease = parentSet.getStartOffset() + parent.getPeriod()*parent.getInstanceID();
				maxRelease = parentSet.getStartOffset() + parent.getPeriod()*parent.getInstanceID() + task.getJitter();
				
				task.setInitialReleaseMin((int)minRelease);
				task.setInitialReleaseMax((int)maxRelease);
			}
		}
		
		Queue<Task> visitQueue = new LinkedList<Task>();
		for (Task task : desc.totalTaskList)
		{
			task.resetVisitableCount(task.getOutgoingEdges().size());
			if (task.isItSink())
				visitQueue.offer(task);
		}

		while (!visitQueue.isEmpty())
		{
			Task task = visitQueue.poll();
			for (Task predecessor : task.getIncommingEdges())
			{
				SuccessorSet[predecessor.id].or(SuccessorSet[task.id]);
				SuccessorSet[predecessor.id].set(task.id);
				
				if (predecessor.decVisitableCount())
					visitQueue.offer(predecessor);
			}
		}
	}
	
	private void InitPeriodShifting()
	{
		for (int i = 0; i < desc.totalTaskList.size(); ++i)
		for (int j = 0; j < desc.totalTaskList.size(); ++j)
			pShift[i][j] = desc.totalTaskList.get(j).getJitter();
	}
	
	private void InitScheduleOrder()
	{
		TaskList prioritySortedList = new TaskList();
		prioritySortedList.addAll(desc.totalTaskList);
		
		Collections.sort(prioritySortedList, new java.util.Comparator<Task>() {
			@Override public int compare(Task lhs, Task rhs)
			{ return lhs.getPriority().compareTo(rhs.getPriority()); }
		});
		
		for (Processor proc: desc.totalProcessorList)
		{
			proc.initPriorityOrder(prioritySortedList);
			proc.topologicalOrder();
		}
		
		scheduleOrder.clear();
		for (int i = 0; scheduleOrder.size() < desc.totalTaskList.size(); ++i)
		{
			for (Processor proc : desc.totalProcessorList)
			{
				if (i < proc.getScanOrder().size())
					scheduleOrder.add(proc.getScanOrder().get(i));
			}
		}
	}
	
	private void InitExclusion()
	{
		for (Task task : desc.totalTaskList)
		{
			ExcludeSet[task.id].or(SuccessorSet[task.id]);
			ExcludeSet[task.id].and(procTaskSet[task.getMappedProcID()]);
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
			for (Task task : scheduleOrder)
			{
				iterateOrder.add(task);
				++iterateCount;
			}
			
			for (; iterateCount > 0; --iterateCount,
				waitCount = Math.max(0, waitCount - 1))
			{
				Task task = iterateOrder.removeFirst();
				
				if (task.isScheduled() && task.getParentTaskGroup().isDeadlineViolated())
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
		Processor proc = desc.totalProcessorList.getFromId(target.getMappedProcID());
		TaskList procScanOrder = proc.getScanOrder();
	
		boolean _changed = false;
		Task previousPreemptor = null;
		
		BestPreemptorSet[target.id].clear();
		
		// release time
		long releaseTime = target.getInitialReleaseMin();
		if (target.getIncommingEdges().size() > 0)
		{	
			if (!target.isItSource())
				releaseTime = -1;
			for (Task predecessor : target.getIncommingEdges())
			{
				if (!predecessor.isScheduled())
					return false;
				
				if (releaseTime < predecessor.getEndMin())
				{
					releaseTime = predecessor.getEndMin();
					previousPreemptor = predecessor;
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
			
			for (Task preemptableTask : procScanOrder)
			{
				if (target.getParentTaskId() != preemptableTask.getParentTaskId())
					continue;
				
				if (!preemptableTask.isScheduled())
					continue;
				if (target.getId() == preemptableTask.getId())
					continue;
				if (preemptableTask.getEndMin() <= startTime)
					continue;
				if (ExcludeSet[target.id].get(preemptableTask.id))
					continue;
				if (BestPreemptorSet[target.id].get(preemptableTask.id))
					continue;
				
				if (startTime < preemptableTask.getReleaseMax())
				{
					if (startTime != preemptableTask.getStartMin())
						continue;
					if (!preemptableTask.predSameProcessor())
						continue;
					
					if (preemptableTask.isItSource())
					{
						if (preemptableTask.getIncommingEdges().size() == 0)
							continue;
						Task previousSource = preemptableTask.getIncommingEdges().get(0); 
						if (previousSource.getEndMax() < preemptableTask.getReleaseMax())
							continue;
					}
					
					if (!BestPreemptorSet[preemptableTask.id].get(previousPreemptor.id))
					{
						if (!previousPreemptor.getOutgoingEdges().contains(preemptableTask))
							continue;
						if (previousPreemptor.getEndMin() != preemptableTask.getReleaseMin())
							continue;
					}
				}
				
				if (proc.isPreemtable())
				{
					if (target.higherPriorThan(preemptableTask))
						continue;
				}
				else
				{
					if (target.higherPriorThan(preemptableTask) && releaseTime <= preemptableTask.getStartMax())
						continue;
				}
				
				BestPreemptorSet[target.id].set(preemptableTask.id);
				previousPreemptor = preemptableTask;
				
				_changed = true;
				startTime = preemptableTask.getEndMin();
				break;
			}
		}
		while (_changed);
		
		if (target.getStartMin() != startTime)
		{
			if (target.getStartMin() > startTime)
			{
				target.setStartMin(startTime);
			}
			target.setStartMin     (startTime);
			changed = true;
		}
		
		// finish time
		long finishTime = target.getStartMin() + target.getComputationLower();
		if (proc.isPreemtable())
		{
			for (Task preemptableTask : procScanOrder)
				preemptableTask.setEndPointDirty(false);
			do
			{
				_changed = false;
				
				for (Task preemptableTask : procScanOrder)
				{
					if (target.getParentTaskId() != preemptableTask.getParentTaskId())
						continue;
					
					if (!preemptableTask.isScheduled())
						continue;
					if (target.getId() == preemptableTask.getId())
						continue;
					if (target.higherPriorThan(preemptableTask))
						continue;
					if (preemptableTask.isEndPointDirty())
						continue;
					if (ExcludeSet[target.id].get(preemptableTask.id))
						continue;
					if (BestPreemptorSet[target.id].get(preemptableTask.id))
						continue;
					
					if (preemptableTask.getEndMin() <= startTime || finishTime <= preemptableTask.getReleaseMax())
						continue;

					long preemptionTime = preemptableTask.getComputationLower();
					if (preemptionTime > preemptableTask.getEndMin() - startTime)
						preemptionTime = preemptableTask.getEndMin() - startTime;
					
					preemptableTask.setEndPointDirty(true);
					BestPreemptorSet[target.id].set(preemptableTask.id);
					
					_changed = true;
					finishTime += preemptionTime;
					break;
				}
			}
			while (_changed);
		}
		if(target.getEndMin() != finishTime)
		{
			if (target.getEndMin() > finishTime)
			{
				target.setEndMin(finishTime);
			}
			target.setEndMin    (finishTime);
			changed = true;
		}
		
		return true;
	}
	
	private boolean ComputeMax(Task target)
	{
		Processor proc = desc.totalProcessorList.getFromId(target.getMappedProcID());
		TaskList procScanOrder = proc.getScanOrder();
	
		boolean _changed = false;

		WorstPreemptorSet[target.id].clear();
		for (int i = 0; i < desc.totalTaskList.size(); ++i)
			maxPreemptCount[target.getBaseId()][i] = 0;
		
		// release time
		long releaseTime = target.getInitialReleaseMax();
		if (target.getIncommingEdges().size() > 0)
		{
			if (!target.isItSource())
				releaseTime = -1;
			
			for (Task predecessor : target.getIncommingEdges())
			{
				if (!predecessor.isScheduled())
					return false;
				
				if (releaseTime < predecessor.getEndMax())
					releaseTime = predecessor.getEndMax();
			}
		}
		target.setReleaseMax(releaseTime);

		// request phase computation
		ComputeRequestPhase(target);
		
		additionalStartTime = 0;
		long savedReleaseTime = releaseTime;
		
		long blockingTime = 0;
		boolean blockingPossible = !proc.isPreemtable();
		if (blockingPossible && !target.isItSource())
		{
			blockingPossible = false;
			for (Task predecessor : target.getIncommingEdges())
			{
				if (predecessor.getMappedProcID() != target.getMappedProcID())
				{
					blockingPossible = true;
					break;
				}
			}
		}
		
		if (blockingPossible)
		{
			Task blockingTask = null;
			for (Task preemptableTask : procScanOrder)
			{
				if (target.getParentTaskId() != preemptableTask.getParentTaskId())
					continue;
				
				if (!preemptableTask.isScheduled())
					continue;
				if (target.getId() == preemptableTask.getId())
					continue;
				if (preemptableTask.getEndMax() <= releaseTime || releaseTime <= preemptableTask.getStartMin())
					continue;
				if (preemptableTask.higherPriorThan(target))
					continue;
				if (ExcludeSet[target.id].get(preemptableTask.id))
					continue;
				if (WorstPreemptorSet[target.id].get(preemptableTask.id))
					continue;
				if (SuccessorSet[preemptableTask.id].get(target.id))
					continue;
				
				long preemptionTime = preemptableTask.getComputationUpper();
				if (preemptionTime > preemptableTask.getEndMax() - releaseTime)
					preemptionTime = preemptableTask.getEndMax() - releaseTime;
				
				if (blockingTime < preemptionTime)
				{
					blockingTime = preemptionTime;
					blockingTask = preemptableTask;
				}
			}
			for (Task preemptableTask : procScanOrder)
			{	// other task group
				if (target.getParentTaskId() == preemptableTask.getParentTaskId())
					continue;
				if (preemptableTask.higherPriorThan(target))
					continue;
				if (blockingTime < preemptableTask.getComputationUpper())
				{
					blockingTime = preemptableTask.getComputationUpper();
					blockingTask = preemptableTask;
				}
			}
			if (blockingTask != null)
			{
				if (blockingTask.getParentTaskId() == target.getParentTaskId())
					WorstPreemptorSet[target.id].set(blockingTask.id);
				maxPreemptCount[target.getBaseId()][blockingTask.getBaseId()]++;
			}
		}

		for (int i = 0; i < desc.totalTaskList.size(); ++i)
			maxPreemptCountTemp[i] = 0;
		
		long STBAPreemptionTime = 0;
		long RTAPreemptionTime = 0;
		long startTime = releaseTime + additionalStartTime + blockingTime;
		do
		{
			_changed = false;
			
			for (Task preemptableTask : procScanOrder)
			{
				if (target.getParentTaskId() != preemptableTask.getParentTaskId())
					continue;
				
				if (!preemptableTask.isScheduled())
					continue;
				if (target.getId() == preemptableTask.getId())
					continue;
				if (target.higherPriorThan(preemptableTask))
					continue;
				if (preemptableTask.getEndMax() <= releaseTime || startTime < preemptableTask.getStartMin()) 
					continue;
				if (ExcludeSet[target.id].get(preemptableTask.id))
					continue;
				if (WorstPreemptorSet[target.id].get(preemptableTask.id))
					continue;
				if (SuccessorSet[preemptableTask.id].get(target.id))
					continue;
				
				long preemptionTime = preemptableTask.getComputationUpper();
				if (preemptionTime > preemptableTask.getEndMax() - releaseTime)
					preemptionTime = preemptableTask.getEndMax() - releaseTime;
				
				WorstPreemptorSet[target.id].set(preemptableTask.id);
				maxPreemptCount[target.getBaseId()][preemptableTask.getBaseId()]++;
				
				STBAPreemptionTime = STBAPreemptionTime + preemptionTime;
				startTime = startTime + preemptionTime;
				_changed = true;
			}
			
			RTAPreemptionTime = 0;
			for (Task preemptableTask : procScanOrder)
			{
				if (target.getParentTaskId() == preemptableTask.getParentTaskId())
					continue;
				
				if (target.higherPriorThan(preemptableTask))
					continue;
				
				int preemptableTaskId = preemptableTask.getBaseId();
				long period = preemptableTask.getParentTaskGroup().getPeriod();
				
				double timeWindow = (double)Math.max(0, Math.max(0, startTime - savedReleaseTime) + 1 -rPhase[preemptableTaskId]);
				int preemptionCount = (int)Math.ceil(timeWindow / period);
				
				RTAPreemptionTime += preemptableTask.getComputationUpper() * preemptionCount;
				maxPreemptCountTemp[preemptableTaskId] = preemptionCount;
			}
			
			if (startTime < releaseTime + additionalStartTime + blockingTime + STBAPreemptionTime + RTAPreemptionTime)
			{
				startTime = releaseTime + additionalStartTime + blockingTime + STBAPreemptionTime + RTAPreemptionTime;
				_changed = true;
			}
			
			if (startTime > target.getParentTaskGroup().getDeadline())
				break;
		}
		while (_changed);

		for (int i = 0; i < desc.totalTaskList.size(); ++i)
			maxPreemptCount[target.getBaseId()][i] += maxPreemptCountTemp[i];

		if (startTime < savedReleaseTime)
			startTime = savedReleaseTime;
		
		if (target.getStartMax() != startTime)
		{
			target.setStartMax     (startTime);
			if (target.getFinalStart() != startTime)
			{
				target.setFinalStart     (startTime);
				changed = true;
			}
		}
		
		// start phase computation
		ComputeStartPhase(target);
		
		// finish time
		for (int i = 0; i < desc.totalTaskList.size(); ++i)
			maxPreemptCountTemp[i] = 0;
		
		STBAPreemptionTime = 0;
		RTAPreemptionTime = 0;
		long finishTime = startTime + target.getComputationUpper();
		if (proc.isPreemtable())
		{
			for (Task preemptableTask : procScanOrder)
				preemptableTask.setEndPointDirty(false);
			do
			{
				_changed = false;
				
				for (Task preemptableTask : procScanOrder)
				{
					if (target.getParentTaskId() != preemptableTask.getParentTaskId())
						continue;
					
					if (!preemptableTask.isScheduled())
						continue;
					if (target.getId() == preemptableTask.getId())
						continue;
					if (target.higherPriorThan(preemptableTask))
						continue;
					if (preemptableTask.isEndPointDirty())
						continue;
					if (ExcludeSet[target.id].get(preemptableTask.id))
						continue;
					if (WorstPreemptorSet[target.id].get(preemptableTask.id))
						continue;
					
					if (!(startTime <= preemptableTask.getStartMin() && preemptableTask.getStartMin() < finishTime) &&
						!(startTime <= preemptableTask.getStartMax() && preemptableTask.getStartMax() < finishTime))
						continue;
					
					int preemptionTime = preemptableTask.getComputationUpper();
					
					preemptableTask.setEndPointDirty(true);
					WorstPreemptorSet[target.id].set(preemptableTask.id);
					maxPreemptCount[target.getBaseId()][preemptableTask.getBaseId()]++;

					_changed = true;
					STBAPreemptionTime += preemptionTime;
					finishTime += preemptionTime;
				}
				
				RTAPreemptionTime = 0;
				for (Task preemptableTask : procScanOrder)
				{
					if (target.getParentTaskId() == preemptableTask.getParentTaskId())
						continue;
					
					if (target.higherPriorThan(preemptableTask))
						continue;
					
					int preemptableTaskId = preemptableTask.getBaseId();
					long period = preemptableTask.getParentTaskGroup().getPeriod();
					
					double timeWindow = (double)Math.max(0, finishTime - startTime -sPhase[preemptableTaskId]);
					int preemptionCount = (int)Math.ceil(timeWindow / period);
					
					RTAPreemptionTime += preemptableTask.getComputationUpper() * preemptionCount;
					maxPreemptCountTemp[preemptableTaskId] = preemptionCount;
				}
				
				if (finishTime < startTime + target.getComputationUpper() + STBAPreemptionTime + RTAPreemptionTime)
				{
					finishTime = startTime + target.getComputationUpper() + STBAPreemptionTime + RTAPreemptionTime;
					_changed = true;
				}
				
				if (finishTime > target.getParentTaskGroup().getDeadline())
					break;
			}
			while (_changed);

			for (int i = 0; i < desc.totalTaskList.size(); ++i)
				maxPreemptCount[target.getBaseId()][i] += maxPreemptCountTemp[i];
		}
		
		finishTime = Math.max(target.getEndMin(), finishTime);
		if (target.getEndMax() != finishTime)
		{
			if (target.getEndMax() < finishTime)
			{
				target.setEndMax(finishTime);
			}
			
			target.setEndMax     (finishTime);
			if (target.getFinalFin() != finishTime)
			{
				target.setFinalFin     (finishTime);
				changed = true;
			}
		}
		
		if (target.getEndMax() > target.getParentTaskGroup().getDeadline())
			target.getParentTaskGroup().violateDeadline();
		
		// finish phase computation
		ComputeFinishPhase(target);
		
		return true;
	}
	
	private void ComputeRequestPhase(Task target)
	{
		if (target.isItSource())
		{
			for (int i = 0; i < desc.totalTaskList.size(); ++i)
				rPhase[i] = -pShift[target.getBaseId()][i];
			return;
		}
		
		for (int i = 0; i < desc.totalTaskList.size(); ++i)
			rPhase[i] = Integer.MAX_VALUE;
		
		for (Task predecessor : target.getIncommingEdges())
			for (int i = 0; i < desc.totalTaskList.size(); ++i)
				rPhase[i] = Math.min(rPhase[i], fPhase[predecessor.getBaseId()][i]+predecessor.getEndMax());

		for (int i = 0; i < desc.totalTaskList.size(); ++i)
			rPhase[i] = rPhase[i] - target.getReleaseMax();
		
		for (int i = 0; i < desc.totalTaskList.size(); ++i)
		{
			Task task = desc.totalTaskList.getFromBaseId(i);
			
			if (target.getMappedProcID() != task.getMappedProcID())
				rPhase[i] = -pShift[target.getBaseId()][i];
			else
				rPhase[i] = Math.max(-pShift[target.getBaseId()][i], rPhase[i]);
		}
	}
	
	private void ComputeStartPhase(Task target)
	{
		for (Task task : desc.totalTaskList)
		{
			if (target.getParentTaskId() == task.getParentTaskId())
				continue;

			int taskId = task.getBaseId();
			long period = task.getParentTaskGroup().getPeriod();
			
			sPhase[taskId] = rPhase[taskId] - (target.getStartMax() - target.getReleaseMax());
			
			if (task.getMappedProcID() == target.getMappedProcID() && task.higherPriorThan(target))
			{
				if (sPhase[taskId] < 0)
					sPhase[taskId] += period * (-sPhase[taskId] / period + 1);
				
				sPhase[taskId] = sPhase[taskId] % period;
			}
		}
	}
	
	private void ComputeFinishPhase(Task target)
	{
		int targetId = target.getBaseId();
		for (Task task : desc.totalTaskList)
		{
			if (target.getParentTaskId() == task.getParentTaskId())
				continue;

			int taskId = task.getBaseId();
			long period = task.getParentTaskGroup().getPeriod();
		
			long phase = sPhase[taskId] - (target.getEndMax() - target.getStartMax());
			
			if (target.isPreemptable() && 
				task.getMappedProcID() == target.getMappedProcID() && task.higherPriorThan(target))
			{
				if (phase < 0)
					phase += period * (-phase / period + 1);
				
				phase = phase % period;
			}
			
			if (fPhase[targetId][taskId] != phase)
			{
				changed = true;
				fPhase[targetId][taskId] = phase;
			}
		}
	}
	
	private void printSchedule()
	{
		if (!Param.DEBUG_ESTIMATION)
			return;
		
		debug.println("Proc size : " + desc.totalProcessorList.size() );
		
		for (int i = 0 ; i < desc.totalProcessorList.size() ; ++i)
		{
			Processor proc = desc.totalProcessorList.get(i);
			debug.println("Proc["+i+"] ");
		
			for (Task s : proc.getScanOrder())
			{	
				debug.println(
						"s[\t"+s.getParentTaskGroup().getInstanceID()+", \t"+s.getBaseId()+"\t"+s.getId()+"]\t"+
						"PR["+s.getPriority()+"]\t"+
						"R["+s.getReleaseMin()+",\t"+s.getReleaseMax()+"]\t"+
						"S["+s.getStartMin()+",\t"+s.getStartMax()+",\t"+s.getFinalStart()+"]\t"+				
						"E["+s.getEndMin()+",\t"+s.getEndMax()+",\t"+s.getFinalFin()+"]\t"+
						"C["+s.getComputationLower()+",\t"+s.getComputationUpper()+"]"+"]\t"+
						"mPL["+BestPreemptorSet[s.id].toString()+"]"+
						"MPL["+WorstPreemptorSet[s.id].toString()+"]"+
						"EL["+ExcludeSet[s.id].toString()+"]");
			}
		}
	}
	
	public long getWCRT(int targetID)
	{
		long WCET = -1;
		
		TaskGroupSet taskGroupSet = desc.totalTaskGroupSetList.getFromBaseId(targetID);
		for (int i = 0 ; i < taskGroupSet.getTaskGroupList().size(); ++i)
		{	
			long minRelease = Integer.MAX_VALUE;
			long maxEnd = -1;

			TaskGroup taskGroup = taskGroupSet.getTaskGroupList().get(i);
			for (int j = 0 ; j < taskGroup.getTaskList().size() ; ++j)
			{
				Task task = taskGroup.getTaskList().get(j);
				long tempMinRelease = task.getReleaseMin();
				if (task.isItSource())
					tempMinRelease = task.getInitialReleaseMin();
				
				if (minRelease > tempMinRelease)
					minRelease = tempMinRelease;
				if (maxEnd < task.getFinalFin())
					maxEnd = task.getFinalFin();
			}
			//taskGroup.setWcetMinMax(maxEnd - minRelease);
			if (WCET < maxEnd - minRelease)
				WCET = maxEnd - minRelease;
		}
		//if (targetID == desc.targetTaskIdGroupOriginal)
		//	taskGroupSet.setWcetMinMax(WCET);
		
		return WCET;
	}
	
	public int[][] getMaxPreemptCount()
	{
		return maxPreemptCount;
	}
	
	private boolean checkError()
	{
		for(int i= 0 ; i< desc.totalTaskList.size() ; i++)
		{
			Task target = desc.totalTaskList.get(i);
				double mr = target.getReleaseMin();
				double mR = target.getReleaseMax();
				double ms = target.getStartMin();
				double mS = target.getStartMax();
				double mf = target.getEndMin();
				double mF = target.getEndMax();
				double bcet = target.getComputationLower();
				double wcet = target.getComputationUpper();

				if(mr>mR)
				{
					System.err.println("ERR01 mr>mR");
					System.err.println(target.getScheduleInfoString());
					return false;
				}
				if(ms > mS)
				{
					System.err.println("ERR02 ms > mS");
					System.err.println(target.getScheduleInfoString());
					
					return false;
				
				}
				if(mf > mF)
				{
					System.err.println("ERR03 mf > mF");
					System.err.println(target.getScheduleInfoString());
					return false;
				}

				if((ms + bcet) > mf)
				{
					System.err.println("ERR04 (ms + bcet) > mf");
					System.err.println(target.getScheduleInfoString());
					return false;
				}
				if((mS + wcet) > mF)
				{
					System.err.println("ERR05 (mS + wcet) > mF");
					System.err.println(target.getScheduleInfoString());
					return false;
				}
		}
		
		return true;
	}
}