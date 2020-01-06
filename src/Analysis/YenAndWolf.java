package Analysis;

import java.util.*;

import Schedule.*;

public class YenAndWolf {

	private Data.Description desc;
	private Schedule schedule;
	
	protected int stepCount;
	protected boolean changed;
	
	protected int taskCount;
	protected ArrayList<Task> topologicalOrder;
	
	protected long[] earliest;
	protected long[] latest;
	protected long[][] rPhase;
	protected long[][] fPhase;
	protected long[][] upper;
	protected long[][] lower;
	protected long[][] maxsep;
	protected long[] rDiff;
	protected long[] rDiff_next;
	
	protected long[] ET;
	protected long[] LT;
	protected long[] WCRT;

	private BitSet[] SuccessorGroups;
	
	public YenAndWolf(Data.Description desc)
	{
		this.desc = desc;
		this.schedule = new Schedule(desc);
		
		taskCount = schedule.getTotalTaskCount();
		
		topologicalOrder = new ArrayList<Task>();
		earliest = new long[taskCount * 2];
		latest = new long[taskCount * 2];
		rPhase = new long[taskCount][taskCount];
		fPhase = new long[taskCount][taskCount];
		upper = new long[taskCount * 2][taskCount * 2];
		lower = new long[taskCount * 2][taskCount * 2];
		maxsep = new long[taskCount * 2][taskCount * 2];
		rDiff = new long[taskCount];
		rDiff_next = new long[taskCount];
		
		ET = new long[taskCount * 2];
		LT = new long[taskCount * 2];
		WCRT = new long[schedule.getGraphCount()];

		SuccessorGroups = new BitSet[taskCount];
		for (int i = 0; i < taskCount; ++i)
			SuccessorGroups[i] = new BitSet(taskCount);
	}
	
	public boolean go()
	{
		boolean error = false;
		
		long startTime = System.currentTimeMillis();
		{
			error = Estimation(200);
		}
		long endTime = System.currentTimeMillis();
		PrintWCRT();
		
		return error;
	}
	
	protected boolean Estimation(int limit)
	{
		Initialize();
		
		do
		{
			changed = false;
			
			ResetTimes();
			
			for (int i = 0; i < taskCount; ++i)
			{
				TopologicalSort(i);
				
				EarliestTimes(i);
				LatestTimes(i);	 
				
				CalcLowerBound(i);
				CalcUpperBound(i);

				CalcRequestDifference();
				
				UpdateTimes(i);
			}
			CalcDerivedBound();
			
			TopologicalSort();
			for (int i = 0; i < taskCount; ++i)
				MaxSeparation(i);
			MaxSeparationRR();
			
			UpdateRequestDifference();
			
			PrintSchedule(stepCount);
			
			++stepCount;
		}
		while (changed && stepCount < limit);
		
		return !CheckDeadline() || stepCount == limit;
	}
	
	protected void Initialize()
	{
		stepCount = 0;
		
		for (int i = 0; i < taskCount * 2; ++i)
		{
			for (int j = 0; j < taskCount * 2; ++j)
			{
				lower[i][j] = -Integer.MAX_VALUE;
				upper[i][j] = -Integer.MAX_VALUE;
				maxsep[i][j] = Integer.MAX_VALUE;
			}
		}
		
		for (int i = 0; i < taskCount; ++i)
		{
			Task task = schedule.getTask(i);
			rDiff[i] = task.getOwnerInstance().getGraph().getPeriod() - task.getWCET();
			rDiff_next[i] = Integer.MIN_VALUE;
		}
		
		InitSuccessorGroups();
	}
	
	protected void InitSuccessorGroups()
	{
		int[] isVisitable = new int[taskCount];
		for (int i = 0; i < taskCount; ++i)
		{
			Task task = schedule.getTask(i);
			isVisitable[task.getID()] = task.getChildCount();
		}

		Queue<Task> visitQueue = new LinkedList<Task>();
		for (int i = 0; i < taskCount; ++i)
		{
			Task task = schedule.getTask(i);
			if (task.isSink())
				visitQueue.offer(task);
		}

		while (!visitQueue.isEmpty())
		{
			Task task = visitQueue.poll();
			for (Iterator<Task> it = task.getChildIterator(); it.hasNext();)
			{
				Task predecessor = it.next();
				SuccessorGroups[predecessor.getID()].or(SuccessorGroups[task.getID()]);
				SuccessorGroups[predecessor.getID()].set(task.getID());
				
				if (--isVisitable[predecessor.getID()] == 0)
					visitQueue.offer(predecessor);
			}
		}
	}
	
