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
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package HPA;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.Queue;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import HPA.Debug.Category;

@XmlRootElement(name = "description")
class xml_data
{
    @XmlElement(name = "targetTask")
    public int targetTask;

    @XmlElement(name = "task_list")
    public xml_task_list taskList;
    @XmlElement(name = "PE_list")
    public xml_PE_list PEList;
    @XmlAttribute(name = "SRcount")
    public int SRcount;
    @XmlElement(name = "dependency")
    public xml_dependency dependency;
}

class xml_task_list
{
    @XmlElement(name = "task")
    public ArrayList<xml_task> taskList;
}

class xml_task
{
    @XmlAttribute(name = "id")
    public int id;
    @XmlAttribute(name = "period")
    public long period;
    @XmlAttribute(name = "deadline")
    public long deadline;
    @XmlAttribute(name = "d")
    public int distance;
    @XmlAttribute(name = "jitter")
    public int jitter;
    @XmlAttribute(name = "offset")
    public int offset;

    @XmlElement(name = "subtask")
    public ArrayList<xml_subtask> SubtaskList;
}

class xml_subtask
{
    @XmlAttribute(name = "id")
    public int id;
    @XmlAttribute(name = "priority")
    public int priority;
    @XmlElement(name = "computation")
    public xml_computation computation;
}

class xml_computation
{
    @XmlElement(name = "upper")
    public int upper;
    @XmlElement(name = "lower")
    public int lower;
}

class xml_PE_list
{        
    @XmlElement(name = "PE")
    public ArrayList<xml_PE> PEList;
}

class xml_PE
{
    @XmlAttribute(name = "id")
    public int id;
    @XmlAttribute(name = "preemptable")
    public boolean preemptable;
    @XmlAttribute(name = "core_count")
    public int core_count;
    
    @XmlElement(name = "subtask")
    public ArrayList<xml_mapping> SubtaskList;
}

class xml_mapping
{
    @XmlAttribute(name = "id")
    public int id;
}

class xml_dependency
{
    @XmlElement(name = "edge")
    public ArrayList<xml_edge> EdgeList;
}

class xml_edge
{
    @XmlElement(name = "from")
    public int from;
    @XmlElement(name = "to")
    public int to;
}

public class Description
{
	// constant values which will be used to represent parsing result
	static final int NONE = 0;
	static final int SUCCESS = 1;
	static final int INVALID_VALUE = 2;
	static final int INVALID_ATTRIBUTE = 3;
	static final int INVALID_XML_ARCHI = 4;
	static final int INVALID_FILE = 5;
	static final int DUPLICATED = 6;
	static final int INSUFFICIENT = 7;

	// parsing result
	int parseStatus;
	String error;

	int numberOfProcessors;
	int numberOfTaskGroups;
	int numberOfTasks;

	//KJW
	public TaskGroupList totalTaskGroupList = new TaskGroupList(); //KJW
	public TaskList totalTaskList = new TaskList(); //KJW
	public ProcessorList totalProcessorList = new ProcessorList(); // KJW
	public TaskGroupSetList totalTaskGroupSetList = new TaskGroupSetList(); // kjw
	public int totalTaskGroupNumOrignal = 0;
	public int totalTaskNumOrignal = 0;
	
	// Id of the task we want to analyse WCET
	public int targetTaskGroupId;
	public int targetTaskIdGroupOriginal;

	public String dPath;
	
	Debug.Stream DEBUG_ANAL;
	Debug.Stream DEBUG_ESTI;
	Debug.Stream DEBUG_DESC;
	
	// constructor
	public Description(String inputString)
	{
		parseStatus = NONE;
		error = "";
		DEBUG_DESC = Debug.Create(Category.DESCRIPTION, "desc.log");
		DEBUG_ESTI = Debug.Create(Category.ESTIMATION, "esti.log");
		DEBUG_ANAL = Debug.Create(Category.ANALYSIS,"anal.log");
		
		if (!parseDescriptionXmlFromInputString(inputString))
			printErrorDetail();			
		postProcessing();
	}
	
