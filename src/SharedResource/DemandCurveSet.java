package SharedResource;

import java.util.ArrayList;

class DemandCurveSet extends DemandCurve
{
	ArrayList<DemandCurve> curves;
	
	public DemandCurveSet()
	{
		super();
		
		curves = new ArrayList<DemandCurve>();
	}
	
	public void add(DemandCurve c)
	{
		curves.add(c);
	}
	
	public int get(int x)
	{
		int res = 0;
		for (DemandCurve c : curves)
		{
			res += c.get(x);
		}
		return res;
	}
}