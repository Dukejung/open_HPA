package Schedule;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;

import Data.Description;

public class ProcessingElement {
	protected final Data.ProcessingElement data;
	protected ArrayList<Task> mappedTaskList;
	protected BitSet mappedTaskSet;

	public int getID()								{ return data.ID; }
	public String getName()							{ return data.Name; }
	public boolean isPreemptable() 					{ return data.Preemptable; }
	public boolean isSingleCore() 					{ return data.CoreCount == 1; }
	public int getCoreCount() 						{ return data.CoreCount; }

	public Iterator<Task> getTaskIterator()			{ return mappedTaskList.iterator(); }
	public void filterUnmappedTasks(BitSet taskset)	{ taskset.and(mappedTaskSet); }
	
	protected ProcessingElement(Data.ProcessingElement data)
	{
		this.data = data;
		mappedTaskList = new ArrayList<Task>();
	}
	
	protected void reset(int totalTaskCount)
	{
		if (mappedTaskSet == null || mappedTaskSet.length() != totalTaskCount)
			mappedTaskSet = new BitSet(totalTaskCount);
		
		for (Task task : mappedTaskList)
			mappedTaskSet.set(task.getInstanceID());
	}
}
