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


import java.util.ArrayList;
import java.util.HashMap;

public class Task
{	
	// subtask's type
	// sensor, CAN Bus message and actuator are regarded as subtask
	static final int SENSOR_TYPE = 10;
	static final int MESSAGE_TYPE = 11;
	static final int SUBTASK_TYPE = 12;
	static final int ACTUATOR_TYPE = 13;
	
	
	
	Integer parentTaskGroupId;
	TaskGroup parentTaskGroup;
	
	Integer computationUpper;
	Integer computationLower;
	Integer computationLowerCached;
/*	Integer memAccessUpper;
	Integer memAccessLower;
	Integer busUsageUpper;
	Integer busUsageLower;
*/	
	public Integer id;
	Integer baseId;
	
	long period;
	Integer type = SUBTASK_TYPE;

	boolean preemptable=true;
	
	TaskList incommingEdges = new TaskList();
	TaskList outgoingEdges = new TaskList();
	
	Integer mappedProcID;
	Integer priority; // lower number has higher priority
	
	Task nextInstance = null;
	Task prevInstance = null;

	Integer conditionId = null;
	Integer conditionCaseId = null;
	
	Integer jitter = null;
	Integer distance = null;

	enum ReleaseType {DataDriven, TimeDriven};
	ReleaseType releaseType;
	
	double resultRelease = -1;
	double resultEnd = -1;
	double resultPO = -1;
	double resultJitter= -1;
	double resultStart = - 1;
	String name;
	double resultTemp = - 1;

	boolean source = false;
	boolean sink = false;
	
	public void setSource(boolean source) {
		this.source = source;
	}
	public void setSink(boolean sink) {
		this.sink = sink;
	}

	HashMap<Integer, Double> preemptionDetail = new HashMap<Integer, Double>();
	
	/* For scheduling - Min-Max*/
	long initialReleaseMin=-1, initialReleaseMax=-1; 
	long releaseMin=-1, releaseMax=-1;
	long startMin=-1, startMax=-1;
	long endMin=-1, endMax=-1;
	long finalFin=-1, finalStart=-1;
	
	boolean scheduled;
	boolean reschduled = false;
	boolean endCheckDirty = false;
	
	int maxDiff=-1;
	int minDiff=-1;
	
	int priorityCached = -1;
	int mm_offset = 0;
	
	ArrayList<Task> excludeList = new ArrayList<Task>(); // tasks that can't preempt me
	ArrayList<Task> preemptorList = new ArrayList<Task>(); // Task which preempts me and has higher priority than me. temporary list. 
	
	ArrayList<Task> lowerPreemptorList = new ArrayList<Task>();// Task which preempts me and has lower priority than me. temporary list. 
	
	ArrayList<Integer> preemptorIDs = new ArrayList<Integer>(); // Task which preempts me in any situation. Permenent list
	ArrayList<Integer> preemptorIDs4FinStar = new ArrayList<Integer>(); // Task which preempts me in any situation. Permenent list
	
	ArrayList<Integer> removerIDs = new ArrayList<Integer>();
	
//	ArrayList<Integer> maxStartPreemptorIDs = new ArrayList<Integer>();
//	ArrayList<Integer> minStartPreemptorIDs = new ArrayList<Integer>();
//	ArrayList<Integer> maxEndPreemptorIDs = new ArrayList<Integer>();
//	ArrayList<Integer> minEndPreemptorIDs = new ArrayList<Integer>();
	/* For Max Finish Star */
	int startMinCached = -1;
	int releaseMaxCached = -1;
	int startMaxCached = -1;
	int finishStarMin = -1, finishStarMax  = -1;
	/* */
//	int candidate_s = -1;
//	int candidate_S = -1;
//	int candidate_f = -1;
//	int candidate_F = -1;
	/*For scheduling end*/

	public boolean isItSource()
	{
		return source;
//		if(this.getIncommingEdges().size() == 0 ) return true;
//				return false;
	}
	public boolean isItSink()
	{
		return sink;
	}
	public Integer getDistance() {
		return distance;
	}



	public void setDistance(Integer distance) {
		this.distance = distance;
	}
	
	public boolean canBeScheduledLater(Task culprit, int t) {
		boolean flag = false;
		for (Task n: incommingEdges) {
			if (n == culprit) continue;
			if (n.getEndMax() <= t) continue;
			flag = true;
		}
		return true;
	}