	protected void EarliestTimes(int taskID)
	{
		for (int i = 0; i < taskCount; ++i)
		{
			earliest[request(i)] = 0;
			for (int j = 0; j < taskCount; ++j)
				rPhase[i][j] = EarliestPhaseAdjustment(i, j, 0);
		}
		
		for (int t = 0; t < topologicalOrder.size(); ++t)
		{
			Task task_i = topologicalOrder.get(t);
			int i = task_i.getID();
			
			long w = BCRT(i);
			earliest[finish(i)] = earliest[request(i)] + w;
			
			for (int j = 0; j < taskCount; ++j)
			{
				// i == j 인 경우?
				// fPhase가 (-pj, 0] 범위이면 rPhase계산도 음수가 나오게 된다.
				// 그럼 BCRT 게산에서 preemption이 더 발생해버리는데?
				
				Task task_j = schedule.getTask(j);
				
				if (task_i.getMappedPE() != task_j.getMappedPE())
				{
					fPhase[i][j] = 0;
					continue;
				}
				
				long period_j = task_j.getOwnerInstance().getGraph().getPeriod();
				
				fPhase[i][j] = -(rPhase[i][j] - w);
				
				if (i == j || IsPreemptable(task_j, task_i))
				{
					if (fPhase[i][j] < 0)
						fPhase[i][j] += period_j * (-fPhase[i][j] / period_j + 1);
					
					fPhase[i][j] = fPhase[i][j] % period_j;
				}
				
				fPhase[i][j] = -Math.max(fPhase[i][j], 0);
				
			}
				
			for (Iterator<Task> it = task_i.getChildIterator(); it.hasNext(); )
			{
				Task task_k = it.next();
				int k = task_k.getID();
				
				if (earliest[request(k)] < earliest[finish(i)])
				{
					earliest[request(k)] = earliest[finish(i)];
					
					for (int j = 0; j < taskCount; ++j)
					{
						if (k == j)
							continue;
						
						// i == j 인 경우?
						
						rPhase[k][j] = EarliestPhaseAdjustment(k, j,
								fPhase[i][j]);
					}
				}
			}
		}
	}
	
	protected void LatestTimes(int taskID)
	{
		for (int i = 0; i < taskCount; ++i)
		{
			latest[request(i)] = 0;
			for (int j = 0; j < taskCount; ++j)
				rPhase[i][j] = LatestPhaseAdjustment(i, j, 0);
		}
		
		for (int t = 0; t < topologicalOrder.size(); ++t)
		{	
			Task task_i = topologicalOrder.get(t);
			int i = task_i.getID();
			long w = WCRT(i);
			latest[finish(i)] = latest[request(i)] + w;
			
			for (int j = 0; j < taskCount; ++j)
			{
				// i == j 인 경우?
				
				Task task_j = schedule.getTask(j);
				
				long period_j = task_j.getOwnerInstance().getGraph().getPeriod();
				
				fPhase[i][j] = rPhase[i][j] - w;
				
				if (i == j || IsPreemptable(task_j, task_i))
				{
					if (fPhase[i][j] < 0)
						fPhase[i][j] += period_j * (-fPhase[i][j] / period_j + 1);
					
					fPhase[i][j] = fPhase[i][j] % period_j;
				}
				
				fPhase[i][j] = Math.max(fPhase[i][j], 0);
			}
			
			for (Iterator<Task> it = task_i.getChildIterator(); it.hasNext();)
			{
				Task task_k = it.next();
				int k = task_k.getID();
				
				// delta로 increase하는 부분은 이해안되서 나름대로 수정해봄
				// rPhase[i][j] = mink (fPhase[k][j])
				// k is a immediate predecessor of i
				// predecessor의 fPhase중에서 가장 작은 값을 선택
				
				if (latest[request(k)] == 0) // first visit
				{
					for (int j = 0; j < taskCount; ++j)
					{
						if (k == j)
							continue;

						// i == j 인 경우?
						
						rPhase[k][j] = LatestPhaseAdjustment(k, j, fPhase[i][j]);
					}
				}
				
				if (latest[request(k)] < latest[finish(i)])
					latest[request(k)] = latest[finish(i)];
				
				for (int j = 0; j < taskCount; ++j)
				{
					if (k == j)
						continue;
					
					// i == j 인 경우?
		
					rPhase[k][j] = LatestPhaseAdjustment(k, j,
							Math.min(rPhase[k][j], fPhase[i][j]));
				}
			}
		}
	}
	