	//KJW
	public void reset()
	{	
		totalTaskGroupList.clear();
		totalTaskList.clear();
		totalProcessorList.clear();
		totalTaskGroupSetList.clear();
		//parseDescriptionXml(this.dPath);
	}

	public void CreateDummyNodes()
	{
		Processor proc = new Processor(this.totalProcessorList.size());
		numberOfProcessors++;
		proc.setPreemtable(true);
		this.totalProcessorList.add(proc);
		
		int beforeTaskCount = totalTaskList.size();
		for (int i = 0; i < this.totalTaskGroupList.size(); ++i)
		{
			TaskGroup taskGroup = totalTaskGroupList.get(i);
			
			Task startNode = new Task(taskGroup, 0, 0, beforeTaskCount + i*2, taskGroup.period);
			Task endNode = new Task(taskGroup, 0, 0, beforeTaskCount + i*2 + 1, taskGroup.period);
			startNode.setType(Task.SUBTASK_TYPE);
			endNode.setType(Task.SUBTASK_TYPE);
			
			startNode.setBaseId(beforeTaskCount + i*2);
			endNode.setBaseId(beforeTaskCount + i*2 + 1);
			
			startNode.setId(beforeTaskCount + i*2);
			endNode.setId(beforeTaskCount + i*2 + 1);
			
			startNode.setPreemptable(true);
			endNode.setPreemptable(true);
			
			startNode.setPriority(0);
			endNode.setPriority(0);
			
			startNode.setMappedProcID(proc.id);
			endNode.setMappedProcID(proc.id);
			
			startNode.setSource(true);
			endNode.setSink(true);
			
			for (Task task : taskGroup.taskList)
			{
				if (task.isItSource())
				{
					startNode.getOutgoingEdges().add(task);
					task.getIncommingEdges().add(startNode);
					task.setSource(false);
				}
				if (task.isItSink())
				{
					task.getOutgoingEdges().add(endNode);
					endNode.getIncommingEdges().add(task);
					task.setSink(false);
				}
			}
			taskGroup.taskList.add(startNode);
			taskGroup.taskList.add(endNode);
			totalTaskList.add(startNode);
			totalTaskList.add(endNode);
		}
		numberOfTasks += this.totalTaskGroupList.size() * 2;
	}
	