	int visitableCount;
	int criticalPathLength;
	public void resetVisitableCount(int value)
	{
		visitableCount = value;
	}
	public boolean decVisitableCount()
	{
		return --visitableCount == 0;
	}
	public int getCriticalPathLength()
	{
		return criticalPathLength;
	}
	public void setCriticalPathLength(int value)
	{
		criticalPathLength = value;
	}
	
	public int getOffset() {
		return mm_offset;
	}

	public void setOffset(int mm_offset) {
		this.mm_offset = mm_offset;
	}

	public int getMaxDiff() {
		return maxDiff;
	}
	
	public long getFinalFin() {
		return finalFin;
	}

	public void setFinalFin(long finalFin) {
		this.finalFin = finalFin;
	}

	public long getFinalStart() {
		return finalStart;
	}

	public void setFinalStart(long finalStart) {
		this.finalStart = finalStart;
	}

	public ArrayList<Integer> getPreemptorIDs4FinStar() {
		return preemptorIDs4FinStar;
	}

	public void setPreemptorIDs4FinStar(ArrayList<Integer> preemptorIDs4FinStar) {
		this.preemptorIDs4FinStar = preemptorIDs4FinStar;
	}

	public ArrayList<Task> getExcludeList() {
		return excludeList;
	}

	public void setExcludeList(ArrayList<Task> excludeList) {
		this.excludeList = excludeList;
	}

	public int getPriorityCached() {
		return priorityCached;
	}

	public void setPriorityCached(int realPriority) {
		this.priorityCached = realPriority;
	}

	public ArrayList<Task> getPreemptorList() {
		return preemptorList;
	}

	public void setPreemptorList(ArrayList<Task> preemptList) {
		this.preemptorList = preemptList;
	}

	public Integer getComputationLowerCached() {
		return computationLowerCached;
	}

	public void setComputationLowerCached(Integer computationLowerCached) {
		this.computationLowerCached = computationLowerCached;
	}

	public ArrayList<Task> getLowerPreemptorList() {
		return lowerPreemptorList;
	}

	public void setLowerPreemptorList(ArrayList<Task> lowerPreemptorist) {
		this.lowerPreemptorList = lowerPreemptorist;
	}

//	public int getCandidate_s() {
//		return candidate_s;
//	}

//	public void setCandidate_s(int canMs) {
//		this.candidate_s = canMs;
//	}

//	public int getCandidate_S() {
//		return candidate_S;
//	}

//	public void setCandidate_S(int canMS) {
//		this.candidate_S = canMS;
//	}

//	public int getCandidate_f() {
//		return candidate_f;
//	}

//	public void setCandidate_f(int canMf) {
//		this.candidate_f = canMf;
//	}

//	public int getCandidate_F() {
//		return candidate_F;
//	}

//	public void setCandidate_F(int canMF) {
//		this.candidate_F = canMF;
//	}

	public boolean isReschduled() {
		return reschduled;
	}

	public void setReschduled(boolean reschduled) {
		this.reschduled = reschduled;
	}

//	public ArrayList<Integer> getMaxStartPreemptorIDs() {
//		return maxStartPreemptorIDs;
//	}

//	public void addMaxStartPreemptorId(Integer id)
//	{
//		if(!this.maxStartPreemptorIDs.contains(id))
////			maxStartPreemptorIDs.add(id);
////	}
////	public ArrayList<Integer> getMinStartPreemptorIDs() {
////		return minStartPreemptorIDs;
////	}
//
//	public void addMinStartPreemptorId(Integer id)
//	{
//		if(!this.minStartPreemptorIDs.contains(id))
//			minStartPreemptorIDs.add(id);
//	}
//	public ArrayList<Integer> getMaxEndPreemptorIDs() {
//		return maxEndPreemptorIDs;
//	}
//
//	public void addMaxEndPreemptorId(Integer id)
//	{
//		if(!this.maxEndPreemptorIDs.contains(id))
//			maxEndPreemptorIDs.add(id);
//	}
//	public ArrayList<Integer> getMinEndPreemptorIDs() {
//		return minEndPreemptorIDs;
//	}
//
//	public void addMinEndPreemptorId(Integer id)
//	{
//		if(!this.minEndPreemptorIDs.contains(id))
//			minEndPreemptorIDs.add(id);
//	}

