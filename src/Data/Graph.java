package Data;

import java.util.ArrayList;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

public class Graph {
    @XmlAttribute(name="Name")			public String Name;
    @XmlElement(name="Activation")		public Activation Activation;
    
    @XmlElement(name="Task")		    public ArrayList<Task> TaskList;
    @XmlElement(name="Dependency")	    public ArrayList<Dependency> DependencyList;

	@XmlTransient	public int ID;
	
	public Graph()
	{
		Name = "";
		Activation = new Activation();
		TaskList = new ArrayList<Task>();
		DependencyList = new ArrayList<Dependency>();
	}
	
	public Graph clone()
	{
		Graph clone = new Graph();
		clone.Name = this.Name;
		clone.ID = this.ID;
		clone.Activation = this.Activation.clone();
		
		clone.TaskList = new ArrayList<Task>();
		for (Task task : this.TaskList)
			clone.TaskList.add(task.clone());
		clone.DependencyList = new ArrayList<Dependency>();
		for (Dependency dependency : this.DependencyList)
			clone.DependencyList.add(dependency.clone());
		return clone;
	}
}