package Data;

import java.util.ArrayList;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

public class ProcessingElement {
    @XmlAttribute(name="Name")   		public String Name;
    @XmlAttribute(name="Preemptable")   public boolean Preemptable;
    @XmlAttribute(name="CoreCount") 	public int CoreCount;
    
    @XmlElement(name="Mapping")		    public ArrayList<Mapping> MappingList;
    
	@XmlTransient	public int ID;
	
	public ProcessingElement()
	{
		Name = "";
		Preemptable = false;
		CoreCount = 0;
		MappingList = new ArrayList<Mapping>();
		ID = 0;
	}
	
	public ProcessingElement clone()
	{
		ProcessingElement clone = new ProcessingElement();
		clone.Name = this.Name;
		clone.ID = this.ID;
		clone.Preemptable = this.Preemptable;
		clone.CoreCount = this.CoreCount;
		clone.MappingList = new ArrayList<Mapping>();
		for (Mapping mapping : this.MappingList)
			clone.MappingList.add(mapping.clone());
		return clone;
	}
}