	public int getStartMinCached() {
		return startMinCached;
	}

	public void setStartMinCached(int startMinCached) {
		this.startMinCached = startMinCached;
	}
	
	
	
	public int getStartMaxCached() {
		return startMaxCached;
	}

	public void setStartMaxCached(int startMaxCached) {
		this.startMaxCached = startMaxCached;
	}

	public int getReleaseMaxCached() {
		return releaseMaxCached;
	}

	public void setReleaseMaxCached(int releaseMaxCached) {
		this.releaseMaxCached = releaseMaxCached;
	}

	public int getFinishStarMin() {
		return finishStarMin;
	}

	public void setFinishStarMin(int finishStarMin) {
		this.finishStarMin = finishStarMin;
	}

	public int getFinishStarMax() {
		return finishStarMax;
	}

	public void setFinishStarMax(int finishStarMax) {
		this.finishStarMax = finishStarMax;
	}

	public void setMaxDiff(int maxDiff) {
		this.maxDiff = maxDiff;
	}

	public int getMinDiff() {
		return minDiff;
	}

	public void setMinDiff(int minDiff) {
		this.minDiff = minDiff;
	}
	
	

	public ArrayList<Integer> getPreemptorIDs() {
		return preemptorIDs;
	}


	@Override
	public String toString() {	
		if(this.getParentTaskGroup().getTaskGroupSet() == null)		
			return "Task id["+id+"] pId["+parentTaskGroupId+"] baseId ["+baseId+"] cu["+computationUpper+"] cl["+computationLower+"] pjd["+period+","+jitter+","+distance+"] offs["+mm_offset
					//	+"] soff["+this.getParentTaskGroup().getTaskGroupSet().getStartOffsetMin()
					//	+", "+this.getParentTaskGroup().getTaskGroupSet().getStartOffsetMax()
					+"] pt["+preemptable+"] ieSize["+incommingEdges.size()
					+"] oeSize["+outgoingEdges.size()+"] map["+mappedProcID+"] prio["+priority
					+"]"+ " R["+String.format("%.2f", resultRelease)+"] END["+
					String.format("%.2f", resultEnd)+"] PO["+String.format("%.2f", resultPO)+"] J["+resultJitter+"] ST["+resultStart+"] TEMP["+resultTemp+"]";		
		else
			return "Task id["+id+"] pId["+parentTaskGroupId+"] baseId ["+baseId+"] cu["+computationUpper+"] cl["+computationLower+"] pjd["+period+","+jitter+","+distance+"] offs["+mm_offset
					+"] soff["+this.getParentTaskGroup().getTaskGroupSet().getStartOffsetMin()
					+", "+this.getParentTaskGroup().getTaskGroupSet().getStartOffsetMax()
					+"] pt["+preemptable+"] ieSize["+incommingEdges.size()
					+"] oeSize["+outgoingEdges.size()+"] map["+mappedProcID+"] prio["+priority
					+"]"+ " R["+String.format("%.2f", resultRelease)+"] END["+
					String.format("%.2f", resultEnd)+"] PO["+String.format("%.2f", resultPO)+"] J["+resultJitter+"] ST["+resultStart+"] TEMP["+resultTemp+"]";
	}
	
	public String getScheduleInfoString()
	{
		int procId = this.getMappedProcID();
		return ("s[\t"+this.getParentTaskGroup().getInstanceID()+", \t"+this.getBaseId()+"\t"+this.getId()+"]\tPE["+procId
				+"]\tPR["+this.getPriority()
				+"]\tR["+this.getReleaseMin()+",\t"+this.getReleaseMax()
				+"]\tS["+this.getStartMin()+",\t"+this.getStartMax()+",\t"+this.getFinalStart()				
				+"]\tE["+this.getEndMin()+",\t"+this.getEndMax()+",\t"+this.getFinalFin()
//				+"]\tE*["+this.getFinishStarMin()+",\t"+this.getFinishStarMax()
				+"]\tC["+this.getComputationLower()+",\t"+this.getComputationUpper()+"]"
//				+"]\tD["+(this.getEndMax()-this.getReleaseMin())+", \t"+(this.getEndMin()-this.getReleaseMax())+"]"
				+" Tid,Sid["+this.getParentTaskGroup().getId()+", "+this.getId()+"]"
				+" PL["+getPreemptorListString()
				+" EL["+getExcludeListString()
				+"]"
				);
	}
	
