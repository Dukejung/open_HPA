package SharedResource;

import java.util.*;

import Data.ResourceAccessPattern;
import Schedule.*;

public class ContentionModeller {
	private Schedule schedule;

	long[][] dist;

	private BitSet[] SuccessorGroups;
	
	boolean noClustering = false;
	boolean noConvolution = false;
	
	private Object[][][] ClusterSets;
	
	public ContentionModeller(Schedule schedule, boolean clustering, boolean convolution)
	{
		this.schedule = schedule;

		noClustering = !clustering;
		noConvolution = !convolution;
		
		dist = new long[schedule.getTaskCount()][schedule.getTaskCount()];
		
		SuccessorGroups = new BitSet[schedule.getTaskCount()];
		for (int i = 0; i < schedule.getTaskCount(); ++i)
			SuccessorGroups[i] = new BitSet(schedule.getTaskCount());

		InitSuccessorGroups();
		
		ClusterSets = new Object[schedule.getTaskCount()][schedule.getSharedResourceCount()][schedule.getPECount()];
	}
	
	protected void InitSuccessorGroups()
	{
		int[] isVisitable = new int[schedule.getTaskCount()];
		for (int i = 0; i < schedule.getTaskCount(); ++i)
			isVisitable[schedule.getTask(i).getID()] = schedule.getTask(i).getChildCount();

		Queue<Task> visitQueue = new LinkedList<Task>();
		for (int i = 0; i < schedule.getTaskCount(); ++i)
			if (schedule.getTask(i).isSink())
				visitQueue.offer(schedule.getTask(i));

		while (!visitQueue.isEmpty())
		{
			Task task = visitQueue.poll();
			for (Iterator<Task> it = task.getParentIterator(); it.hasNext();)
			{
				Task predecessor = it.next();
				SuccessorGroups[predecessor.getID()].or(SuccessorGroups[task.getID()]);
				SuccessorGroups[predecessor.getID()].set(task.getID());
				
				if (--isVisitable[predecessor.getID()] == 0)
					visitQueue.offer(predecessor);
			}
		}
	}
	
	public void Update()
	{
		SeparationAnalysis();
		
		for (int i = 0; i < schedule.getTaskCount(); ++i)
		{
			Task target = schedule.getTask(i);
			for (int resourceID = 0; resourceID < schedule.getSharedResourceCount(); ++resourceID)
			{
				for (int procID = 0; procID < schedule.getPECount(); ++procID)
				{
					if (procID == target.getMappedPE().getID())
						continue;

					ClusterSets[i][resourceID][procID] = TaskClustering(target, procID, resourceID);
				}
			}
		}
	}

	void SeparationAnalysis()
	{
		for (int i = 0; i < schedule.getTaskCount(); ++i)
		for (int j = 0; j < schedule.getTaskCount(); ++j)
			dist[i][j] = Integer.MIN_VALUE;

		for (int i = 0; i < schedule.getTaskCount(); ++i)
		for (int j = 0; j < schedule.getTaskCount(); ++j)
			RecursiveCalcDistance(i, j);
	}

	long RecursiveCalcDistance(int i, int j)
	{
		if (dist[i][j] != Integer.MIN_VALUE)
			return dist[i][j];

		Task task_i = schedule.getTask(i);
		Task task_j = schedule.getTask(j);

		if (task_i.getOwnerInstance().getGraph() != task_j.getOwnerInstance().getGraph())
		{
			dist[i][j] = Integer.MIN_VALUE;
		}
		else if (i == j)
		{
			dist[i][i] = task_i.getReleaseMin() - task_i.getFinishMin();
		}
		else if (SuccGroupContains(task_i, task_j))
		{
			for (Iterator<Task> it = task_i.getChildIterator(); it.hasNext();)
			{
				Task task_k = it.next();
				int succID = task_k.getID();

				long distance = -RecursiveCalcDistance(succID, succID) + RecursiveCalcDistance(succID, j);
				if (dist[i][j] < distance)
					dist[i][j] = distance;
			}
		}
		else
			dist[i][j] = task_j.getReleaseMin() - task_i.getFinishMax();

		return dist[i][j];
	}

