package Misc;

public class Math {
	public static long GCD(long lhs, long rhs)
    {
        if (rhs == 0) return lhs;
        return GCD(rhs, lhs % rhs);
    }

    public static long LCM(long lhs, long rhs)
    {
        long gcd = GCD(lhs, rhs);

        return (long)lhs / gcd * (long)rhs;
    }

    public static long GCD(long[] values)
    {
        if (values.length == 0)
            return 0;

        long result = values[0];
        for (int i = 1; i < values.length; ++i)
            result = GCD(result, values[i]);

        return result;
    }

    public static long LCM(long[] values)
    {
        if (values.length == 0)
            return 0;

        long result = values[0];
        for (int i = 1; i < values.length; ++i)
            result = LCM(result, values[i]);

        return result;
    }
}