	public String getPreemptorListString()
	{
		String ret="h-";
		for(int i = 0 ; i< this.preemptorList.size(); i++)
		{
//			ret += this.higherPreemptorList.get(i).getId() + " ";
			ret += "("+this.preemptorList.get(i).getParentTaskGroup().getInstanceID()+", "+this.preemptorList.get(i).getBaseId()+") ";
		}
		ret += "l-";
		for(int i = 0 ; i< this.lowerPreemptorList.size(); i++)
		{
			ret += this.lowerPreemptorList.get(i).getId() + " ";
		}
		ret += "* ";
		for(int i = 0 ; i< this.preemptorIDs.size(); i++)
		{
			ret += this.preemptorIDs.get(i) + " ";
		}
		return ret;
	}
	public String getExcludeListString()
	{
		String ret="e-";
		for(int i = 0 ; i< this.excludeList.size(); i++)
		{
			ret += this.excludeList.get(i).getId() + " ";
//			ret += "("+this.preemptorList.get(i).getParentTask().getInstanceID()+", "+this.preemptorList.get(i).getBaseId()+") ";
		}
		return ret;
	}

	//Constructor
	public Task(TaskGroup parentTask,
			Integer computationUpper, Integer computationLower,
//			Integer memAccessUpper, Integer memAccessLower,
//			Integer busUsageUpper, Integer busUsageLower,
			Integer id, long period)
	{
		super();
		this.parentTaskGroupId = parentTask.getId();
		this.parentTaskGroup = parentTask;
		this.computationUpper = computationUpper;
		this.computationLower = computationLower;
/*		this.memAccessUpper = memAccessUpper;
		this.memAccessLower = memAccessLower;
		this.busUsageUpper = busUsageUpper;
		this.busUsageLower = busUsageLower;
*/		this.id = id;
		this.period = period;
		this.priority = id;
//		this.distance = parentTask.getInterDistance();
//		this.jitter = parentTask.getJitter();
	}
	
	public void reset()
	{
		setEndMin(-1);
		setEndMax(-1);
		setReleaseMin(-1);
		setReleaseMax(-1);
		setStartMin(-1);
		setStartMax(-1);
		setScheduled(false);
		getRemoverIDs().clear();
		getPreemptorIDs().clear();
		getPreemptorList().clear();			
		getExcludeList().clear();
	}
	
	// getter and setter	
	public boolean hasPred(Task subt)
	{
		if (this.incommingEdges.size()==0)
			return false;
		
		for (Task s : this.incommingEdges)
		{
			if (s.getId() == subt.getId())
				return true;
		}
		return false;
	}
	
	public boolean hasSucc(Task subt)
	{
		if (this.outgoingEdges.size()==0)
			return false;
		
		for (Task s : this.outgoingEdges)
		{
			if (s.getId() == subt.getId())
				return true;
		}
		return false;
	}
	
	public boolean hasPredIgnoreSrc(Task subt)
	{
		if (this.isItSource()) // if src node, igrore incomming edge 
			return false;
		
		for (Task s : this.incommingEdges)
		{
			if (s.getId() == subt.getId())
				return true;
		}
		return false;
	}
	
	public boolean hasSuccIgnoreSrc(Task subt)
	{
		if (this.isItSource())
			return false;
		
		for (Task s : this.outgoingEdges)
		{
			if (s.getId() == subt.getId())
				return true;
		}
		return false;
	}
	
	public boolean predGroupContains(Task subt)
	{
		if ( this.hasPred(subt) )
			return true;
		else
		{
			for (Task pred: this.incommingEdges)
			{				
				if ( pred.predGroupContains(subt) )
					return true;
			}	
		}
		return false;
	}
	
	public boolean succGroupContains(Task subt)
	{
		if ( this.hasSucc(subt) )
			return true;
		else
		{
			for (Task succ: this.outgoingEdges)
			{				
				if ( succ.succGroupContains(subt) )
					return true;
			}	
		}
		return false;
	}
	
	public boolean predGroupContainsIgnoreSrc(Task subt)
	{
		if ( this.hasPredIgnoreSrc(subt) )
			return true;
		else
		{
			for (Task pred: this.incommingEdges)
			{				
				if ( pred.predGroupContainsIgnoreSrc(subt) )
					return true;
			}	
		}
		return false;
	}
	
