package Data;

import javax.xml.bind.annotation.XmlAttribute;

public class ResourceAccessPattern
{
	@XmlAttribute(name="ResourceID")	public int ID;
	@XmlAttribute(name="NumAccesses")	public int n;
	@XmlAttribute(name="Duration")		public int w;
	@XmlAttribute(name="Distance")		public int d;
	
	public ResourceAccessPattern()
	{
		ID = 0;
		n = 0;
		w = 0;
		d = 0;
	}
	
	public ResourceAccessPattern clone()
	{
		ResourceAccessPattern clone = new ResourceAccessPattern();
		clone.ID = this.ID;
		clone.n = this.n;
		clone.w = this.w;
		clone.d = this.d;
		return clone;
	}
}