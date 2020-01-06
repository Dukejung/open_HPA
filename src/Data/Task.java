package Data;

import java.util.ArrayList;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

public class Task {
    @XmlAttribute(name="Name")					public String Name;
    @XmlAttribute(name="Priority")  			public int Priority;
    @XmlAttribute(name="BCET")					public long BCET;
    @XmlAttribute(name="WCET")					public long WCET;
    @XmlElement(name="ResourceAccessPattern")	public ArrayList<ResourceAccessPattern> SRAccessPatternList;

	@XmlTransient	public int ID;
	@XmlTransient	public int MappedPE;
	
	public Task()
	{
		Name = "";
		Priority = 0;
		BCET = 0;
		WCET = 0;
		SRAccessPatternList = new ArrayList<ResourceAccessPattern>();
	}
	
	public Task clone()
	{
		Task clone = new Task();
		clone.Name = this.Name;
		clone.Priority = this.Priority;
		clone.BCET = this.BCET;
		clone.WCET = this.WCET;
		
		clone.SRAccessPatternList = new ArrayList<ResourceAccessPattern>();
		for (ResourceAccessPattern pattern : this.SRAccessPatternList)
			clone.SRAccessPatternList.add(pattern.clone());
		
		clone.ID = this.ID;
		clone.MappedPE = this.MappedPE;
		return clone;
	}
}