	public boolean succGroupContainsIgnoreSrc(Task subt)
	{
		if ( this.hasSuccIgnoreSrc(subt) )
			return true;
		else
		{
			for (Task succ: this.outgoingEdges)
			{				
				if ( succ.succGroupContainsIgnoreSrc(subt) )
					return true;
			}	
		}
		return false;
	}
	
	public boolean higherPriorThan(Task sub)
	{

		if (this.priority < sub.getPriority())
			return true;
		else if (this.priority == sub.getPriority() 
				&& this.parentTaskGroup.getInstanceID() < sub.getParentTaskGroup().getInstanceID())
		{
			return true;
		}
		else
			return false;
	}
	
	public boolean predSameProcessor()
	{
		if (isItSource())
			return true;
		
		boolean anotherProc = false;
		for (Task predecessor : getIncommingEdges())
		{
			if (predecessor.getMappedProcID() != getMappedProcID())
			{
				anotherProc = true;
				break;
			}
		}
		return !anotherProc;
	}
	public ReleaseType getTaskType() {
		return releaseType;
	}

	public void setTaskType(ReleaseType releaseType) {
		this.releaseType = releaseType;
	}
	
	public double getResultRelease()
	{
		return resultRelease;
	}

	public void setResultRelease(double resultRelease)
	{
		this.resultRelease = resultRelease;
	}

	public double getResultEnd()
	{
		return resultEnd;
	}

	public void setResultEnd(double resultEnd)
	{
		this.resultEnd = resultEnd;
	}


	public double getResultJitter()
	{
		return resultJitter;
	}

	public void setResultJitter(double resultJitter)
	{
		this.resultJitter = resultJitter;
	}

	public double getResultPO()
	{
		return resultPO;
	}

	public void setResultPO(double resultPO)
	{
		this.resultPO = resultPO;
	}
	
	public double getResultStart()
	{
		return resultStart;
	}

	public void setResultStart(double resultPO)
	{
		this.resultStart = resultPO;
	}
	
	public double getResultTemp()
	{
		return resultTemp;
	}

	public void setResultTemp(double resultPO)
	{
		this.resultTemp = resultPO;
	}

	public Integer getParentTaskId()
	{
		return parentTaskGroupId;
	}
	public Task getPrevInstance()
	{
		return prevInstance;
	}

	public void setPrevInstance(Task prevInstance)
	{
		this.prevInstance = prevInstance;
	}

	public Task getNextInstance()
	{
		return nextInstance;
	}

	public void setNextInstance(Task nextInstance)
	{
		this.nextInstance = nextInstance;
	}

	public Integer getMappedProcID()
	{
		return mappedProcID;
	}

	public void setMappedProcID(Integer mappedProcID)
	{
		this.mappedProcID = mappedProcID;
	}

	public Integer getPriority()
	{
		return priority;
	}

	public void setPriority(Integer priority)
	{
		this.priority = priority;
	}

/*	public Integer getMemAccessUpper()
	{
		return memAccessUpper;
	}

	public void setMemAccessUpper(Integer memAccessUpper)
	{
		this.memAccessUpper = memAccessUpper;
	}

	public Integer getMemAccessLower()
	{
		return memAccessLower;
	}

	public void setMemAccessLower(Integer memAccessLower)
	{
		this.memAccessLower = memAccessLower;
	}

	public Integer getBusUsageUpper()
	{
		return busUsageUpper;
	}

	public void setBusUsageUpper(Integer busUsageUpper)
	{
		this.busUsageUpper = busUsageUpper;
	}

	public Integer getBusUsageLower()
	{
		return busUsageLower;
	}

	public void setBusUsageLower(Integer busUsageLower)
	{
		this.busUsageLower = busUsageLower;
	}
*/
	public TaskGroup getParentTaskGroup()
	{
		return parentTaskGroup;
	}
	
	public void setParentTaskGroup(TaskGroup parentTask)
	{
		this.parentTaskGroup = parentTask;
	}
	
	public boolean isPreemptable()
	{
		return preemptable;
	}
	
	public void setPreemptable(boolean preemptable)
	{
		this.preemptable = preemptable;
	}
	
	public void setParentTaskGroupId(Integer parentTaskId)
	{
		this.parentTaskGroupId = parentTaskId;
	}
	
	public Integer getComputationUpper()
	{
		return computationUpper;
	}
	