	protected void CalcLowerBound(int taskID)
	{
		int i = taskID;
		for (int t = 0; t < topologicalOrder.size(); ++t)
		{
			Task task_j = topologicalOrder.get(t);
			int j = task_j.getID();
			
			lower[request(i)][request(j)] = earliest[request(j)];
			lower[request(i)][finish(j)] = earliest[finish(j)];
		}
	}
	
	protected void CalcUpperBound(int taskID)
	{
		int i = taskID;
		for (int t = 0; t < topologicalOrder.size(); ++t)
		{
			Task task_j = topologicalOrder.get(t);
			int j = task_j.getID();
			
			upper[request(i)][request(j)] = latest[request(j)];
			upper[request(i)][finish(j)] = latest[finish(j)];
		}
	}
	
	protected void CalcDerivedBound()
	{
		for (int i = 0; i < taskCount; ++i)
		{
			Task task_i = schedule.getTask(i);
			for (int j = 0; j < taskCount; ++j)
			{
				if (i == j)
					continue;
			
				lower[finish(i)][request(j)] = -Integer.MAX_VALUE;
				for (Iterator<Task> it = task_i.getChildIterator(); it.hasNext();)
				{
					Task task_k = it.next();
					int k = task_k.getID();
			
					if (lower[finish(i)][request(j)] < lower[request(k)][request(j)])
						lower[finish(i)][request(j)] = lower[request(k)][request(j)];
				}
			}
		}
	}
	
	protected void MaxSeparation(int taskID)
	{
		int i = taskID;
		Task task_i = schedule.getTask(i);

		Queue<Task> queue = new LinkedList<Task>();
		for (int t = 0; t < topologicalOrder.size(); ++t)
		{
		   Task task_j = topologicalOrder.get(t);
		   int j = task_j.getID();
		   
		   if (i == j)
			   continue;
		   
		   if (PredGroupContains(task_i, task_j))
		   {
			   if (maxsep[request(i)][finish(j)] != -lower[finish(j)][request(i)])
			   {
				   maxsep[request(i)][finish(j)] = -lower[finish(j)][request(i)];
				   changed = true;
			   }
			   continue;
	       }
		   if (SuccGroupContains(task_i, task_j))
		   {
			   if (maxsep[request(i)][finish(j)] != upper[request(i)][finish(j)])
			   {
				   maxsep[request(i)][finish(j)] = upper[request(i)][finish(j)];
				   changed = true;
			   }
			   continue;
		   }
	         
		   queue.clear();
		   queue.offer(task_j);
		   long max_sep = Integer.MIN_VALUE;

		   while (!queue.isEmpty())
		   {
		      Task task_k = queue.poll();
		      int k = task_k.getID();
		      
		      for (Iterator<Task> it = task_k.getParentIterator(); it.hasNext();)
		      {
		         Task task_l = it.next();
		         int l = task_l.getID();

		         if (PredGroupContains(task_i, task_l))
		         {
		        	 max_sep = Math.max(max_sep,
		        			 upper[request(k)][finish(j)] - lower[finish(l)][request(i)]);
		            
		            continue;
		         }
		         
		         if (!queue.contains(task_l))
		            queue.offer(task_l);
		      }
		   }
		   if (max_sep == Integer.MIN_VALUE)
			   max_sep = Integer.MAX_VALUE;
		   
		   if (maxsep[request(i)][finish(j)] != max_sep)
		   {
			   maxsep[request(i)][finish(j)] = max_sep;
			   changed = true;
		   }
		}
	}
	
	protected void MaxSeparationRR()
	{	
		for (int i = 0; i < taskCount; ++i)
		{	
			for (int j = 0; j < taskCount; ++j)
			{
				if (i == j)
					continue;

				Task task_j = schedule.getTask(j);
				
				// max_sep이 Infinite인 경우를 허용?
				// incommingEdge가 없는 경우
				// incommingEdge는 1개 있고, 그 1개가 i인 경우
				long max_sep = Integer.MIN_VALUE;
				for (Iterator<Task> it = task_j.getParentIterator(); it.hasNext();)
				{
					Task task_k = it.next();
					int k = task_k.getID();
				
					if (i == k)
						continue;
					
					if (max_sep < maxsep[request(i)][finish(k)])
						max_sep = maxsep[request(i)][finish(k)];
				}
				
				if (max_sep == Integer.MIN_VALUE)
					max_sep = Integer.MAX_VALUE;
				
				if (maxsep[request(i)][request(j)] != max_sep)
				{
					maxsep[request(i)][request(j)] = max_sep;
					changed = true;
				}
			}
		}
	}
	