	Vector<ArrayList<Task>> TaskClustering(Task target, int procID, int resourceID)
	{
		Vector<ArrayList<Task>> taskClusters = new Vector<ArrayList<Task>>();

		ArrayList<Task> candidates = new ArrayList<Task>();
		for (int i = 0; i < schedule.getTaskCount(); ++i)
		{
			Task task = schedule.getTask(i);
			
			if (procID != task.getMappedPE().getID())
				continue;
			if (target.hasHigherPriorityThan(task))
				continue;
			if (task.getResourceAccessPattern(resourceID) == null)
				continue;

			if (!noClustering)
			{
				if (dist[task.getID()][target.getID()] >= 0 ||
					dist[target.getID()][task.getID()] >= 0)
					continue;
			}

			candidates.add(task);
		}

		if (noClustering)
		{
			for (Task task : candidates)
			{
				ArrayList<Task> cluster = new ArrayList<Task>();
				cluster.add(task);
				
				taskClusters.add(cluster);
			}
			return taskClusters;
		}
		
		while (!candidates.isEmpty())
		{
			ArrayList<Task> taskCluster = new ArrayList<Task>();
			taskCluster.add(candidates.get(0));
			candidates.remove(0);

			while (true)
			{
				Task newLeft = null, newRight = null;
				int left = taskCluster.get(0).getID();
				int right = taskCluster.get(taskCluster.size()-1).getID();

				for (int i = 0; i < candidates.size(); ++i)
				{
					Task task = candidates.get(i);
					if (dist[task.getID()][left] >= 0)
					{
						if (newLeft == null || dist[task.getID()][left] < dist[newLeft.getID()][left])
							newLeft = task;
					}
					if (dist[right][task.getID()] >= 0)
					{
						if (newRight == null || dist[right][task.getID()] < dist[right][newRight.getID()])
							newRight = task;
					}
				}

				if (newLeft == null && newRight == null)
					break;

				if (newLeft != null)
				{
					candidates.remove(newLeft);
					taskCluster.add(0, newLeft);
				}
				if (newRight != null)
				{
					candidates.remove(newRight);
					taskCluster.add(taskCluster.size(), newRight);
				}
			}

			taskClusters.add(taskCluster);
		}

		return taskClusters;
	}

	long GetResourceDemand(ArrayList<Task> taskCluster, int resourceID, long time)
	{
		long demandBound = 0;
		for (int i = 0; i < taskCluster.size(); ++i)
		{
			int resourceN = taskCluster.get(i).getResourceAccessPattern(resourceID).n;

			for (int j = 0; j < resourceN; j+=resourceN-1)
			{
				int startAccess = j;
				int currentTask = i;
				int nextTask = (i+1)%taskCluster.size();
				long remainTime = time;
				
				long demandAmount = 0;
				while (remainTime > 0)
				{
					ResourceAccessPattern accessPattern = taskCluster.get(currentTask).getResourceAccessPattern(resourceID);
					
					long timeToNextTask = ((accessPattern.n-startAccess)*(accessPattern.w+accessPattern.d))-accessPattern.d;
					if (currentTask != i)
						timeToNextTask += Math.max(0,
								(long)taskCluster.get(currentTask).getBCET() -
								(long)(accessPattern.n*(accessPattern.w+accessPattern.d)+accessPattern.d));

					if (nextTask != 0)
					{
						timeToNextTask += dist[taskCluster.get(currentTask).getID()][taskCluster.get(nextTask).getID()];
					}
					else
					{
						Task task0 = taskCluster.get(nextTask);
						Task taskN = taskCluster.get(currentTask);

						timeToNextTask += Math.max(0, task0.getOwnerInstance().getGraph().getPeriod() + 
													task0.getReleaseMin() - taskN.getFinishMax());
					}

					if (remainTime <= timeToNextTask)
					{
						long inTCount = Math.min(accessPattern.n-startAccess, remainTime/(accessPattern.w+accessPattern.d));
						demandAmount += (long)inTCount*accessPattern.w;
						if (inTCount < accessPattern.n-startAccess)
							demandAmount += Math.min(accessPattern.w, remainTime- (long)inTCount*(accessPattern.w+accessPattern.d));

						remainTime = 0;
					}
					else
					{
						demandAmount += (long)(accessPattern.n-startAccess)*accessPattern.w;

						remainTime -= timeToNextTask;
						startAccess = 0;
						currentTask = nextTask;
						nextTask = (currentTask+1)%taskCluster.size();
					}
				}
				
				if (demandBound < demandAmount)
					demandBound = demandAmount;
			}
		}
		return demandBound;
	}
	