	public void setComputationUpper(Integer computationUpper)
	{
		this.computationUpper = computationUpper;
	}
	
	public Integer getComputationLower()
	{
		return computationLower;
	}
	
	public void setComputationLower(Integer computationLower)
	{
		this.computationLower = computationLower;
	}
	
	public Integer getId()
	{
		return id;
	}
	
	public void setId(Integer id)
	{
		this.id = id;
	}
	
	public long getPeriod()
	{
		return period;
	}
	
	public void setPeriod(long period)
	{
		this.period = period;
	}
	
	public TaskList getIncommingEdges()
	{
		return incommingEdges;
	}
	
	public void setIncommingEdges(TaskList incommingEdges)
	{
		this.incommingEdges = incommingEdges;
	}
	
	public TaskList getOutgoingEdges()
	{
		return outgoingEdges;
	}
	
	public void setOutgoingEdges(TaskList outgoingEdges)
	{
		this.outgoingEdges = outgoingEdges;
	}
	
	public Integer getConditionId()
	{
		return this.conditionId;
	}
	
	public void setConditionId(int id)
	{
		this.conditionId = id;
	}
	
	public Integer getConditionCaseId()
	{
		return this.conditionCaseId;
	}
	
	public void setConditionCaseId(int id)
	{
		this.conditionCaseId = id;
	}
	
	public Integer getType()
	{
		return this.type;
	}
	
	public void setType(int t)
	{
		if (t == SENSOR_TYPE || t == MESSAGE_TYPE || t == SUBTASK_TYPE || t == ACTUATOR_TYPE)
			this.type = t;
		else
			this.type = SUBTASK_TYPE;
	}
	
	public Integer getJitter() 
	{
		return jitter;
	}

	public void setJitter(Integer jitter) 
	{
		this.jitter = jitter;
	}
	

	public HashMap<Integer, Double> getPreemptionDetail() {
		return preemptionDetail;
	}

	public void setPreemptionDetail(HashMap<Integer, Double> preemptionDetail) {
		this.preemptionDetail = preemptionDetail;
	}
	
	public Integer getBaseId() {
		return baseId;
	}

	public void setBaseId(Integer baseId) {
		this.baseId = baseId;
	}

	
	/* From here, for scheduling */
	public long getInitialReleaseMin() {
		return initialReleaseMin;
	}
	
	public void setInitialReleaseMin(long releaseMin) {
		this.initialReleaseMin = releaseMin;
	}
	
	public long getInitialReleaseMax() {
		return initialReleaseMax;
	}
	
	public void setInitialReleaseMax(long releaseMax) {
		this.initialReleaseMax = releaseMax;
	}
	
	public long getReleaseMin() {
		return releaseMin;
	}

	public void setReleaseMin(long releaseMin) {
		this.releaseMin = releaseMin;
	}

	public long getReleaseMax() {
		return releaseMax;
	}

	public void setReleaseMax(long releaseMax) {
		this.releaseMax = releaseMax;
	}

	public long getStartMin() {
		return startMin;
	}

	public void setStartMin(long startMin) {
		this.startMin = startMin;
	}

	public long getStartMax() {
		return startMax;
	}

	public void setStartMax(long startMax) {
		this.startMax = startMax;
	}

	public long getEndMin() {
		return endMin;
	}

	public void setEndMin(long endMin) {
		this.endMin = endMin;
	}

	public long getEndMax() {
		return endMax;
	}

	public void setEndMax(long endMax) {
		this.endMax = endMax;
	}

	public boolean isScheduled() {
		return scheduled;
	}

	public void setScheduled(boolean scheduled) {
		this.scheduled = scheduled;
	}

	public boolean isEndPointDirty() {
		return endCheckDirty;
	}

	public void setEndPointDirty(boolean endPointDirty) {
		this.endCheckDirty = endPointDirty;
	}	
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ArrayList<Integer> getRemoverIDs() {
		return removerIDs;
	}

	public void setRemoverIDs(ArrayList<Integer> removerIDs) {
		this.removerIDs = removerIDs;
	}	

	int migrationCount = 0;
	public void resetMigrationCount() {
		migrationCount = 0;
	}
	public void incMigrationCount() {
		++migrationCount;
	}
	public int getMigrationCount(int coreCount) {
		return Math.min(Math.max(1, migrationCount - coreCount + 1), coreCount);
	}
}