package Data;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

public class Mapping {
    @XmlAttribute(name="Task")		public String Task;
    @XmlAttribute(name="Replica")	public int Replica;
    
    @XmlTransient	public int TaskID;
    
    public Mapping()
    {
    	Task = "";
    	Replica = 0;
    	TaskID = 0;
    }
    
    public Mapping clone()
    {
    	Mapping clone = new Mapping();
    	clone.Task = this.Task;
    	clone.Replica = this.Replica;
    	clone.TaskID = this.TaskID;
    	return clone;
    }
}