	public void reconstructGraph() 
	{
		Queue<Task> visitQueue = new LinkedList<Task>();
		
		int[] groupIDs = new int[totalTaskList.size()];
		for (int i = 0; i < groupIDs.length; ++i)
			groupIDs[i] = -1;
		int currentGroupID = 0;
		int nonGroupTaskCount = totalTaskList.size();
		
		while (nonGroupTaskCount > 0)
		{
			Task target = null;
			for (int i = 0; i < totalTaskList.size(); ++i)
			{
				if (groupIDs[i] >= 0)
					continue;
				Task task = totalTaskList.getFromBaseId(i);
				
				boolean targetable = true;
				for (Task predecessor : task.getIncommingEdges())
					if (groupIDs[predecessor.getBaseId()] < 0)
					{
						targetable = false;
						break;
					}
				if (targetable)
				{
					target = task;
					break;
				}
			}
			
			for (int i = 0; i < totalTaskList.size(); ++i)
			{
				if (groupIDs[i] >= 0)
					continue;
				Task task = totalTaskList.getFromBaseId(i);
				
				if (task.getParentTaskId() != target.getParentTaskId())
					continue;
				if (task.getMappedProcID() != target.getMappedProcID())
					continue;
				if (task.getIncommingEdges().size() != target.getIncommingEdges().size())
					continue;
				
				boolean targetable = true;
				for (Task predecessor : task.getIncommingEdges())
					if (!target.getIncommingEdges().contains(predecessor))
					{
						targetable = false;
						break;
					}
				if (!targetable)
					continue;
				
				visitQueue.offer(task);
			}
			
			while (!visitQueue.isEmpty())
			{
				Task task = visitQueue.poll();
				groupIDs[task.getBaseId()] = currentGroupID;
				--nonGroupTaskCount;
				
				for (Task successor : task.getOutgoingEdges())
				{
					if (successor.getMappedProcID() != task.getMappedProcID())
						continue;
					boolean targetable = true;
					for (Task predecessor : successor.getIncommingEdges())
						if (groupIDs[predecessor.getBaseId()] != groupIDs[task.getBaseId()] &&
							!target.getIncommingEdges().contains(predecessor))
						{
							targetable = false;
							break;
						}
					if (targetable)
						visitQueue.offer(successor);
				}
			}
			++currentGroupID;
		}
		
		TaskList groupTaskList = new TaskList();
		TaskList scheduleOrder = new TaskList();
		for (int i = 0; i < totalTaskList.size(); ++i)
		{
			Task task = totalTaskList.getFromBaseId(i);
			task.setEndPointDirty(false);
		}	
		for (int i = 0; i < currentGroupID; ++i)
		{
			groupTaskList.clear();
			scheduleOrder.clear();
			for (int j = 0; j < totalTaskList.size(); ++j)
			{
				if (groupIDs[j] != i)
					continue;
				Task task = totalTaskList.getFromBaseId(j);
				groupTaskList.add(task);
			}
			while (scheduleOrder.size() < groupTaskList.size())
			{
				Task target = null;
				for (Task task : groupTaskList)
				{
					if (task.isEndPointDirty())
						continue;
					boolean targetable = true;
					for (Task predecessor : task.getIncommingEdges())
						if (!predecessor.isEndPointDirty())
						{
							targetable = false;
							break;
						}
					if (!targetable)
						continue;
					if (target == null || task.higherPriorThan(target))
						target = task;
				}
				target.setEndPointDirty(true);
				scheduleOrder.add(target);
			}
			for (int j = 1; j < scheduleOrder.size(); ++j)
			{
				Task parent = scheduleOrder.get(j-1);
				Task child = scheduleOrder.get(j);
				if (!parent.getOutgoingEdges().contains(child))
					parent.getOutgoingEdges().add(child);
				if (!child.getIncommingEdges().contains(parent))
					child.getIncommingEdges().add(parent);
			}
		}
		
		BitSet[] SuccessorSet = CreateSuccessorSet();
		Queue<Task> removeList = new LinkedList<Task>();
		for (int i = 0; i < totalTaskList.size(); ++i)
		{
			Task s = totalTaskList.get(i);

			for (Task successor : s.getOutgoingEdges())
				removeList.offer(successor);
			while (!removeList.isEmpty())
			{
				Task successor = removeList.poll();
				
				boolean anotherPath = false;
				for (Task otherSuccessor : s.getOutgoingEdges())
				{
					if (successor != otherSuccessor && s.getBaseId() != otherSuccessor.getBaseId() &&
						SuccessorSet[otherSuccessor.getBaseId()].get(successor.getBaseId()))
					{
						anotherPath = true;
						break;
					}
				}
				
				if (anotherPath)
				{
					s.getOutgoingEdges().remove(successor);
					successor.getIncommingEdges().remove(s);
				}
			}
		}
		
		for (Task s : this.totalTaskList)
		{
			if (s.getIncommingEdges().size() == 0 && s.getPeriod() == -1) 
				s.setPeriod(s.getParentTaskGroup().getPeriod());
			
			s.setSource(s.getIncommingEdges().size() == 0);
			s.setSink(s.getOutgoingEdges().size() == 0);
		}
	}
	
	BitSet[] CreateSuccessorSet()
	{
		BitSet[] SuccessorSet = new BitSet[totalTaskList.size()];
		for (int i = 0; i < totalTaskList.size(); ++i)
			SuccessorSet[i] = new BitSet(totalTaskList.size());
		
		Queue<Task> visitQueue = new LinkedList<Task>();
		for (Task task : totalTaskList)
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
		return SuccessorSet;
	}
	private void postProcessing()
	{
		for(int i = 0 ;i < totalTaskList.size();i++)
		{
			Task s = totalTaskList.get(i);
			TaskGroup t = s.getParentTaskGroup();
			if(s.isItSource())
			{
				s.setJitter(t.getJitter());
				s.setDistance(t.getInterDistance());
			}
		}
	}