	protected boolean SuccGroupContains(Task lhs, Task rhs)
	{
		return SuccessorGroups[lhs.getID()].get(rhs.getID());
	}
	
	protected boolean PredGroupContains(Task lhs, Task rhs)
	{
		return SuccessorGroups[rhs.getID()].get(lhs.getID());
	}
	
	public long getValue(int targetID, int resourceID, long time)
	{
		long ret = 0;
		for (int i = 0; i < schedule.getPECount(); ++i)
		{
			Vector<ArrayList<Task>> clusterSet = (Vector<ArrayList<Task>>)ClusterSets[targetID][resourceID][i];
			if (clusterSet != null)
			{
				for (int j = 0; j < clusterSet.size(); ++j)
					ret += GetResourceDemand(clusterSet.get(j), resourceID, time);
			}
		}
		
		return ret;
	}
}

//public class ResourceContentionModel {
//	private Description desc;
//
//	int[][] dist;
//	DemandCurve[][][] curves;
//
//	private BitSet[] SuccessorGroups;
//	
//	static int time_unit = 1;
//
//	boolean noClustering = false;
//	boolean noConvolution = false;
//	
//	public ResourceContentionModel(Description desc, boolean clustering, boolean convolution)
//	{
//		this.desc = desc;
//
//		noClustering = !clustering;
//		noConvolution = !convolution;
//		
//		dist = new int[desc.totalTaskList.size()][desc.totalTaskList.size()];
//
//		curves = new DemandCurve[desc.totalTaskList.size()][][];
//		for (int i = 0; i < desc.totalTaskList.size(); ++i)
//		{
//			curves[i] = new DemandCurve[desc.numberOfSharedResources][];
//			for (int j = 0; j < desc.numberOfSharedResources; ++j)
//			{
//				curves[i][j] = new DemandCurve[desc.totalProcessorList.size()];
//				for (int k = 0; k < desc.totalProcessorList.size(); ++k)
//					curves[i][j][k] = new DemandCurve();
//			}
//		}
//		
//		SuccessorGroups = new BitSet[desc.totalTaskList.size()];
//		for (int i = 0; i < desc.totalTaskList.size(); ++i)
//			SuccessorGroups[i] = new BitSet(desc.totalTaskList.size());
//
//		InitSuccessorGroups();
//	}
//	
//	protected void InitSuccessorGroups()
//	{
//		int[] isVisitable = new int[desc.totalTaskList.size()];
//		for (Task task : desc.totalTaskList)
//			isVisitable[task.getBaseId()] = task.getOutgoingEdges().size();
//
//		Queue<Task> visitQueue = new LinkedList<Task>();
//		for (Task task : desc.totalTaskList)
//			if (task.isItSink())
//				visitQueue.offer(task);
//
//		while (!visitQueue.isEmpty())
//		{
//			Task task = visitQueue.poll();
//			for (Task predecessor : task.getIncommingEdges())
//			{
//				SuccessorGroups[predecessor.getBaseId()].or(SuccessorGroups[task.getBaseId()]);
//				SuccessorGroups[predecessor.getBaseId()].set(task.getBaseId());
//				
//				if (--isVisitable[predecessor.getBaseId()] == 0)
//					visitQueue.offer(predecessor);
//			}
//		}
//	}
//	
//	public void Update()
//	{
//		SeparationAnalysis();
//		
//		for (int i = 0; i < desc.totalTaskList.size(); ++i)
//		{
//			Task target = desc.totalTaskList.get(i);
//			for (int resourceID = 0; resourceID < desc.numberOfSharedResources; ++resourceID)
//			{
//				for (int procID = 0; procID < desc.totalProcessorList.size(); ++procID)
//				{
//					if (procID == target.getMappedProcID())
//						continue;
//
//					Vector<ArrayList<Task>> taskClusters = TaskClustering(target, procID, resourceID);
//					DerivePEResourceDemandCurve(target, procID, resourceID, taskClusters);
//				}
//			}
//		}
//	}
//
//	void SeparationAnalysis()
//	{
//		for (int i = 0; i < desc.totalTaskList.size(); ++i)
//		for (int j = 0; j < desc.totalTaskList.size(); ++j)
//			dist[i][j] = Integer.MIN_VALUE;
//
//		for (int i = 0; i < desc.totalTaskList.size(); ++i)
//		for (int j = 0; j < desc.totalTaskList.size(); ++j)
//			RecursiveCalcDistance(i, j);
//	}
//
//	int RecursiveCalcDistance(int i, int j)
//	{
//		if (dist[i][j] != Integer.MIN_VALUE)
//			return dist[i][j];
//
//		Task task_i = desc.totalTaskList.get(i);
//		Task task_j = desc.totalTaskList.get(j);
//
//		if (task_i.getParentTaskId() != task_j.getParentTaskId())
//		{
//			dist[i][j] = Integer.MIN_VALUE;
//		}
//		else if (i == j)
//		{
//			dist[i][i] = task_i.getReleaseMin() - task_i.getEndMin();
//		}
//		else if (SuccGroupContains(task_i, task_j))
//		{
//			for (int k = 0; k < task_i.getOutgoingEdges().size(); ++k)
//			{
//				Task task_k = task_i.getOutgoingEdges().get(k);
//				int succID = task_k.getBaseId();
//
//				int distance = -RecursiveCalcDistance(succID, succID) + RecursiveCalcDistance(succID, j);
//				if (dist[i][j] < distance)
//					dist[i][j] = distance;
//			}
//		}
//		else
//			dist[i][j] = task_j.getReleaseMin() - task_i.getEndMax();
//
//		return dist[i][j];
//	}
//
//	Vector<ArrayList<Task>> TaskClustering(Task target, int procID, int resourceID)
//	{
//		Vector<ArrayList<Task>> taskClusters = new Vector<ArrayList<Task>>();
//
//		ArrayList<Task> candidates = new ArrayList<Task>();
//		for (int i = 0; i < desc.totalTaskList.size(); ++i)
//		{
//			Task task = desc.totalTaskList.get(i);
//			
//			if (procID != task.getMappedProcID())
//				continue;
//			if (target.higherPriorThan(task))
//				continue;
//			if (task.getResourceAccessInfo(resourceID) == null)
//				continue;
//
//			if (!noClustering)
//			{
//				if (dist[task.getBaseId()][target.getBaseId()] >= 0 ||
//					dist[target.getBaseId()][task.getBaseId()] >= 0)
//					continue;
//			}
//
//			candidates.add(task);
//		}
//
//		if (noClustering)
//		{
//			for (Task task : candidates)
//			{
//				ArrayList<Task> cluster = new ArrayList<Task>();
//				cluster.add(task);
//				
//				taskClusters.add(cluster);
//			}
//			return taskClusters;
//		}
//		
//		while (!candidates.isEmpty())
//		{
//			ArrayList<Task> taskCluster = new ArrayList<Task>();
//			taskCluster.add(candidates.get(0));
//			candidates.remove(0);
//
//			while (true)
//			{
//				Task newLeft = null, newRight = null;
//				int left = taskCluster.get(0).getBaseId();
//				int right = taskCluster.get(taskCluster.size()-1).getBaseId();
//
//				for (int i = 0; i < candidates.size(); ++i)
//				{
//					Task task = candidates.get(i);
//					if (dist[task.getBaseId()][left] >= 0)
//					{
//						if (newLeft == null || dist[task.getBaseId()][left] < dist[newLeft.getBaseId()][left])
//							newLeft = task;
//					}
//					if (dist[right][task.getBaseId()] >= 0)
//					{
//						if (newRight == null || dist[right][task.getBaseId()] < dist[right][newRight.getBaseId()])
//							newRight = task;
//					}
//				}
//
//				if (newLeft == null && newRight == null)
//					break;
//
//				if (newLeft != null)
//				{
//					candidates.remove(newLeft);
//					taskCluster.add(0, newLeft);
//				}
//				if (newRight != null)
//				{
//					candidates.remove(newRight);
//					taskCluster.add(taskCluster.size(), newRight);
//				}
//			}
//
//			taskClusters.add(taskCluster);
//		}
//
//		return taskClusters;
//	}
//
//	void DerivePEResourceDemandCurve(Task target, int procID, int resourceID, Vector<ArrayList<Task>> taskClusters)
//	{
//		if (noConvolution)
//		{
//			DemandCurveSet curveSet = new DemandCurveSet();
//		
//			for (int i = 0; i < taskClusters.size(); ++i)
//			{
//				DemandCurve clusterCurve = GetClusterCurve(taskClusters.get(i), resourceID);
//				curveSet.add(clusterCurve);
//			}
//			curves[target.getBaseId()][resourceID][procID] = curveSet;
//		}
//		else
//		{
//			DemandCurve curve = new DemandCurve();
//	
//			for (int i = 0; i < taskClusters.size(); ++i)
//			{
//				DemandCurve clusterCurve = GetClusterCurve(taskClusters.get(i), resourceID);
//				curve = DemandCurve.convolution(curve, clusterCurve);
//			}
//			
//			curves[target.getBaseId()][resourceID][procID] = curve;
//		}
//	 }
//
//	DemandCurve GetClusterCurve(ArrayList<Task> taskCluster, int resourceID)
//	{
//		DemandCurve curve = new DemandCurve();
//
//		int prevTimePoint = 0;
//		int t = time_unit;
//
//		boolean isIncrease = false;
//		int prevBound = 0;
//
//		int maxDemand = 0;
//		for (int i = 0; i < taskCluster.size(); ++i)
//		{
//			ResourceAccessInfo accessInfo = taskCluster.get(i).getResourceAccessInfo(resourceID);
//			maxDemand += accessInfo.n * accessInfo.w;
//		}
//
//		while (true)
//		{
//			int demandBound = 0;
//			for (int i = 0; i < taskCluster.size(); ++i)
//			{
//				int resourceN = taskCluster.get(i).getResourceAccessInfo(resourceID).n;
//
//				for (int j = 0; j < resourceN; ++j)
//				{
//					int startAccess = j;
//					int currentTask = i;
//					int nextTask = (i+1)%taskCluster.size();
//					int remainTime = t;
//					
//					int demandAmount = 0;
//					while (remainTime > 0)
//					{
//						ResourceAccessInfo accessInfo = taskCluster.get(currentTask).getResourceAccessInfo(resourceID);
//						
//						int timeToNextTask = ((accessInfo.n-startAccess)*(accessInfo.w+accessInfo.d))-accessInfo.d;
//						if (currentTask != i)
//							timeToNextTask += Math.max(0,
//									taskCluster.get(currentTask).getComputationLower() -
//									accessInfo.n*(accessInfo.w+accessInfo.d)+accessInfo.d);
//
//						if (nextTask != 0)
//						{
//							timeToNextTask += dist[taskCluster.get(currentTask).getBaseId()][taskCluster.get(nextTask).getBaseId()];
//						}
//						else
//						{
//							Task task0 = taskCluster.get(nextTask);
//							Task taskN = taskCluster.get(currentTask);
//
//							timeToNextTask += Math.max(0, task0.getParentTaskGroup().getPeriod() + task0.getReleaseMin() - taskN.getEndMax());
//						}
//
//						if (remainTime <= timeToNextTask)
//						{
//							int inTCount = Math.min(accessInfo.n-startAccess, remainTime/(accessInfo.w+accessInfo.d));
//							demandAmount += inTCount*accessInfo.w;
//							if (inTCount < accessInfo.n-startAccess)
//								demandAmount += Math.min(accessInfo.w, remainTime- inTCount*(accessInfo.w+accessInfo.d));
//
//							remainTime = 0;
//						}
//						else
//						{
//							demandAmount += (accessInfo.n-startAccess)*accessInfo.w;
//
//							remainTime -= timeToNextTask;
//							startAccess = 0;
//							currentTask = nextTask;
//							nextTask = (currentTask+1)%taskCluster.size();
//						}
//					}
//					
//					if (demandBound < demandAmount)
//						demandBound = demandAmount;
//				}
//			}
//
//			if (isIncrease && (prevBound == demandBound))
//			{
//				curve.Curve.add(new DemandCurve.Position(prevTimePoint, demandBound));	
//				isIncrease = false;
//			}
//			if (!isIncrease && prevBound < demandBound)
//			{
//				prevTimePoint = t - time_unit;
//				isIncrease = true;
//			}
//			
//			if (demandBound == maxDemand)
//			{
//				curve.Curve.add(new DemandCurve.Position(prevTimePoint, demandBound));
//				break;
//			}
//
//			prevBound = demandBound;
//			
//			t += time_unit;
//		}
//
//		int t_end = 0;
//		int t_startDiff = 0;
//		for (int i = 0; i < taskCluster.size(); ++i)
//		{
//			Task task = taskCluster.get(i);
//			ResourceAccessInfo accessInfo = task.getResourceAccessInfo(resourceID);
//
//			int resourceAccessLength = accessInfo.n*(accessInfo.w+accessInfo.d)-accessInfo.d;
//			t_end += Math.max(task.getComputationLower(), resourceAccessLength);
//			if (i != taskCluster.size() - 1)
//				t_end += dist[task.getBaseId()][taskCluster.get(i+1).getBaseId()];
//			else
//			{
//				Task task0 = taskCluster.get(0);
//				t_end += task0.getParentTaskGroup().getPeriod() + task0.getReleaseMin() - task.getEndMax();
//			}
//
//			if (t_startDiff < Math.max(0, task.getComputationLower() - resourceAccessLength))
//				t_startDiff = Math.max(0, task.getComputationLower() - resourceAccessLength);
//		}
//		t_end -= t_startDiff;
//
//		int curvePosSize = curve.Curve.size();
//		int firstDemand = curve.Curve.get(0).y;
//		int lastDemand = curve.Curve.get(curvePosSize-1).y;
//		
//		for (int i = 0; i < curvePosSize; ++i)
//		{
//			DemandCurve.Position pos = curve.Curve.get(i);
//			curve.Curve.add(new DemandCurve.Position(pos.x + t_end, pos.y + lastDemand));
//		}
//		curve.Curve.add(new DemandCurve.Position(t_end + taskCluster.get(0).getParentTaskGroup().getPeriod(),
//												firstDemand + lastDemand*2));
//		curve.repeat_st = curvePosSize;
//		curve.repeat_end = curvePosSize*2;
//		curve.repeat_flag = true;
//		curve.curv_end = curve.Curve.get(curve.Curve.size()-1).x;
//		
//		return curve;
//	}
//	
//	protected boolean SuccGroupContains(Task lhs, Task rhs)
//	{
//		return SuccessorGroups[lhs.getBaseId()].get(rhs.getBaseId());
//	}
//	
//	protected boolean PredGroupContains(Task lhs, Task rhs)
//	{
//		return SuccessorGroups[rhs.getBaseId()].get(lhs.getBaseId());
//	}
//	
//	public int getValue(int targetID, int resourceID, int time)
//	{
//		int ret = 0;
//		for (int i = 0; i < desc.totalProcessorList.size(); ++i)
//			ret += curves[targetID][resourceID][i].get(time);
//		
//		return ret;
//	}
//}