	protected long EarliestPhaseAdjustment(int i, int j, long value)
	{
		Task task_j = schedule.getTask(j);
		
		// 좀 이해안되는 부분. 나름대로 수정해봄
		if (maxsep[request(j)][request(i)] > 0)
			return value;
		
		long period_j = task_j.getOwnerInstance().getGraph().getPeriod();
		
		return Math.min(value,
				-maxsep[request(j)][request(i)] - period_j);
	}
	
	protected long LatestPhaseAdjustment(int i, int j, long value)
	{
		return Math.max(value,
				-maxsep[request(j)][request(i)]);
	}
	
	protected void CalcRequestDifference()
	{
		// rDiff의 사용에 대해 논문에 자세히 나와있지 않다.
		for (int i = 0; i < taskCount; ++i)
		{
			long rDiff_i = latest[request(i)] - earliest[request(i)];
			
			if (rDiff_next[i] < rDiff_i)
				rDiff_next[i] = rDiff_i;
		}
	}
	
	protected void UpdateRequestDifference()
	{
		for (int i = 0; i < taskCount; ++i)
		{
			Task task = schedule.getTask(i);
			long WCRD = task.getOwnerInstance().getGraph().getPeriod() - task.getWCET();
			
			rDiff[i] = Math.min(WCRD, rDiff_next[i]);
			rDiff_next[i] = Integer.MIN_VALUE;
		}
	}
	
	// sort full graph G
	protected void TopologicalSort()
	{
		Queue<Task> queue = new LinkedList<Task>();
		
		for (int i = 0; i < taskCount; ++i)
		{
			Task task = schedule.getTask(i);
			task.setScheduled(false);
			
			if (task.getParentCount() == 0)
			{
				task.setScheduled(true);
				queue.offer(task);
			}
		}
		
		_TopologicalSort(queue);
	}
	
	// sort sub graph Gi
	protected void TopologicalSort(int taskID)
	{
		Queue<Task> queue = new LinkedList<Task>();
		
		for (int i = 0; i < taskCount; ++i)
		{
			Task task = schedule.getTask(i);
			task.setScheduled(true);
		}
		_VisitSuccessors(taskID);
		
		Task task = schedule.getTask(taskID);
		task.setScheduled(true);
		queue.offer(task);
		
		_TopologicalSort(queue);
	}
	
	private void _TopologicalSort(Queue<Task> queue)
	{
		topologicalOrder.clear();
		
		while (!queue.isEmpty())
		{
			Task task = queue.poll();
			topologicalOrder.add(task);
			
			for (Iterator<Task> it = task.getChildIterator(); it.hasNext();)
			{
				Task out_task = it.next();
				if (!out_task.isScheduled())
				{
					boolean schedulable = true;
					for (Iterator<Task> it2 = out_task.getParentIterator(); it2.hasNext();)
					{
						if (!it2.next().isScheduled())
						{
							schedulable = false;
							break;
						}
					}
					
					if (schedulable)
					{
						out_task.setScheduled(true);
						queue.offer(out_task);
					}
				}
			}
		}
	}
	
	private void _VisitSuccessors(int taskID)
	{
		Queue<Task> queue = new LinkedList<Task>();
		
		Task task = schedule.getTask(taskID);
		task.setScheduled(false);
		queue.offer(task);
		
		while (!queue.isEmpty())
		{
			task = queue.poll();
			
			for (Iterator<Task> it = task.getChildIterator(); it.hasNext();)
			{
				Task out_task = it.next();
				if (out_task.isScheduled())
				{
					out_task.setScheduled(false);
					queue.offer(out_task);
				}
			}
		}
	}
	
	protected long BCRT(int i)
	{
		Task task_i = schedule.getTask(i);
		
		long x = task_i.getBCET();
		long gx = x;
		do
		{
			x = gx;
			
			long gsum = 0;
			for (int j = 0; j < taskCount; ++j)
			{
				if (i == j)
					continue;
				
				Task task_j = schedule.getTask(j);
				if (IsPreemptable(task_j, task_i))
				{
					long period_j = task_j.getOwnerInstance().getGraph().getPeriod();
					gsum += task_j.getBCET() *
							Math.floor((double)Math.max(0, x - rPhase[i][j] - rDiff[j])
										/ period_j);
				}
			}
			
			gx = task_i.getBCET() + gsum;
			if (x > task_i.getOwnerInstance().getGraph().getDeadline())
				break;
		}
		while (x < gx);
		
		return x;
	}
	