	// parse input from inputString and save it
	public boolean parseDescriptionXmlFromInputString(String inputString)
	{
    	try {
    		JAXBContext jaxbContext = JAXBContext.newInstance(xml_data.class);
    		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
    		xml_data input = (xml_data) unmarshaller.unmarshal(new StringReader(inputString));

    		init(input);
    	
    		if (!getTargetTask(input)
    			|| !getTaskInfo(input)
    			|| !getProcessorInfo(input)
    			|| !getDependencyInfo(input))
    		{
    			return false;	// when parsing fails
    		}

    		sortSubtaskList();
    		printSubtaskModel();

    		parseStatus = SUCCESS;
    		return true;
    	} catch (Exception e) {
    		e.printStackTrace();
			parseStatus = INVALID_FILE;
			error = "Description:parseDescriptionXml";
			return false;
    	}
	}

	private void init(xml_data input)
	{
		this.numberOfProcessors = input.PEList.PEList.size();
		this.numberOfTaskGroups = input.taskList.taskList.size();
		this.totalTaskGroupNumOrignal = this.numberOfTaskGroups;
		
		for (int i = 0; i < this.numberOfTaskGroups; ++i)
			this.totalTaskNumOrignal += input.taskList.taskList.get(i).SubtaskList.size();
		
		this.numberOfTasks = this.totalTaskNumOrignal;
	}
	
	private boolean getTargetTask(xml_data input)
	{
		if (isValidId(input.targetTask, this.numberOfTaskGroups))
		{
			this.targetTaskGroupId = input.targetTask;
			this.targetTaskIdGroupOriginal = input.targetTask;
			
			return true;
		}
		parseStatus = INVALID_VALUE;
		error = "Description:getTargetTask:1";
		return false;
	}

	public void addTask(TaskGroup t)
	{
		this.totalTaskGroupList.add(t);
		this.numberOfTaskGroups++;
		for(int i = 0; i < t.getTaskList().size(); i++)
		{
			Task st = t.getTaskList().get(i);
			this.totalTaskList.add(st);
			this.numberOfTasks++;
		}
	}
	
	private boolean getTaskInfo(xml_data input)
	{
		ArrayList<xml_task> xmlTasks = input.taskList.taskList;
			
		// get tasks and subtasks information
		boolean[] taskIds = new boolean[this.numberOfTaskGroups];
		boolean[] subtaskIds = new boolean[this.numberOfTasks];

		for (xml_task task : xmlTasks)
		{
			if (!isValidId(task.id, this.numberOfTaskGroups))
			{
				parseStatus = INVALID_ATTRIBUTE;
				error = "Description:getTaskInfo:0";
				return false;
			}
			
			if (taskIds[task.id])
			{
				parseStatus = DUPLICATED;
				error = "Description:getTaskInfo:1";
				return false;
			}
			
			taskIds[task.id] = true;

			long period = task.period;
			long deadline = task.deadline == 0 ? task.period : task.deadline;
			int jitter = task.jitter;
			int distance = task.distance;
			String name = "TaskGroup" + task.id;
			
			TaskGroup ttask = new TaskGroup(0 , period, deadline, task.id, this); //KJW
			ttask.setName(name);
			ttask.setJitter(jitter);
			ttask.setInterDistance(distance);
			
			for (xml_subtask subtask : task.SubtaskList)
			{
				if (!isValidId(subtask.id, this.numberOfTasks))
				{
					parseStatus = INVALID_ATTRIBUTE;
					error = "Description:getTaskInfo:6";
					return false;
				}
				
				if (subtaskIds[subtask.id])
				{
					parseStatus = DUPLICATED;
					error = "Description:getTaskInfo:7";
					return false;
				}
				subtaskIds[subtask.id] = true;
				
				if (subtask.computation.lower < 0 || subtask.computation.lower > subtask.computation.upper) 
					return false;
				
				Task ssubtask = new Task(ttask, subtask.computation.upper, subtask.computation.lower,
						subtask.id, -1);
				ssubtask.setType(Task.SUBTASK_TYPE);
				
				ssubtask.setBaseId(subtask.id);
				ssubtask.setPreemptable(true);
				ssubtask.setPriority(subtask.priority);
				ssubtask.setName("Task" + subtask.id);
				ssubtask.setJitter(-1);
				
				ttask.getTaskList().add(ssubtask);
				this.totalTaskList.add(ssubtask);
			}
			this.totalTaskGroupList.add(ttask);
			
			TaskGroupSet tg = new TaskGroupSet(this, ttask);
			ttask.setTaskGroupSet(tg);
			this.totalTaskGroupSetList.add(tg);
		}
		
		for (int i = 0; i < this.numberOfTaskGroups; i++)
		{
			if (!taskIds[i])
			{
				parseStatus = INSUFFICIENT;
				error = "Description:getTaskInfo:12";
				return false;
			}
		}
		for (int i = 0; i < this.numberOfTasks; i++)
		{
			if (!subtaskIds[i])
			{
				parseStatus = INSUFFICIENT;
				error = "Description:getTaskInfo:13";
				return false;
			}
		}
		return true;
	}

	private boolean getProcessorInfo(xml_data input)
	{
		ArrayList<xml_PE> xmlProcessor = input.PEList.PEList;
		
		boolean[] processorIds = new boolean[this.numberOfProcessors];
		boolean[] subtaskMapped = new boolean[this.totalTaskList.size()];

		for (xml_PE processor : xmlProcessor)
		{
			if (!isValidId(processor.id, this.numberOfProcessors))
			{
				parseStatus = INVALID_ATTRIBUTE;
				error = "Description:getProcessorInfo:0";
				return false;
			}

			if (processorIds[processor.id])
			{
				parseStatus = DUPLICATED;
				error = "Description:getProcessorInfo:1";
				return false;
			}
			
			processorIds[processor.id] = true;

			Processor proc = new Processor(processor.id); //KJW			
			proc.setPreemtable(processor.preemptable);
			proc.setName("PE" + processor.id);
			
			//check null processor
			if(processor.SubtaskList != null)
			{
				for (xml_mapping mapping : processor.SubtaskList)
				{
					if (!isValidId(mapping.id, this.numberOfTasks))
					{
						parseStatus = INVALID_VALUE;
						error = "Description:getProcessorInfo:6";
						return false;	
					}
					if (subtaskMapped[mapping.id])
					{
						parseStatus = DUPLICATED;
						error = "Description:getProcessorInfo:5";
						return false;
					}
					subtaskMapped[mapping.id] = true;

					Task s = this.totalTaskList.getFromId(mapping.id); //KJW
					proc.getMappedSubtasks().add(s); //KJW
					s.setMappedProcID(processor.id);//KJW
					s.setPreemptable(proc.isPreemtable());
				}
			}			
			
			this.totalProcessorList.add(proc); //KJW
		}

		for (int i = 0; i < subtaskMapped.length; i++)
		{
			if (!subtaskMapped[i])
			{
				parseStatus = INSUFFICIENT;
				error = "Description:getProcessorInfo:7 :subtask " + i;
				return false;
			}
		}

		for (int i = 0; i < this.numberOfProcessors; i++)
		{
			if (!processorIds[i])
			{
				parseStatus = INSUFFICIENT;
				error = "Description:getProcessorInfo:8 " + this.numberOfProcessors;
				return false;
			}
		}

		return true;
	}

	private boolean getDependencyInfo(xml_data input)
	{
		ArrayList<xml_edge> xmlDependency = input.dependency.EdgeList;
		
		// get information of subtask graph's edge
		for (xml_edge dependency : xmlDependency)
		{
			if (!isValidId(dependency.from, this.numberOfTasks))
			{
				parseStatus = INVALID_VALUE;
				error = "Description:getDependencyInfo:0";
				return false;
			}
			if (!isValidId(dependency.to, this.numberOfTasks))
			{
				parseStatus = INVALID_VALUE;
				error = "Description:getDependencyInfo:1";
				return false;
			}
			
			Task ts = this.totalTaskList.getFromId(dependency.to);
			Task fs = this.totalTaskList.getFromId(dependency.from);
			ts.getIncommingEdges().add(fs);
			fs.getOutgoingEdges().add(ts); 
		}
		
		// subtasks without incoming edge are executed periodically
		for (Task s : this.totalTaskList)
		{
			if (s.getIncommingEdges().size() == 0 && s.getPeriod() == -1) 
				s.setPeriod(s.getParentTaskGroup().getPeriod());
			if (s.getIncommingEdges().size() == 0)
				s.setSource(true);
			if (s.getOutgoingEdges().size() == 0)
				s.setSink(true);
		}

		return true;
	}