	protected long WCRT(int i)
	{	
		Task task_i = schedule.getTask(i);

		long x = task_i.getWCET();
		long gx = x;
		do
		{
			x = gx;
			
			long gsum = 0;
			for (int j = 0; j < taskCount; ++j)
			{
				if (i == j)
					continue;
				
				Task task_j = schedule.getTask(j);
				double mpc;
				if (IsPreemptable(task_j, task_i))
				{	
					long period_j = task_j.getOwnerInstance().getGraph().getPeriod();
					
					gsum += task_j.getWCET() *
							Math.ceil((double)Math.max(0, x - rPhase[i][j] + rDiff[j])/ period_j);
				}
			}
			
			gx = task_i.getWCET() + gsum;
			if (x > task_i.getOwnerInstance().getGraph().getDeadline())
				break;
		}
		while (x < gx);
		
		return x;
	}
	
	protected boolean IsPreemptable(Task lhs, Task rhs)
	{
		int lhsID = lhs.getID();
		int rhsID = rhs.getID();
		
		return lhs.getMappedPE() == rhs.getMappedPE() &&
				lhs.hasHigherPriorityThan(rhs) &&
				(maxsep[request(lhsID)][finish(rhsID)] > 0 &&
				 maxsep[request(rhsID)][finish(lhsID)] > 0);
	}
	
	protected void ResetTimes()
	{
		for (int i = 0; i < taskCount; ++i)
		{
			ET[request(i)] = ET[finish(i)] = 0;
			LT[request(i)] = LT[finish(i)] = 0;
			earliest[request(i)] = earliest[finish(i)] = 0;
			latest[request(i)] = latest[finish(i)] = 0;
		}
		
		for (int i = 0; i < schedule.getGraphCount(); ++i)
			WCRT[i] = 0;
	}
	
	protected void UpdateTimes(int taskID)
	{
		Task task = schedule.getTask(taskID);
		
		if (task.getParentCount() == 0)
		{
			Graph graph = task.getOwnerInstance().getGraph();
			int graphID = graph.getID();
			
			for (Iterator<Task> it = graph.getTaskIterator(); it.hasNext();)
			{
				task = it.next();
				taskID = task.getID();
				
				if (ET[request(taskID)] < earliest[request(taskID)])
					ET[request(taskID)] = earliest[request(taskID)];
				if (ET[finish(taskID)] < earliest[finish(taskID)])
					ET[finish(taskID)] = earliest[finish(taskID)];
				
				if (LT[request(taskID)] < latest[request(taskID)])
					LT[request(taskID)] = latest[request(taskID)];
				if (LT[finish(taskID)] < latest[finish(taskID)])
					LT[finish(taskID)] = latest[finish(taskID)];
				
				if (WCRT[graphID] < latest[finish(taskID)])
					WCRT[graphID] = latest[finish(taskID)];
			}
		}
	}
	
	private boolean CheckDeadline()
	{
		for (int i = 0; i < schedule.getGraphCount(); ++i)
		{
			if (WCRT[i] > schedule.getGraph(i).getDeadline())
				return false;
		}
		return true;
	}
	
	protected void PrintSchedule(int stepCount)
	{
		System.out.println("Iteration " + stepCount);
		
		for (int i = 0; i < topologicalOrder.size(); ++i)
		{
			Task task = topologicalOrder.get(i);
			
			System.out.print("ID(" + task.getID() + ")  ");
			System.out.print("Request(" + ET[request(task.getID())] + ", " + LT[request(task.getID())] + ")  ");
			System.out.print("Finish(" + ET[finish(task.getID())]+ ", " + LT[finish(task.getID())] + ")  ");
			System.out.println();
		}
	}
	
	protected void PrintWCRT()
	{	
		for (int i = 0; i < schedule.getGraphCount(); ++i)
			System.out.println("*** WCET of TaskID["+i+"] - Yen&Wolf Result -: " + WCRT[i]);
	}
	
	protected int request(int taskID)
	{
		return taskID * 2 + 0;
	}
	
	protected int finish(int taskID)
	{
		return taskID * 2 + 1;
	}
	
	protected boolean SuccGroupContains(Task lhs, Task rhs)
	{
		return SuccessorGroups[lhs.getID()].get(rhs.getID());
	}
	
	protected boolean PredGroupContains(Task lhs, Task rhs)
	{
		return SuccessorGroups[rhs.getID()].get(lhs.getID());
	}
	
	public long getWCRT(int targetID)
	{
		return WCRT[targetID];
	}
}