	private boolean isValidId(long id, int numberOfId)
	{
		return id >= 0 && id < numberOfId;
	}
	
	private void sortSubtaskList()
	{
		for (int i = 0; i < totalTaskList.size(); ++i)
		{
			for (int j = 0; j < totalTaskList.size() - i - 1; ++j)
			{
				Task left = totalTaskList.get(j);
				Task right = totalTaskList.get(j+1);
				
				if (left.id > right.id)
				{
					totalTaskList.set(j, right);
					totalTaskList.set(j+1, left);
				}
			}
		}
	}

	public void printSubtaskModel()
	{
		for (int i = 0; i < this.totalTaskGroupList.size(); i++)
		{
			TaskGroup t = this.totalTaskGroupList.get(i);
			DEBUG_DESC.println(t);
			for (int j = 0 ; j < t.getTaskList().size(); j++)
			{
				Task ss = t.getTaskList().get(j);
				DEBUG_DESC.print("  ");
				DEBUG_DESC.println(ss);
			}
		}

		DEBUG_DESC.println("edges");
		for(int i = 0 ; i < this.totalTaskList.size() ; i ++)
		{
			Task s = this.totalTaskList.get(i);
			for(int j = 0 ; j < s.getOutgoingEdges().size () ; j++)
			{
				Task dst_s = s.getOutgoingEdges().get(j);
				DEBUG_DESC.println(s.getId() + " -> " + dst_s.getId());
			}
		}
		DEBUG_DESC.println("mapping");
		for(int i = 0 ; i < this.totalProcessorList.size() ; i ++)
		{
			Processor p = this.totalProcessorList.get(i);
			DEBUG_DESC.print("Proc["+p.getId()+"] : ");
			for(int j = 0 ; j < p.getMappedSubtasks().size(); j++)
			{
				Task s = p.getMappedSubtasks().get(j);
				DEBUG_DESC.print(s.getId() + " ");
			}
			DEBUG_DESC.println();
		}
	}

	public void printErrorDetail()
	{
		switch (this.parseStatus)
		{
			case INVALID_VALUE :
				DEBUG_DESC.println("Parse Error : Invalid Value @ " + this.error);
				DEBUG_DESC.println("The value enclosed by tags is improper type or unacceptable value.");
				break;
			case INVALID_ATTRIBUTE :
				DEBUG_DESC.println("Parse Error : Invalid Attribute @ " + this.error);
				DEBUG_DESC.println("The value in the attribute is improper type or unacceptable value.");
				break;
			case INVALID_XML_ARCHI :
				DEBUG_DESC.println("Parse Error : Invalid XML Architecture @ " + this.error);
				DEBUG_DESC.println("XML file has an architecture we do not support.");
				break;
			case INVALID_FILE :
				DEBUG_DESC.println("Parse Error : Invalid File @ " + this.error);
				DEBUG_DESC.println("Can not open the file in the give path.");
				break;
			case DUPLICATED :
				DEBUG_DESC.println("Parse Error : Duplicated Id @ " + this.error);
				DEBUG_DESC.println("Id of processor, task, subtask should be unique value.");
				break;
			case INSUFFICIENT:
				DEBUG_DESC.println("ParseError : Information is insufficient @ " + this.error);
				DEBUG_DESC.println("All tasks, subtasks's information should be specified. And all subtasks should be mapped to a processor.");
				break;
			default :
				DEBUG_DESC.println("Parse Error @ " + this.error);
				break;
		}
	}
}
